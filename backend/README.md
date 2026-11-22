# PolyglotMesh Backend

Spring Boot service for PolyglotMesh, the GraalVM multi-language runtime sandbox.
Week 2 builds this backend incrementally. Day 12 executes JavaScript (GraalJS),
Python (GraalPy) and Java (JDK toolchain) for real; every other language answers
`501` until its adapter exists.

## Stack

| Item | Version |
|---|---|
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Build tool | Maven 3.9.16 |
| Modules | Spring Web, Validation, Actuator |
| GraalVM Polyglot API | 23.1.12 (`org.graalvm.polyglot:polyglot`) |
| Embedded languages | GraalJS 23.1.12 (`js-community`), GraalPy 23.1.12 (`python-community`) |
| Java execution | JDK `javax.tools.JavaCompiler` + the JDK's own `java` launcher (no extra dependency) |
| Tests | JUnit 5, Mockito (via `spring-boot-starter-test`) |

GraalJS, GraalPy and Java are available. TruffleRuby is deliberately **not** a
dependency: its 23.1 runtime refuses to start on Windows (see
[Current limitations](#current-limitations)), so Ruby answers `501` rather than
pretending to run. C, C++, Rust, Go and R need external toolchains and stay
editor-only.

## Local tool paths

Neither Maven nor JDK 21 is first on `PATH` on the development machine, so use
absolute paths (documented in [`../docs/DEVELOPMENT_SETUP.md`](../docs/DEVELOPMENT_SETUP.md)):

```powershell
# Maven (uses JAVA_HOME = E:\Program Files\Java, which is JDK 21)
& "$env:USERPROFILE\.maven\apache-maven-3.9.16\bin\mvn.cmd" -v

# Java 21 explicitly, for running the packaged jar
& "$env:JAVA_HOME\bin\java.exe" -version
```

Do not run the backend with the `java` on `PATH`: that is JDK 17.

## Build and test

Run from this directory:

```powershell
& "$env:USERPROFILE\.maven\apache-maven-3.9.16\bin\mvn.cmd" clean verify
```

`verify` compiles, runs the JUnit 5 suite and packages the executable jar in
`target/`.

## Run

```powershell
& "$env:USERPROFILE\.maven\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run
```

or use the packaged jar:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/polyglotmesh-backend-0.0.1-SNAPSHOT.jar
```

The service listens on **http://localhost:8080**.

## Endpoints

| Method | Path | Purpose | Response |
|---|---|---|---|
| GET | `/api/health` | Application health for the frontend and smoke tests | `{"status":"UP","application":"PolyglotMesh"}` |
| POST | `/api/execute` | Unified execution endpoint | `200` with the execution result; `501` for a known language without an engine; `400` for invalid input |
| GET | `/actuator/health` | Actuator liveness/readiness | `{"status":"UP"}` |
| GET | `/actuator/info` | Application metadata | build/app info |

Only `health` and `info` actuator endpoints are exposed; health details are hidden.

## Execution API

`POST /api/execute` accepts:

```json
{
  "language": "javascript",
  "code": "console.log('Hello PolyglotMesh')",
  "input": ""
}
```

A successful execution returns:

```json
{
  "success": true,
  "language": "javascript",
  "output": "Hello PolyglotMesh\n",
  "error": null,
  "executionTimeMs": 15
}
```

`output` is everything the program wrote to stdout and stderr, including its own
newlines. `error` is `null` on success and carries the compile/runtime error
otherwise; output produced before a failure is still returned.

### JavaScript (executable)

JavaScript runs on GraalJS inside the backend. The program has `console.log` and
`console.error`, plus two helpers for standard input:

| Helper | Behaviour |
|---|---|
| `readLine()` | Returns the next input line, or `null` when input is exhausted |
| `readAll()` | Returns the remaining input lines joined with `\n` |

```javascript
const name = readLine();
console.log(`Hello, ${name}!`);
```

Each request gets a fresh `Context`, so globals never leak between runs. The
sandbox denies host access (`Java.type`, `Polyglot`), file and network IO, process
creation, native access, thread creation and environment variables. A wall-clock
timeout (default 5 s) cancels infinite loops, and output/error text is capped.

`executionTimeMs` covers context creation and evaluation. The first request after
startup also pays the one-off GraalVM engine start (a few seconds here), so the
first measurement is larger than subsequent ones.

### Python (executable)

Python runs on GraalPy. Standard input is a real `sys.stdin`, so the built-in
`input()` behaves as expected:

```python
name = input()
count = int(input())
print(f"Hello, {name}! " * count)
```

The same sandbox rules apply: a fresh context per request, no host access, no
file or network IO, no process or thread creation, the wall-clock timeout and
capped output. GraalPy starts more slowly than GraalJS in interpreter mode, so
the first Python request after startup can take several seconds.

### Java (executable)

Java is compiled with the JDK's own `javax.tools.JavaCompiler` and then run in a
**separate child JVM**, not inside the backend process. The entry point is the
first public top-level type in the source (or the first type when none is
public); its simple name is used as the file name and an optional `package`
declaration is honoured. It must declare
`public static void main(String[] args)`.

```java
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String name = scanner.nextLine();
        System.out.println("Hello, " + name + "!");
    }
}
```

Standard input is the child's real `System.in`, so `Scanner`, `BufferedReader`
and `System.in.read()` work normally. `System.out` is `output`; a non-zero exit
status or a runtime exception makes `success` false and puts the stderr text in
`error`, while output printed before the failure is still returned. Output line
endings are normalised to `\n` so the API behaves the same on every host.

The child JVM gets `-Xmx128m`, a private working directory, its own temporary
directory and a cleared environment; it is killed when the execution timeout
expires. That isolates crashes and `System.exit(...)` from the backend, but it is
**process isolation, not a security sandbox**: guest Java code can still read and
write files and open network connections as the backend user. See
[Current limitations](#current-limitations).

Validation limits (enforced before any executor is reached):

| Field | Rule |
|---|---|
| `language` | required, at most 32 characters; one of the nine known ids, case-insensitive |
| `code` | required, at most 100,000 characters |
| `input` | optional, at most 10,000 characters; null is treated as empty |

Current behaviour and status codes:

| Situation | Status | Body |
|---|---|---|
| JavaScript, Python or Java request | `200 OK` | `ExecutionResponse`; `success` is false for compile/runtime errors |
| Valid request, known language without an engine | `501 Not Implemented` | `ApiError` explaining that no engine is available yet |
| Unknown language id | `400 Bad Request` | `ApiError` listing the known ids |
| Blank/missing/oversized field | `400 Bad Request` | `ApiError` with the failing field(s) |
| Malformed or missing JSON | `400 Bad Request` | `ApiError` |
| Unexpected server failure | `500 Internal Server Error` | generic `ApiError`, details only in logs |

As of Day 12 **JavaScript, Python and Java** execute. Ruby, C, C++, Rust, Go and
R still return an honest `501` — no output is simulated for them. Ruby is blocked
by TruffleRuby's lack of Windows support on this machine; C/C++/Rust/Go/R need
external toolchains; the embedded engine only serves languages whose Truffle
artifacts are present.

## Architecture

```
HTTP -> ExecutionController   (validation, status codes)
          |
          v
        ExecutionService      (language resolution, executor routing)
          |
          v
        CodeExecutor          (one bean per language:
          |                    JavaScriptExecutor, PythonExecutor, JavaExecutor)
          |
          +--> GraalVmSandbox  (fresh locked-down Context per request)
          |         |
          |         v
          |       GraalVmEngine (one shared, lazily created polyglot Engine)
          |
          +--> JavaExecutionSandbox (javac in-process, program in a child JVM)
                    |
                    v
        ExecutionResult       (internal) -> ExecutionResponse (API)
```

- `ExecutionRequest` / `ExecutionResponse` / `ApiError` are the API contract.
- `ExecutionResult` stays internal so adapters can evolve without breaking clients.
- `GraalVmEngine` owns the single thread-safe `Engine`, creates it lazily and
  reports the languages actually installed on the classpath.
- `GraalVmSandbox` creates a new `Context` per execution, captures stdout/stderr,
  applies the timeout and caps output. It never reuses a guest context.
- `JavaExecutionSandbox` compiles in-process with `javax.tools`, then launches the
  program in a child JVM with a 128 MB heap, a cleared environment and a private
  temporary directory. The parent kills the child when the timeout expires.
- `JavaScriptExecutor` and `PythonExecutor` delegate to the GraalVM engine;
  `JavaExecutor` delegates to the JDK toolchain. Availability is checked at
  runtime, so a missing artifact or a JRE without `javax.tools` means an honest
  `501`, never fake output.
- Executors are injected as a list and indexed by `Language`; adding a language
  is a new bean, not a change to the controller or service.
- `GlobalExceptionHandler` gives every error one JSON shape and hides stack
  traces and internal messages on `500`.

## Configuration

`src/main/resources/application.properties`:

| Property | Value |
|---|---|
| `server.port` | `8080` (checked free on the development machine) |
| `spring.application.name` | `PolyglotMesh` |
| `management.endpoints.web.exposure.include` | `health,info` |
| `polyglotmesh.cors.allowed-origins` | `http://localhost:5173`, `http://127.0.0.1:5173` |
| `polyglotmesh.graalvm.enabled` | `true`; master switch for the GraalVM engine (JavaScript/Python). Java uses the JDK toolchain independently. |
| `polyglotmesh.graalvm.execution-timeout` | `5s`; wall-clock limit per request (GraalVM evaluation and Java child JVM) |
| `polyglotmesh.graalvm.max-output-chars` | `100000`; stdout/stderr cap |
| `polyglotmesh.graalvm.max-error-chars` | `20000`; error text cap |

CORS applies to `/api/**` only and deliberately does not use `*`, so the API is
not opened to arbitrary browser origins. Add the Vite preview origin (4173) or a
deployed origin to the property when there is a real need.

## Tests

| Test class | Covers |
|---|---|
| `PolyglotMeshApplicationTests` | Context startup, `/api/health`, `/actuator/health` on a real random port |
| `HealthControllerTest` | Web slice: JSON body, content type, unknown API path |
| `CorsConfigurationTest` | Allowed origin, preflight, rejected origin on the running app |
| `ExecutionRequestValidationTest` | Required fields, blank values, size limits, optional input |
| `ExecutionServiceTest` | Executor routing, case-insensitive ids, unknown language, unavailable engine, duplicate executors, result mapping |
| `ExecutionControllerTest` | 501 without executor beans, 400 for invalid/blank/unknown/malformed requests, 405 |
| `GlobalExceptionHandlerTest` | Generic 500 that does not leak exception details |
| `GraalVmEngineTest` | Engine availability, installed-language detection (`js`, `python`), disabled mode |
| `GraalVmSandboxTest` | Real GraalJS runs: output capture, runtime/syntax errors, host-access denial, timeout cancellation, output cap, stdin helpers, context isolation |
| `GraalVmPythonSandboxTest` | Real GraalPy runs: output, `input()`, runtime/syntax errors, host and file denial, timeout cancellation, output cap, context isolation |
| `JavaExecutionSandboxTest` | Real JDK compile/run: output, `Scanner` stdin, custom/packaged entry class, compile diagnostics, runtime exception, non-zero exit, `System.exit` isolation, timeout cancellation, output cap |
| `JavaScriptExecutionIntegrationTest` | End-to-end `/api/execute`: JavaScript success/failure, stdin, and 501 for Ruby |
| `PythonExecutionIntegrationTest` | End-to-end `/api/execute`: Python success, stdin, and runtime errors |
| `JavaExecutionIntegrationTest` | End-to-end `/api/execute`: Java success, stdin, compile failure, and 501 for Ruby |

## Current limitations

- **JavaScript, Python and Java execute.** Ruby, C, C++, Rust, Go and R return
  `501`; there is no simulated output for them.
- **Ruby is blocked on this Windows machine.** TruffleRuby 23.1 is a Linux/macOS
  runtime: language initialisation throws
  `UnsupportedOperationException: Unsupported operating system: Windows 11`
  before any Ruby code runs. No Ruby, JRuby or WSL runtime is installed here, so
  the adapter cannot be verified and Ruby stays `501` rather than shipping code
  that only pretends to work. The `ruby-community` dependency is therefore not in
  `pom.xml`.
- Java is compiled with `javax.tools` and run in a **child JVM**, so the backend
  survives `System.exit(...)` and crashed programs. This is process isolation,
  not a security boundary: guest Java code can still access files and the
  network as the backend user. The child is limited to a 128 MB heap and is
  killed on timeout.
- Java's entry point is detected heuristically as the first public top-level
  type (or the first type when none is public); its simple name must match the
  program's public class name, as normal `javac` requires. A source file without
  a class/record/enum/interface declaration is rejected before compilation.
- Generated class files live in a per-request temporary directory that is
  deleted after the run.
- On the stock JDK 21 used here, GraalJS and GraalPy run in Truffle's fallback
  interpreter mode (no runtime compilation). Starting the jar on the installed
  GraalVM JDK enables compiled execution, but that is not required for
  correctness. GraalPy is noticeably slower to start in interpreter mode.
- JavaScript and Python execute in-process. Their `Context` sandbox denies
  host/IO/process access and enforces a timeout, but it does **not** impose a hard
  per-request CPU or memory quota, and it is not a container boundary. Do not
  describe it as production isolation.
- Request limits remain at the API boundary; output caps and the timeout are the
  executor-level limits available today.
- No authentication, database or persistence.
- Standard input is language-idiomatic: `readLine()`/`readAll()` for JavaScript,
  real `sys.stdin`/`input()` for Python, and the child's real `System.in` for
  Java. Other languages will get their own bridge with their adapters.
