# PolyglotMesh Backend

Spring Boot service for PolyglotMesh, the GraalVM multi-language runtime sandbox.
Week 2 built the service incrementally; Week 3 hardens it. Day 15 added
defence-in-depth request validation: a byte-level body limit for `/api/execute`,
strict JSON that rejects unknown fields, explicit `allowAllAccess(false)` on
GraalVM contexts, and regression tests for the security boundary. Day 16 adds
bounded resource management: separate setup/execution/compile budgets, a fixed
sandbox worker pool, tracked-context shutdown and long-running-program tests.
Day 17 standardises execution diagnostics: stdout and stderr are separate, every
result carries a `status` classification, compiler diagnostics are preserved and
internal stack traces are never returned. Day 18 adds observability: every
request gets a correlation id, every completed execution is counted and timed by
language and outcome, and one structured log line records the outcome without
logging source code or standard input. JavaScript (GraalJS), Python (GraalPy)
and Java (JDK toolchain) execute for real, `GET /api/languages` reports
capabilities, the execution endpoint is protected by per-client rate limits and
bounded concurrency, and shutdown is graceful. Every other language answers
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
| GET | `/api/languages` | Read-only capability list | every language with `available` true/false |
| POST | `/api/execute` | Unified execution endpoint | `200` with the execution result; `501` for a known language without an engine; `429` when over quota/busy; `413` for an oversized body; `400` for invalid input |
| GET | `/actuator/health` | Actuator liveness/readiness | `{"status":"UP"}` |
| GET | `/actuator/info` | Application and build metadata | app name/description plus `build` version and artifact |

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
  "stderr": "",
  "status": "SUCCESS",
  "executionTimeMs": 15
}
```

`output` is standard output only and `stderr` is standard error only; both keep
the program's own newlines. `error` is `null` on success and carries the
diagnostic otherwise (compiler output, exception message or timeout notice).
`status` classifies the outcome and is always present. Output produced before a
failure is still returned.

A failed program still returns HTTP `200` with `success: false`: the request was
valid and the response is a real execution result, not an API error. For example:

```json
{
  "success": false,
  "language": "python",
  "output": "",
  "error": "Traceback (most recent call last): ... ZeroDivisionError: division by zero",
  "stderr": "",
  "status": "RUNTIME_ERROR",
  "executionTimeMs": 42
}
```

| `status` | Meaning |
|---|---|
| `SUCCESS` | The program compiled (when applicable) and exited successfully |
| `COMPILE_ERROR` | The compiler rejected the source; `error` carries its diagnostics |
| `SYNTAX_ERROR` | The interpreter rejected the source before running it |
| `RUNTIME_ERROR` | The program started but raised an exception or exited non-zero |
| `TIMEOUT` | A setup, compilation or execution budget was exceeded |
| `CANCELLED` | The request was interrupted or the service is shutting down |
| `INTERNAL_ERROR` | The server failed for a reason that is not the program's fault; the message is generic |

API-level failures keep their existing HTTP status and `ApiError` shape:
unknown language `400`, malformed JSON or validation failures `400`, oversized
body `413`, known language without a runtime `501`, quota/concurrency `429`, and
unexpected server failures `500`. None of those responses include a `success`,
`output`, `stderr` or execution `status` field; they use the error shape instead.

### Language discovery

`GET /api/languages` lists all nine editor languages and whether this deployment
can execute them. Availability comes from the bundled language provider classes
and the JDK toolchain, not from a hard-coded promise; checking it does not start
the GraalVM engine or a child JVM.

```json
{
  "languages": [
    {"id":"java","displayName":"Java","available":true},
    {"id":"javascript","displayName":"JavaScript","available":true},
    {"id":"python","displayName":"Python","available":true},
    {"id":"ruby","displayName":"Ruby","available":false}
  ]
}
```

The real response contains all nine languages in editor order. Unavailable
languages can still be sent to `/api/execute`; they answer `501` rather than
silently substituting another runtime.

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
creation, native access, thread creation and environment variables. `console.log`
writes to `output` and `console.error` writes to `stderr`. A wall-clock timeout
(default 5 s) cancels infinite loops, and output/error text is capped.

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
capped output. `print()` writes to `output` and `sys.stderr` writes to `stderr`.
GraalPy starts more slowly than GraalJS in interpreter mode, so the first Python
request after startup can take several seconds.

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
and `System.in.read()` work normally. `System.out` is `output` and `System.err`
is `stderr`. A non-zero exit status or a runtime exception makes `success` false,
classifies the result `RUNTIME_ERROR` and puts the diagnostic in `error`, while
output printed before the failure is still returned. Compiler diagnostics keep
their line numbers and are classified `COMPILE_ERROR`. Output line endings are
normalised to `\n` so the API behaves the same on every host.

The child JVM gets `-Xmx128m`, a private working directory, its own temporary
directory and a cleared environment; it is killed when the execution timeout
expires. Compilation runs on a bounded daemon pool with its own timeout
(default 20 s), so a pathological source cannot hold the request thread or spawn
unbounded compile threads. That isolates crashes and `System.exit(...)` from the
backend, but it is
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
| JavaScript, Python or Java request within quota | `200 OK` | `ExecutionResponse` with `status`; `success` is false for compile/runtime/timeout outcomes |
| Valid request, known language without an engine | `501 Not Implemented` | `ApiError` explaining that no engine is available yet |
| Client over its rate limit, or no execution slot free in time | `429 Too Many Requests` | `ApiError` plus a `Retry-After` header |
| Unknown language id | `400 Bad Request` | `ApiError` listing the known ids |
| Blank/missing/oversized field | `400 Bad Request` | `ApiError` with the failing field(s) |
| Unknown JSON field | `400 Bad Request` | `ApiError` (strict JSON contract) |
| Malformed or missing JSON | `400 Bad Request` | `ApiError` |
| Body over the byte limit | `413 Payload Too Large` | `ApiError` from the request-size guard |
| Unexpected server failure | `500 Internal Server Error` | generic `ApiError`, details only in logs |

As of Day 17 **JavaScript, Python and Java** execute. Ruby, C, C++, Rust, Go and
R still return an honest `501` — no output is simulated for them. Ruby is blocked
by TruffleRuby's lack of Windows support on this machine; C/C++/Rust/Go/R need
external toolchains; the embedded engine only serves languages whose Truffle
artifacts are present. `GET /api/languages` reports the same availability without
probing each language with a real execution.

## Security controls

The execution API is hardened in layers. None of these layers is a substitute
for operating-system or container isolation.

| Layer | Control |
|---|---|
| Request size | `RequestSizeLimitFilter` rejects a `POST /api/execute` body larger than `polyglotmesh.security.max-request-bytes` (default 1 MiB) with `413` **before** Jackson parses it. It reads at most limit + 1 bytes, so an attacker cannot make the backend buffer an unbounded payload. |
| JSON contract | `spring.jackson.deserialization.fail-on-unknown-properties=true` rejects any field other than `language`, `code` and `input` with `400`. Malformed or missing JSON also returns `400`. |
| Field validation | `@NotBlank` rejects null/blank `code` and `language`; `@Size` caps `language` at 32, `code` at 100,000 and `input` at 10,000 characters. Unknown language ids are rejected with `400` before any executor runs. |
| Error disclosure | `GlobalExceptionHandler` returns one `ApiError` shape and hides stack traces, exception class names and internal messages on `500`. |
| GraalVM context | A fresh `Context` per request with explicit `allowAllAccess(false)`, `HostAccess.NONE`, host class lookup and loading disabled, `IOAccess.NONE`, and thread, process, native and environment access denied. GraalPy's Python-visible `os.environ`/`posix.environ` copies are cleared before guest code runs, because GraalPy's posix backend reads the host environment independently of `EnvironmentAccess.NONE`. Guest code cannot reach `Java.type`, `Polyglot`, files, sockets or process environment variables. |
| Response headers | Execution responses carry `X-Content-Type-Options: nosniff` and `Cache-Control: no-store`. |
| Java execution | Compiled in-process and run in a child JVM with a 128 MB heap, a cleared environment, a private working directory and a kill-on-timeout policy. This is **process isolation, not a security sandbox**: guest Java code can still read and write files and open network connections as the backend user. |

Application-level validation protects the API contract and the backend process;
it does **not** provide operating-system-level sandboxing. The GraalVM context
restrictions and the Java child JVM reduce accidental damage, but a determined
guest program is still constrained mainly by the host account and the absence of
a container boundary.

## Architecture

```
HTTP -> RequestIdFilter          (correlation id, MDC, X-Request-Id)
          |
          v
        RequestSizeLimitFilter   (POST /api/execute body cap; 413 before parsing)
          |
          v
        ExecutionController      (validation, status codes)
          |
          v
        ExecutionGate            (per-client quota + bounded concurrency; 429)
          |
          v
        ExecutionService         (language resolution, executor routing)
          |
          +--> ExecutionTelemetry (counter + timer by language/status; structured log)
          |
          v
        CodeExecutor             (one bean per language:
          |                       JavaScriptExecutor, PythonExecutor, JavaExecutor)
          |
          +--> GraalVmSandbox  (fresh locked-down Context per request)
          |         |
          |         v
          |       GraalVmEngine (one shared, lazily created polyglot Engine)
          |
          +--> JavaExecutionSandbox (javac on a bounded pool, program in a child JVM)
                    |
                    v
        ExecutionResult       (internal) -> ExecutionResponse (API)

HTTP -> LanguageController -> ExecutionService.languageStatuses()
```

- `ExecutionRequest` / `ExecutionResponse` / `ApiError` are the API contract.
- `RequestIdFilter` assigns a safe correlation id (or keeps a well-formed
  caller-supplied one), publishes it as `X-Request-Id`, and puts it in the MDC so
  every log line from that request can be correlated.
- `RequestSizeLimitFilter` runs before Spring MVC on `POST /api/execute`, reads at
  most the configured limit + 1 bytes, and returns `413` before Jackson parses an
  oversized body. It also sets `X-Content-Type-Options: nosniff` and
  `Cache-Control: no-store` on execution responses.
- `ExecutionTelemetry` records one counter and one timer per completed execution,
  tagged by language and outcome, and writes one structured log line. It never
  receives source code, stdin or program output.
- `ExecutionResult` stays internal so adapters can evolve without breaking clients.
- `ExecutionGate` runs before any language is resolved. It enforces a
  fixed-window per-client request quota and a bounded number of concurrent
  executions, failing fast with `429` and `Retry-After` instead of queueing
  without bound. Its permit is closed in a `try`-with-resources block, so a
  failed execution cannot leak a slot.
- `GraalVmEngine` owns the single thread-safe `Engine`, creates it lazily and
  decides availability from the bundled provider classes, so discovery does not
  start the engine or a child JVM.
- `GraalVmSandbox` creates a new `Context` per execution, captures stdout/stderr,
  applies the setup and execution budgets separately and caps output. Context
  creation and stdin installation run on the worker pool under `setup-timeout`;
  guest evaluation runs under `execution-timeout`. A fixed pool (sized by
  `max-concurrent-executions`) bounds threads, active contexts are tracked and
  closed on shutdown, and no guest context is ever reused.
- `JavaExecutionSandbox` compiles with `javax.tools` on a bounded daemon pool,
  then launches the program in a child JVM with a 128 MB heap, a cleared
  environment and a private temporary directory. Compilation and child execution
  have independent budgets, running children and temp directories are tracked,
  timed-out compile directories are removed after a delay, and stale directories
  from a previous crash are swept at startup.
- `LanguageController` exposes `GET /api/languages`; `ExecutionService` reports
  every product language with its real availability.
- `JavaScriptExecutor` and `PythonExecutor` delegate to the GraalVM engine;
  `JavaExecutor` delegates to the JDK toolchain. Availability is checked at
  runtime, so a missing artifact or a JRE without `javax.tools` means an honest
  `501`, never fake output.
- Executors are injected as a list and indexed by `Language`; adding a language
  is a new bean, not a change to the controller or service.
- `GlobalExceptionHandler` gives every error one JSON shape, adds
  `Retry-After` to `429`, and hides stack traces and internal messages on `500`.

## Configuration

`src/main/resources/application.properties`:

| Property | Value |
|---|---|
| `server.port` | `8080` (checked free on the development machine) |
| `spring.application.name` | `PolyglotMesh` |
| `server.shutdown` | `graceful`; stop accepting new work and let in-flight executions finish |
| `spring.lifecycle.timeout-per-shutdown-phase` | `45s`; upper bound for the graceful phase (covers a Java compile plus child run) |
| `management.endpoints.web.exposure.include` | `health,info` |
| `spring.jackson.deserialization.fail-on-unknown-properties` | `true`; unknown JSON fields are rejected instead of ignored |
| `polyglotmesh.security.max-request-bytes` | `1048576` (1 MiB); byte limit for a `POST /api/execute` body, enforced before JSON parsing |
| `polyglotmesh.cors.allowed-origins` | `http://localhost:5173`, `http://127.0.0.1:5173` |
| `polyglotmesh.graalvm.enabled` | `true`; master switch for the GraalVM engine (JavaScript/Python). Java uses the JDK toolchain independently. |
| `polyglotmesh.graalvm.setup-timeout` | `60s`; context creation plus stdin installation, separate from guest execution |
| `polyglotmesh.graalvm.execution-timeout` | `5s`; guest-code evaluation only (not context setup, not Java) |
| `polyglotmesh.graalvm.max-output-chars` | `100000`; stdout/stderr cap |
| `polyglotmesh.graalvm.max-error-chars` | `20000`; error text cap |
| `polyglotmesh.execution.rate-limit-enabled` | `true`; per-client request throttling |
| `polyglotmesh.execution.rate-limit-requests` | `60`; valid execution requests per client per window |
| `polyglotmesh.execution.rate-limit-window` | `1m`; fixed-window length |
| `polyglotmesh.execution.max-concurrent-executions` | `4`; execution slots and sandbox worker threads |
| `polyglotmesh.execution.acquire-timeout` | `2s`; how long a request waits for a slot before `429` |
| `polyglotmesh.execution.compile-timeout` | `20s`; upper bound for compiling one Java program |
| `polyglotmesh.execution.java-execution-timeout` | `10s`; upper bound for running the compiled Java child JVM |
| `polyglotmesh.execution.stale-work-dir-age` | `1h`; abandoned Java temp directories older than this are swept at startup |

The timeouts are deliberately separate because they cover different work:

| Budget | Default | Measured on this host |
|---|---|---|
| GraalVM context setup | `60s` | 5.8 s (first JavaScript), 21.6 s (first Python) |
| GraalVM guest execution | `5s` | ~32 ms warm (JavaScript), ~1.0 s warm (Python) |
| Java compilation | `20s` | ~3 s for a trivial program; Week 2 saw 8–13 s under load |
| Java child-JVM execution | `10s` | ~0.8 s warm |

The setup budget is large because the first GraalPy context is expensive; it is
not charged to the program, and guest code still gets only 5 s. Do not lower the
compile budget to the execution budget: `javac` cold-start and slow disks need
the larger allowance. Lower a value only after measuring the host.

CORS applies to `/api/**` only and deliberately does not use `*`, so the API is
not opened to arbitrary browser origins. Add the Vite preview origin (4173) or a
deployed origin to the property when there is a real need.

## Operations

- `/actuator/info` exposes the application name/description and, because the
  Maven build runs Spring Boot's `build-info` goal, the project version and
  artifact name. It is a safe way to confirm which build is running.
- Shutdown is graceful: Spring stops accepting new requests, waits up to 45 s
  for in-flight executions, then tears down the context. `@PreDestroy` closes
  tracked GraalVM contexts, kills any tracked child JVM and deletes tracked
  temporary directories.
- The GraalVM sandbox uses a fixed worker pool sized by
  `max-concurrent-executions`. It cannot create unbounded threads, and after
  shutdown new requests fail fast with "execution service is shutting down"
  instead of hanging.
- A forced kill (for example `Stop-Process -Force` on Windows) bypasses graceful
  shutdown; the next startup sweeps stale Java work directories.

## Observability

Every request gets a correlation id. A caller may send `X-Request-Id`; the value
is echoed only when it matches `[A-Za-z0-9._:-]{8,128}`, otherwise a UUID is
generated. The id is returned in the same header, placed in the logging MDC for
the duration of the request, and exposed to allowed CORS origins so browser
clients can read it. Rejecting unsafe values prevents log injection and response
splitting.

Each completed execution writes one line at `INFO`:

```text
execution requestId=3f1c9e2a-... language=javascript status=SUCCESS success=true durationMs=32
```

The line never contains source code, standard input, program output or error
text; those stay in the API response. Program failures are normal outcomes and
are logged at `INFO` with their `status`, not as warnings.

Micrometer metrics (in-memory, one instance):

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `polyglotmesh.executions` | counter | `language`, `status` | Completed executions |
| `polyglotmesh.execution.duration` | timer | `language`, `status` | Execution time reported by the sandbox |
| `http.server.requests` | timer | Spring Boot defaults | End-to-end HTTP latency and status |

`GET /actuator/metrics` and `GET /actuator/metrics/{name}` are exposed read-only;
for example `/actuator/metrics/polyglotmesh.executions?tag=language:javascript`.
Metrics are aggregate operational data and never include program text. The
endpoint is not covered by CORS, so a browser page from another origin cannot
read it. The registry is in memory and resets on restart; a multi-instance or
long-lived deployment should add a registry such as Prometheus. Micrometer is
already provided by Spring Boot Actuator, so this adds no dependency.

## Tests

| Test class | Covers |
|---|---|
| `PolyglotMeshApplicationTests` | Context startup, `/api/health`, `/actuator/health`, `/actuator/info` build metadata, exposed `/actuator/metrics`, `/api/languages` availability on a real random port |
| `HealthControllerTest` | Web slice: JSON body, content type, unknown API path |
| `CorsConfigurationTest` | Allowed origin, preflight, exposed `X-Request-Id`, rejected origin on the running app |
| `ExecutionRequestValidationTest` | Required fields, null/blank values, size limits, boundary values, optional input |
| `ExecutionServiceTest` | Executor routing, case-insensitive ids, unknown language, unavailable engine, duplicate executors, result mapping (stdout, stderr, diagnostic, status), availability filtering and statuses |
| `ExecutionResultTest` | Internal result invariants: separate streams, explicit status, default classification, rejection of inconsistent results |
| `ExecutionResponseTest` | Public DTO mapping of language, stdout, stderr, diagnostic, status and timing |
| `ExecutionGateTest` | Per-client quota, window reset, client isolation, disabled limiting, concurrency rejection, idempotent permit release |
| `ExecutionPropertiesTest` | Safe defaults, and independent compile versus Java run budgets |
| `GraalVmPropertiesTest` | Setup timeout defaults separately from guest execution |
| `ExecutionControllerTest` | Real gate in the web slice: 501 without executor beans, 400 for invalid/null/blank/unknown/malformed requests and unknown JSON fields, 400 for oversized stdin, 413 for an oversized body (including a trailing-slash path), security headers, 405 |
| `GlobalExceptionHandlerTest` | Generic 500 that does not leak exception details, 429 with `Retry-After` |
| `GraalVmEngineTest` | Engine availability, installed-language detection (`js`, `python`), provider detection without engine start, disabled mode |
| `GraalVmSandboxTest` | Real GraalJS runs: separate stdout/stderr, status classification (success, syntax, runtime, timeout, cancelled), runtime/syntax errors, host-access denial, timeout cancellation, output cap, stdin helpers, context isolation, rejection after shutdown |
| `GraalVmPythonSandboxTest` | Real GraalPy runs: separate stdout/stderr, status classification, `input()`, runtime/syntax errors, host, file and environment denial, timeout cancellation, output cap, context isolation |
| `JavaExecutionSandboxTest` | Real JDK compile/run: separate stdout/stderr, status classification (success, compile, runtime, timeout), `Scanner` stdin, custom/packaged entry class, compile diagnostics, runtime exception, non-zero exit, `System.exit` isolation, independent execution and compile timeouts, output cap, stale-directory sweep |
| `JavaScriptExecutionIntegrationTest` | End-to-end `/api/execute`: JavaScript success/failure with `status`, separate stderr, stdin, and 501 for Ruby |
| `PythonExecutionIntegrationTest` | End-to-end `/api/execute`: Python success, separate stderr, stdin, runtime errors with `status` |
| `JavaExecutionIntegrationTest` | End-to-end `/api/execute`: Java success, separate stderr, stdin, compile failure with `COMPILE_ERROR`, and 501 for Ruby |
| `ExecutionTimeoutIntegrationTest` | End-to-end runaway programs for JavaScript, Python and Java: the configured budget stops them with `TIMEOUT`, and a later execution still succeeds |
| `ExecutionDiagnosticsIntegrationTest` | End-to-end classification of JavaScript/Python syntax errors, Java compile and runtime errors, compiler line numbers, absence of backend stack traces, and the 400/501 `ApiError` contract |
| `RequestIdFilterTest` | Correlation id generation, echo of well-formed ids, rejection of unsafe values, and MDC lifecycle/restoration |
| `ExecutionTelemetryTest` | Counter and timer tags/durations; the log line carries metadata and request id but never program text |
| `ObservabilityIntegrationTest` | End-to-end `X-Request-Id` generation/echo/replacement, execution counters and timers, and the exposed metrics endpoint |

## Verification

Recorded on the development machine; timings vary, functional results matter.

- Day 18 (28 November 2026), `mvn -B -ntp clean verify`: **158 tests, 0 failures,
  0 errors, 0 skipped**; `BUILD SUCCESS` in 4 min 12 s; jar written to `target/`.
  New coverage exercises correlation-id generation, echo of well-formed ids,
  rejection of unsafe values, MDC lifecycle and restoration, counter/timer tags
  and durations by language and outcome, the guarantee that the execution log
  line carries metadata but never source/stdin/output, and end-to-end
  `X-Request-Id` handling with the exposed metrics endpoint.
  The packaged-jar smoke test passed **19/19 checks** — health, build metadata,
  nine-language discovery, JavaScript success with an echoed request id,
  generated and replaced request ids, JavaScript syntax/runtime classification,
  Python success, Java success/compile error, `GET /actuator/metrics`, the
  execution counter (with and without a tag filter), the execution timer,
  unknown language `400`, malformed JSON `400`, Ruby `501`, and CORS. The run
  produced 8 `execution requestId=...` lines, and after shutdown there were no
  child `java` processes, no `polyglotmesh-java-*` temporary directories, and no
  `ERROR`/`WARN` lines in the application log.
- Day 17 (27 November 2026), `mvn -B -ntp clean verify`: **144 tests, 0 failures,
  0 errors, 0 skipped**; `BUILD SUCCESS` in 12 min 58 s; jar written to `target/`.
  The new coverage exercises the internal result invariants (separate streams,
  explicit status, inconsistent results rejected), public DTO mapping, and
  end-to-end classification: JavaScript/Python syntax errors, Java compile and
  runtime errors, preserved compiler line numbers, separate stdout/stderr,
  absence of backend stack traces, and the unchanged `400`/`501` `ApiError`
  contract. The suite ran under heavy memory pressure on this host, which made
  the child-JVM tests slow but did not change the result.
  The packaged-jar smoke test passed **18/18 checks** — health, build metadata,
  nine-language discovery, JavaScript success with separate stderr, JavaScript
  syntax/runtime classification, Python success/runtime, Java success/compile
  error/runtime error with partial stdout, a JavaScript infinite loop stopped at
  ~5.1 s with `TIMEOUT`, successful recovery immediately afterwards, unknown
  language `400`, malformed JSON `400`, Ruby `501`, and CORS. After shutdown there
  were no child `java` processes, no `polyglotmesh-java-*` temporary directories,
  and no `ERROR`/`WARN` lines in the application log.
- Day 16 (26 November 2026), `mvn -B -ntp clean verify`: **115 tests, 0 failures,
  0 errors, 0 skipped**; `BUILD SUCCESS` in 2 min 52 s; jar written to `target/`.
  New coverage exercises independent setup/execution/compile/Java-run budgets,
  JavaScript, Python and Java runaway programs stopped at their budget with the
  service recovering afterwards, and rejection of work after sandbox shutdown.
  The packaged-jar smoke test passed **10/10 checks** — health, build metadata,
  nine-language discovery, JavaScript, Python, Java, a JavaScript infinite loop
  stopped at 5000 ms, a successful execution immediately afterwards, Ruby `501`,
  and CORS. After shutdown there were no child `java` processes, no
  `polyglotmesh-java-*` temporary directories, and no `ERROR`/`WARN` lines in the
  application log.
- Day 15 (25 November 2026), `mvn -B -ntp clean verify`: **107 tests, 0 failures,
  0 errors, 0 skipped**; `BUILD SUCCESS` in 3 min 24 s; jar written to `target/`.
  The new coverage exercises the `413` body guard (including a trailing-slash
  path), strict unknown-field rejection, null/empty code, oversized stdin,
  security response headers, and GraalPy environment-variable denial.
- Day 14 (24 November 2026), packaged jar: `mvn -B -ntp clean verify` reported
  96 tests, 0 failures. The live smoke test on `http://localhost:8080` passed
  **11/11 checks** — health, `/actuator/info` (app name plus build
  version/artifact), nine-language discovery, JavaScript, Python, Java, Ruby
  `501`, CORS, bounded-concurrency `429` (`Retry-After: 1`), rate-limit `429`
  (`Retry-After: 60`), and a read-only endpoint still answering after throttling.
  After shutdown there were no child `java` processes, no
  `polyglotmesh-java-*` temporary directories, and no `ERROR`/`WARN` lines in the
  application log.

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
  deleted after the run. A timed-out compile keeps its directory for 30 s before
  cleanup, and directories older than `stale-work-dir-age` are swept at startup.
- Admission control is **in-memory and per instance**. The rate-limit key is the
  caller's remote address; forwarded headers are deliberately not trusted, so a
  deployment behind a proxy needs enforcement at the proxy or a verified client
  identity. The fixed window can allow a burst at a window boundary, and a
  multi-instance deployment shares no quota.
- The `429` path protects capacity, not fairness: a client that stays under its
  quota can still occupy all execution slots, and waiting requests give up after
  `acquire-timeout` rather than queueing.
- Request limits remain at the API boundary; output caps, the setup/execution
  timeouts and the compile/run timeouts are the executor-level limits available
  today.
- Cold GraalPy setup is bounded by `polyglotmesh.graalvm.setup-timeout` (60 s)
  but still takes ~22 s on this host and is not charged to the program's 5 s
  execution budget. There is no startup warm-up yet, so the first Python request
  after a restart is slow.
- The `413` guard caps the whole JSON body, not each field: a request can fit
  under 1 MiB and still be rejected with `400` for oversized `code` or `input`.
  The filter buffers the accepted body in memory, so lower
  `polyglotmesh.security.max-request-bytes` on a memory-constrained host.
- These are application-level controls. They do not create an operating-system
  sandbox, a container boundary or a network policy; run the service in a
  restricted account or container for real isolation.
- On the stock JDK 21 used here, GraalJS and GraalPy run in Truffle's fallback
  interpreter mode (no runtime compilation). Starting the jar on the installed
  GraalVM JDK enables compiled execution, but that is not required for
  correctness. GraalPy is noticeably slower to start in interpreter mode.
- JavaScript and Python execute in-process. Their `Context` sandbox denies
  host/IO/process access and enforces a timeout, but it does **not** impose a hard
  per-request CPU or memory quota, and it is not a container boundary. Do not
  describe it as production isolation.
- GraalPy's Python-visible environment is cleared before guest code runs. This
  closes `os.environ`, `os.getenv`, `posix.environ` and `nt.environ`, but it is a
  Python-level mitigation, not an OS boundary; native access is separately
  disabled, so a guest cannot re-read the process environment through a native
  extension.
- No authentication, database or persistence.
- Standard input is language-idiomatic: `readLine()`/`readAll()` for JavaScript,
  real `sys.stdin`/`input()` for Python, and the child's real `System.in` for
  Java. Other languages will get their own bridge with their adapters.
