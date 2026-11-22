# Language Runtime Compatibility Matrix - PolyglotMesh

Status of this document: **reference documentation**. It describes the intended language runtime
architecture of PolyglotMesh and records, language by language, what the repository actually
implements today.

- **Repository:** `https://github.com/sthtavarthi-maker/PolyglotMesh`
- **Documented branch:** `leelavardhini-dev`
- **Baseline commit:** `cca6061` ("Day 12: Execute Java with the JDK toolchain"), merged into
  this branch
- **Last reviewed:** 23 November 2026
- **Scope:** language runtimes, execution adapters, host toolchain requirements and sandbox
  controls. No application code is changed by this document.

> **Read this first.** As of Day 12, **JavaScript, Python and Java execute end to end** through
> `POST /api/execute`. JavaScript and Python run on GraalJS and GraalPy inside a locked-down
> Polyglot context; Java is compiled with the JDK compiler and run in a child JVM with a 128 MB
> heap, a cleared environment and a 5-second timeout. The other **six languages still return
> `501 Not Implemented`** because no adapter exists for them. The in-process engines have no hard
> CPU/memory quota, and Java's child process is process isolation rather than a security boundary:
> guest Java code can still read and write files and open network connections as the backend user.
> Editor-only languages must not be described as runnable.

---

## 1. Introduction

PolyglotMesh is a browser-based, multi-language compiler workspace built on **Java 21 +
GraalVM**. The long-term goal is to let a user pick a language, write a program, provide stdin
and run it from one interface, without installing a toolchain locally and without a separate
execution service or third-party judge.

Multi-language runtime support matters for three reasons:

1. **One product, many ecosystems.** Learners and reviewers should be able to compare the same
   algorithm across Java, Python, JavaScript, Ruby, C, C++, Rust, Go and R without leaving the
   platform or managing nine local environments.
2. **One execution contract.** A single request/response format for every language keeps the
   frontend simple and lets the backend add a runtime without changing the UI.
3. **Explicit capability boundaries.** Editor support and execution support are different
   features. PolyglotMesh labels them separately so a language that can be edited is never
   presented as a language that can be run.

The runtime design is intentionally split in two:

- **JVM-embedded languages** (Python, JavaScript, and later Ruby) run inside the backend JVM
  through the GraalVM Polyglot API. **Java** is compiled in-process with the JDK compiler API but
  executed in a child JVM, so guest code cannot crash or exit the backend process.
- **Native-toolchain languages** (C, C++, Rust, Go, R) cannot run inside the JVM. They require a
  host compiler or interpreter and an isolated child process.

Because these two groups have different trust models, PolyglotMesh routes them through
**independent execution adapters** behind one unified execution API (section 4).

### 1.1 Status vocabulary used in this document

| Status | Meaning |
|---|---|
| **Planned** | Target runtime is named and designed, but no dependency, adapter or wiring exists. |
| **Configured** | Editor metadata (Monaco mode, file name, starter program, runtime label) exists in the repository. This is **not** execution support. |
| **Implemented** | Runtime dependency and adapter code exist and can execute a program. |
| **Tested** | Execution has been verified by a recorded automated or manual test. |

The repository currently reaches **Configured** for all nine editor modes, **Implemented and
Tested** for the JavaScript, Python and Java runtimes, and **Planned** for the other six execution
runtimes. JavaScript, Python and Java are the only languages that may be described as executable.

---

## 2. Language Runtime Compatibility Matrix

Execution Method and Integration Type describe the intended design. Implementation Status
describes the **current** state of the repository at the baseline commit. Since Day 12,
JavaScript and Python execute on GraalJS and GraalPy in-process, and Java compiles with the JDK
compiler and runs in a child JVM; the remaining six languages have no executor and return `501`.

| Programming Language | Runtime / Compiler | Execution Method | Integration Type | Implementation Status |
|---|---|---|---|---|
| Java | GraalVM JDK 21 / `javax.tools.JavaCompiler` | Compile in-process with the JDK compiler, then run in a child JVM (128 MB heap, cleared environment) | Hybrid (in-process compile, child JVM) | Editor **Configured**; runtime **Implemented** and **Tested** (Day 12) |
| Python | GraalPy 23.1.12 (`python-community`) | In-process `Context.eval("python", source)` in a locked-down context | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Implemented** and **Tested** (Day 11) |
| JavaScript | GraalJS 23.1.12 (`js-community`) | In-process `Context.eval("js", source)` in a locked-down context | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Implemented** and **Tested** (Day 10) |
| Ruby | TruffleRuby | In-process `Context.eval("ruby", source)` | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Planned** - TruffleRuby 23.1 does not start on Windows; no adapter |
| C | GCC (`gcc`) or Clang (`clang`) | Compile to a temporary executable, run it as a restricted child process | Out-of-process (native toolchain) | Editor **Configured**; runtime **Planned** - no compiler invocation exists |
| C++ | G++ (`g++`) or Clang++ (`clang++`) | Compile to a temporary executable, run it as a restricted child process | Out-of-process (native toolchain) | Editor **Configured**; runtime **Planned** - no compiler invocation exists |
| Rust | `rustc` | Compile to a temporary executable, run it as a restricted child process | Out-of-process (native toolchain) | Editor **Configured**; runtime **Planned** - no compiler invocation exists |
| Go | Go compiler (`go build`) | Compile to a temporary executable, run it as a restricted child process | Out-of-process (native toolchain) | Editor **Configured**; runtime **Planned** - no compiler invocation exists |
| R | R interpreter (`Rscript`) | Interpret the script directly in a restricted child process | Out-of-process (interpreter) | Editor **Configured**; runtime **Planned** - no interpreter invocation exists |

### 2.1 What "editor Configured" means (and does not mean)

All nine languages are present in `frontend/src/data/languages.ts`, which is the single source of
truth for editor modes:

| Language | Monaco language id | File name | Runtime label in metadata |
|---|---|---|---|
| Java | `java` | `Main.java` | `GraalVM JDK 21` |
| Python | `python` | `main.py` | `GraalVM Polyglot` |
| JavaScript | `javascript` | `main.js` | `GraalVM Polyglot` |
| Ruby | `ruby` | `main.rb` | `null` (shown as "Editor only") |
| C | `c` | `main.c` | `null` (shown as "Editor only") |
| C++ | `cpp` | `main.cpp` | `null` (shown as "Editor only") |
| Rust | `rust` | `main.rs` | `null` (shown as "Editor only") |
| Go | `go` | `main.go` | `null` (shown as "Editor only") |
| R | `r` | `main.r` | `null` (shown as "Editor only") |

The type definition in `frontend/src/types/language.ts` states the limitation directly: the
`runtime` field is "a description of the execution target, not proof that execution works".
`frontend/README.md` repeats that Run, Save and Share remain disabled for all nine languages.
Days 10-12 added working backend runtimes but did not connect the frontend, so the workspace UI
still disables Run; JavaScript, Python and Java are reachable through the REST API, not through
the browser yet.

For Ruby, C, C++, Rust, Go and R, the runtime label is `null`, so even the *intended* engine is
not recorded in the application. JavaScript and Python are the exceptions whose engines are
declared in `backend/pom.xml`; Java uses the JDK compiler and launcher that the backend already
requires, with no extra dependency. All three are implemented in `JavaScriptExecutor`,
`PythonExecutor` and `JavaExecutor` and exercised by automated tests. The other six engines in the
matrix come from the project plan and this design document, not from working code.

### 2.2 What exists and what is absent

Day 12 makes **Java** executable alongside JavaScript and Python:

- `JavaExecutor` and `JavaExecutionSandbox` are registered components; availability is checked at
  runtime from `ToolProvider.getSystemJavaCompiler()` plus a located `java` launcher, so a JRE
  answers `501` instead of failing with a server error;
- Java compiles in-process with the JDK `JavaCompiler` (`--release` matching the running JDK,
  `-proc:none`, UTF-8), and execution happens in a **child JVM**, not inside the backend process;
- the child gets `-Xmx128m`, `-XX:+UseSerialGC`, a private working directory, its own
  `java.io.tmpdir` and a cleared environment (only `TEMP`/`TMP` point into the workspace);
- the entry class is detected as the first public top-level type (or the first type when none is
  public), so single-file programs run; the child is killed when the shared timeout expires and the
  temporary workspace is deleted in a `finally` block;
- `System.exit(...)` and JVM crashes are contained by the child process, and compilation
  diagnostics are returned as structured errors;
- `backend/pom.xml` declares `polyglot`, `js-community` and `python-community`; Java needs no
  extra dependency because the JDK already ships the compiler and launcher;
- the test suite includes real GraalJS, GraalPy and Java runs plus end-to-end `/api/execute` tests.

Still absent:

- executors for Ruby, C, C++, Rust, Go and R, which all answer `501`;
- the TruffleRuby artifact, which is blocked on this Windows host;
- a hard per-request CPU or memory quota for the in-process engines;
- filesystem and network restrictions for guest code, and container or OS-level isolation;
- frontend integration, so the browser workspace still has Run disabled.

The backend genuinely executes three languages. It is not a general sandbox: JavaScript and Python
run inside the backend JVM, while Java is process-isolated but its child can still read and write
files and open network connections as the backend user. Java also pays a child-JVM start-up cost,
and GraalPy starts more slowly than GraalJS in interpreter mode.

---

## 3. Runtime Technologies

This section explains the engine chosen for each language and the constraints that come with it.
GraalJS and GraalPy are bundled as project dependencies, and Java uses the JDK compiler and
launcher that the backend already runs on. These three runtimes execute. The other six engines are
not installed or wired.

### 3.1 Java - JDK compiler and a child JVM (implemented)

Java is compiled in-process with `javax.tools.JavaCompiler`, then executed in a separate child JVM
launched from the running JDK.

- **Repository evidence:** `JavaExecutor` is a registered `CodeExecutor`; `JavaExecutionSandbox`
  compiles and launches programs; `JavaExecutionSandboxTest` and `JavaExecutionIntegrationTest`
  cover real compilation, stdin, diagnostics, `System.exit` isolation, timeout cancellation,
  output caps and `/api/execute`.
- **No extra dependency:** the JDK already ships the compiler (`javax.tools`) and the launcher, so
  availability is checked at runtime rather than declared in `pom.xml`.
- **Isolation:** the child JVM gets `-Xmx128m`, `-XX:+UseSerialGC`, a private working directory,
  `java.io.tmpdir` inside that workspace, a cleared environment and a forced kill when the timeout
  expires; the workspace is deleted in a `finally` block.
- **Caveat:** this is process isolation, not a security sandbox. The child runs as the backend user
  and can still read and write files and open network connections; compilation itself runs inside
  the backend JVM. Entry-point detection is heuristic (first public top-level type), and programs
  are expected to declare `public static void main(String[] args)`.

### 3.2 Python - GraalPy (implemented)

GraalPy is the GraalVM Python runtime, embedded through the Polyglot API
(`Context.eval("python", ...)`).

- **Repository evidence:** `org.graalvm.polyglot:python-community:23.1.12` is a runtime
  dependency; `PythonExecutor` is a registered `CodeExecutor`; `GraalVmPythonSandboxTest` and
  `PythonExecutionIntegrationTest` execute real Python through the API.
- **Sandbox settings:** the same locked-down context as JavaScript - no host access, class lookup,
  IO, threads, processes, native access or environment access; a fresh context per run.
- **Limits:** the shared 5-second timeout and the 100,000/20,000-character output and error caps.
- **Standard input:** `sys.stdin` is replaced with an in-memory stream, so `input()` behaves
  normally.
- **Caveat:** GraalPy is noticeably slower to start than GraalJS in interpreter mode, and Python
  packages beyond the bundled standard library are not available. On a stock JDK it also runs in
  Truffle's fallback interpreter mode.

### 3.3 JavaScript - GraalJS (implemented)

GraalJS is the GraalVM JavaScript engine, embedded through the Polyglot API
(`Context.eval("js", ...)`).

- **Repository evidence:** `org.graalvm.polyglot:js-community:23.1.12` is a runtime dependency;
  `JavaScriptExecutor` is a registered `CodeExecutor`; `GraalVmSandbox` executes real programs;
  `JavaScriptExecutionIntegrationTest` proves `/api/execute` returns program output.
- **Sandbox settings:** `HostAccess.NONE`, host class lookup and loading disabled, `IOAccess.NONE`,
  no new threads, no processes, no native access, no environment access, fresh `Context` per run.
- **Limits:** 5-second wall-clock timeout (configurable), 100,000-character output cap and
  20,000-character error cap.
- **Caveat:** on a stock JDK 21 (the development machine's default) GraalJS runs in Truffle's
  fallback interpreter mode without runtime compilation. Starting the jar on the GraalVM JDK
  enables compilation. Startup also pays a one-off engine initialisation cost.

### 3.4 Ruby - TruffleRuby (blocked on this host)

TruffleRuby is the GraalVM Ruby implementation and would be embedded the same way
(`Context.eval("ruby", ...)`).

- **Status:** still the least-defined integration. TruffleRuby is **not referenced** by the build;
  Ruby is editor-only with a `null` runtime label and answers `501`.
- **Blocked on this Windows machine:** TruffleRuby 23.1 is a Linux/macOS runtime and language
  initialisation throws `UnsupportedOperationException: Unsupported operating system: Windows 11`
  before any Ruby code runs. The `ruby-community` dependency is deliberately not in `pom.xml`, and
  no CRuby, JRuby or WSL runtime is installed here, so an adapter could not be verified.
- **Caveat:** embedding TruffleRuby from Maven is more involved than GraalJS or GraalPy, and gem
  availability is limited compared with CRuby. Choosing between embedded TruffleRuby and an
  out-of-process CRuby is an open decision that needs a supported host or a WSL/container path.

### 3.5 C - GCC or Clang

C cannot run inside the JVM. The intended method is to write the source to a temporary directory,
invoke `gcc` (or `clang`) to produce a native executable, then run that executable in a sandboxed
child process with a timeout.

- **Why an external compiler:** C is defined by its system toolchain and ABI; an in-JVM
  interpreter would not provide representative behaviour.
- **Caveat:** the host must have a compiler installed and the backend must never pass user input
  to a shell. Arguments must be passed as an argument vector.

### 3.6 C++ - G++ or Clang++

The same model as C, using `g++` or `clang++`.

- **Caveat:** C++ compilation is slower and more memory-hungry than C, so compile timeouts and
  output-size caps matter more. Template-heavy submissions can exhaust memory during compilation,
  before the program even runs.

### 3.7 Rust - rustc

Rust is compiled with `rustc` into a temporary executable and then run as a restricted child
process.

- **Why `rustc` directly:** avoids Cargo project scaffolding, network dependency resolution and a
  writable registry cache. Single-file programs are sufficient for a compiler workspace.
- **Caveat:** crates are unavailable under this model unless a pre-vendored registry is provided.
  That limitation must be communicated in the UI if Rust support ships.

### 3.8 Go - Go compiler

Go is compiled with `go build` (or `go run`) in a temporary module directory and then executed.

- **Why a module directory:** modern Go expects a module context even for a single file; building
  in a throwaway directory keeps the host workspace clean.
- **Caveat:** `GOCACHE`, `GOMODCACHE` and `GOPATH` must point at writable, disposable locations,
  and `GOFLAGS=-mod=mod` plus a disabled proxy are needed to guarantee offline builds.

### 3.9 R - R interpreter

R is interpreted with `Rscript` in a restricted child process.

- **Why an interpreter:** R has no compilation step in this workflow; `Rscript` gives a
  non-interactive run with a predictable exit code.
- **Caveat:** R startup loads site and user profiles, which can execute arbitrary code from the
  host environment. Runs must use `--vanilla` (or equivalent) and a controlled library path.

---

## 4. Execution Architecture

The architecture separates **one public contract** from **many independent adapters**. The goal
is that adding a language never changes the HTTP layer or the frontend. Day 9 built the contract,
validation and routing; Days 10-12 added working executors for JavaScript, Python and Java.

### 4.1 Request flow (JavaScript, Python and Java are live)

```text
Browser (React workspace, not yet connected)
        |
        | POST /api/execute  { language, code, input }
        v
ExecutionController            (HTTP validation, status codes)
        |
        v
ExecutionService               (Language.fromId, executor routing)
        |
        v
CodeExecutor (interface)
        |
        +--> JavaScriptExecutor (registered) --> GraalVmSandbox --> GraalJS
        |                                        fresh Context, 5 s timeout
        +--> PythonExecutor     (registered) --> GraalVmSandbox --> GraalPy
        +--> JavaExecutor       (registered) --> JavaExecutionSandbox
        |                                        JavaCompiler + child JVM, 128 MB heap
        |
        +--> no other executor -----------------> 501 Not Implemented

Planned implementations behind the same interface:
        +--> TruffleRubyExecutor   in-process (Polyglot Context, ruby)
        +--> CExecutor             out-of-process (gcc/clang)
        +--> CppExecutor           out-of-process (g++/clang++)
        +--> RustExecutor          out-of-process (rustc)
        +--> GoExecutor            out-of-process (go build)
        +--> RExecutor             out-of-process (Rscript)
```

### 4.2 The adapter contract (implemented)

Every executor implements the same interface, so the service layer is language-agnostic:

```java
public interface CodeExecutor {
    Language language();                        // enum constant, unique per executor
    boolean isAvailable();                      // is the runtime/toolchain usable here?
    ExecutionResult execute(ExecutionRequest request);
}
```

Executors are discovered by Spring and indexed by language; a second executor for the same
language fails application startup rather than silently winning. Request body:

| Field | Rule |
|---|---|
| `language` | Required, at most 32 characters, one of the nine ids, case-insensitive after trim. |
| `code` | Required, non-blank, at most 100,000 characters. |
| `input` | Optional, at most 10,000 characters; null is treated as empty. |

Response body (null fields are serialised so the shape stays stable):

| Field | Purpose |
|---|---|
| `success` | Whether the program ran and exited successfully. |
| `language` | Echo of the resolved language id. |
| `output` | Captured stdout/stderr; produced for JavaScript, Python and Java since Day 12. |
| `error` | Failure message, or `null` on success. |
| `executionTimeMs` | Execution duration reported by the executor. |

Current status codes: `200` for a JavaScript, Python or Java request, including programs that fail
with a syntax, compile or runtime error (`success` is then false); `400` for an unknown language,
blank field, size violation or malformed JSON; `501` when a known language has no available
executor; `500` for an unexpected server failure with a generic body that leaks no stack trace. A
missing engine produces an honest `501`, never fabricated output.

### 4.3 Adapter families

**In-process adapters (JavaScript and Python today; Ruby planned)**

- **Implemented for JavaScript and Python:** a shared, lazily created GraalVM `Engine`; a fresh
  `Context` per request; `HostAccess.NONE`; host class lookup and loading disabled;
  `IOAccess.NONE`; environment, thread, process and native access all disabled; stdout/stderr
  captured in memory.
- A watchdog submits evaluation to a daemon worker and cancels via `Context.close(true)` when the
  wall-clock deadline expires.
- **Still advisory:** the JVM heap is shared with the backend, so there is no hard memory cap.
  Hard isolation requires a separate worker JVM, process or container.
- Ruby would reuse this sandbox once its language artifact can be verified.

**Child-process adapters (Java today; C, C++, Rust, Go, R planned)**

- **Implemented for Java:** compile in-process with `javax.tools.JavaCompiler` into a temporary
  classes directory, then launch the program in a child JVM with `-Xmx128m`, a cleared environment
  and a working directory inside a unique temporary workspace. The parent kills the child on
  timeout and deletes the workspace in a `finally` block.
- **Required for native adapters:** create a unique temporary workspace per request, write the
  source with a fixed generated file name (never a user-supplied path), compile via a direct
  `ProcessBuilder` call with an argument vector and no shell, run with a minimal environment and a
  resource watchdog, capture bounded stdout/stderr, then delete the workspace in a `finally` block.

### 4.4 Shared execution policy layer

**Implemented:** source and input size caps at the API boundary, language allow-listing, one JSON
error shape, fail-closed routing, a 5-second execution timeout and output/error caps. These limits
are configured in `application.properties` and enforced for JavaScript, Python and Java.

**Not implemented:** hard memory limits for the in-process engines and for Java compilation, CPU
quotas and concurrency limits. These need a worker JVM, cgroup or container boundary, not just an
in-process setting. Timeout enforcement is also per-executor today rather than centralised, so it
must be re-checked when each new adapter lands. Error mapping already avoids leaking host paths and
stack traces.

### 4.5 Current state

Days 10-12 implement the full path for JavaScript, Python and Java: the three executors ->
`GraalVmSandbox` -> `GraalVmEngine` -> GraalJS/GraalPy for the embedded engines, and
`JavaExecutionSandbox` -> JDK `JavaCompiler` + child JVM for Java, with a timeout and output caps.
The API returns real program output for `javascript`, `python` and `java`, and `501` for the other
six languages. Ruby and the native toolchains remain adapters to be written, and the isolation
controls in section 6 are only partially implemented.

---

## 5. Runtime Requirements

The backend host must provide the toolchains below. The embedded group needs Maven dependencies and
a GraalVM-based JDK; Java needs the JDK's compiler and launcher; the native group needs real
compilers installed on the host or inside the execution image.

### 5.1 Requirements per language

| Language | Required component | Minimum expectation | Where it runs | Status |
|---|---|---|---|---|
| Java | JDK 21 compiler (`javax.tools.JavaCompiler`) and `java` launcher | JDK 21+; project baseline is 21.0.12 | Backend process (compile) + child JVM (run) | **Bundled with the JDK and tested** |
| Python | GraalPy `python-community` artifact | 23.1.12 (declared in `pom.xml`) | Backend JVM | **Bundled and tested** |
| JavaScript | GraalJS `js-community` artifact | 23.1.12 (declared in `pom.xml`) | Backend JVM | **Bundled and tested** |
| Ruby | TruffleRuby artifact (or CRuby, if the design changes) | Version decision still open | Backend JVM (or child process) | Planned |
| C | `gcc` or `clang` | C11 or later | Child process | Planned |
| C++ | `g++` or `clang++` | C++17 or later | Child process | Planned |
| Rust | `rustc` | Current stable, edition 2021 | Child process | Planned |
| Go | `go` | Go 1.21 or later | Child process | Planned |
| R | `Rscript` | R 4.x | Child process | Planned |

### 5.2 Environment notes from the repository

`docs/DEVELOPMENT_SETUP.md` records the verified development machine and is the authority for
local versions. Relevant points:

- Oracle GraalVM 21.0.12+7.1 is installed but **not on `PATH`**, and `GRAALVM_HOME` is unset.
- The default `java` on `PATH` is JDK 17, not JDK 21.
- The installed GraalVM is a **JDK-level** install. It does not itself bundle the JavaScript,
  Python or Ruby language runtimes; GraalJS and GraalPy arrive as Maven dependencies, Java uses
  the JDK's own compiler and launcher, and TruffleRuby is still absent.
- Only Java, Maven, Node.js, npm and Git were verified. **No C, C++, Rust, Go or R toolchain was
  verified**, so the native-language group has no environment evidence at all.
- The Day 12 suite ran on Java 21.0.12 and passed (83 tests), which verifies the GraalJS, GraalPy
  and JDK Java paths on this machine but does not replace a deployment-image check.

Consequences:

1. Runtime availability must be detected at startup (`isAvailable()`), not assumed.
2. The application must not depend on the ambient `PATH`; compiler locations should be
   configurable and validated.
3. The container or host image must pin toolchain versions; "latest" is not reproducible.

### 5.3 Dependency and licensing notes

- The Polyglot API and language artifacts are Maven dependencies of the backend, not OS packages.
- Native compilers are OS-level dependencies and belong in deployment documentation and the
  execution image, not in `pom.xml`.
- Every added runtime increases image size and attack surface. Runtimes should be installed only
  when their adapter is actually enabled.

---

## 6. Security Considerations

Running untrusted code is the highest-risk feature in PolyglotMesh. The controls below are
**partially implemented for JavaScript, Python and Java** and remain requirements for the other six
languages. Java has process isolation; the in-process sandbox is defence in depth, not a hardened
isolation boundary.

### 6.1 Execution timeout

- **Implemented for JavaScript, Python and Java:** a 5-second wall-clock deadline per request
  (configurable via `polyglotmesh.graalvm.execution-timeout`). In-process evaluation runs on a
  daemon worker and is cancelled by closing the `Context`; the Java adapter force-kills the child
  JVM. A tight loop is interrupted this way.
- **Still required:** the deadline for native languages must also cover compilation, and a hard
  server maximum should clamp any client-supplied value. Timeout enforcement currently lives in
  the executor rather than a central policy layer.

### 6.2 Memory restrictions

- Native child processes should be started with an address-space or cgroup limit; exceeding it
  must yield `MEMORY_EXCEEDED`, not a host OOM kill.
- **Implemented for Java:** the child JVM is started with `-Xmx128m`, so a runaway allocation is
  bounded by that heap cap and surfaces as a child-JVM error or non-zero exit. Compilation still
  runs in the backend JVM and is not separately capped.
- **Not implemented for the in-process engines:** there is no hard per-request memory cap. GraalJS
  and GraalPy run inside the backend JVM and share its heap, so a memory cap inside that JVM is
  advisory. Real isolation requires a worker JVM or process with `-Xmx` set, or a container limit.
- **Implemented:** stdout/stderr are capped at 100,000 characters and error text at 20,000, which
  bounds the most common memory-exhaustion path (printing in a loop).
- **Still required:** compiler memory counts for C++ and Rust, so compile and run phases need
  separate limits once native adapters exist.

### 6.3 Filesystem restrictions

- **Implemented for JavaScript and Python:** `IOAccess.NONE` denies guest file IO, so a program
  cannot open host files through the context.
- **Not restricted for Java:** the child runs in a private temporary directory, but guest Java code
  can read and write host files as the backend user. There is no filesystem namespace or chroot.
- **Still required for native languages:** each run needs a fresh temporary directory, generated
  file names (never user-supplied paths), and a workspace outside the application tree.
- The backend, its configuration and its credentials must not be reachable from any execution
  directory.
- Symlink traversal must be considered when cleaning up or when passing paths to a compiler.

### 6.4 Process isolation

- **JavaScript and Python are not process-isolated.** They run inside the backend JVM; the context
  denies host access, IO, threads, processes, native access and environment access, but a
  JVM-level escape is not contained by this design.
- **Java is process-isolated, but not securely sandboxed.** The child JVM contains `System.exit`
  and JVM crashes, has a 128 MB heap, a cleared environment and a private working directory, and is
  killed on timeout. It still runs as the backend user with filesystem and network access.
- **Native languages must run in a separate process**, ideally under a dedicated low-privilege OS
  user, without inheriting the backend's environment, file descriptors or working directory.
- Arguments must be passed as an argument vector; user input is never interpolated into a shell
  command, and a process-group kill is required so children do not survive a timeout.
- Container-level isolation (separate namespace, read-only root filesystem, dropped capabilities)
  is preferred over OS flags alone. Process isolation is **not** a security boundary unless it is
  configured as one.

### 6.5 Network restrictions

- Guest programs must have no outbound network access by default.
- **Partially covered for JavaScript and Python:** the context exposes no host IO or process
  access, so guest code has no supported route to a socket. This is not a network sandbox and must
  not be relied on as one.
- **Not covered for Java:** the child JVM can open sockets as the backend user; there is no
  network namespace or egress filter.
- **Still required:** enforcement at the container or network-namespace level for every runtime,
  including native child processes. Package managers (`pip`, `gem`, `cargo`, `go get`) must be
  unreachable at run time.
- The backend's own network access is separate and should not be exposed to guest code through
  loopback.

### 6.6 Temporary file cleanup

- **Implemented for Java:** each run gets a unique temporary directory holding the source, compiled
  classes and the child's `java.io.tmpdir`; it is deleted in a `finally` block after success,
  compile failure, timeout or crash.
- **Not applicable to JavaScript or Python:** both execute in memory and create no temporary files.
- **Required for native adapters:** delete each workspace in a `finally` block after every run,
  including compile failures, timeouts and crashes.
- Add a startup sweep for orphaned workspaces older than a fixed age, covering kills that skip
  normal cleanup; log and alert on cleanup failures.
- Compiled artefacts must not be reused across users. Any cache must be keyed by content and
  scoped to one language and toolchain version.

### 6.7 Additional controls to design in from the start

- **Request size limits:** implemented at the API boundary (100,000-character source,
  10,000-character input, 32-character language id).
- **Fail closed:** implemented; an unknown language, missing engine or disabled runtime returns an
  error and never falls back to another adapter.
- **Concurrency limits** per user and globally: not implemented, and needed to prevent resource
  exhaustion through volume.
- **No secrets in the execution image**, and no backend credentials in the child process
  environment: required before native execution ships.
- **Audit logging** of language, duration, outcome and limit violations, without logging full
  source by default: not implemented.

---

## 7. Future Improvements

### 7.1 Planned runtime integrations

The realistic sequence follows the project roadmap and the existing metadata:

| Phase | Scope | Status |
|---|---|---|
| 1 | Unified execution API, adapter interface, routing | Done (Day 9) |
| 2 | JavaScript (GraalJS), sandbox, timeout, output caps | **Done and tested (Day 10)** |
| 3 | Python (GraalPy) using the same sandbox | **Done and tested (Day 11)** |
| 4 | Java (JDK compiler + child JVM) | **Done and tested (Day 12)** |
| 5 | Ruby (TruffleRuby or CRuby) | Blocked on this Windows host; engine choice still open |
| 6 | C and C++ (GCC/Clang, G++/Clang++) | Planned; establishes the native sandbox pattern |
| 7 | Rust, Go, R | Planned; reuse the native pattern |

Sequencing rule: a language is only exposed in the UI after its adapter passes the compatibility
suite. Editor mode availability does not imply execution availability, and the UI must keep
distinguishing the two.

### 7.2 Compatibility testing

A compatibility suite should verify behaviour, not just "it ran":

- **Golden programs per language**: a hello-world, stdin echo, arithmetic, a deliberate compile
  error and a deliberate runtime error, each with expected stdout/stderr/status.
- **Limit tests**: infinite loop (timeout), large allocation (memory), large output (truncation),
  filesystem access attempt (denied), network access attempt (denied).
- **Toolchain matrix**: at least one pinned version per compiler/interpreter, recorded in CI.
- **Startup availability tests**: each adapter reports `isAvailable()` correctly when its toolchain
  is present and when it is absent.
- **Regression tests on the API contract**: identical request shapes across languages, stable error
  codes, no host paths leaked in responses.
- **Cross-language smoke test**: the same trivial algorithm implemented in all nine languages,
  asserting equivalent output.

JavaScript, Python and Java are marked **Tested** because their suites cover successful execution,
stdin, syntax/compile and runtime errors, timeout cancellation, output truncation, host/file access
denial or process containment, plus end-to-end `/api/execute` tests. The other six remain untested;
the cross-language suite and golden programs above are still future work.

### 7.3 Documentation follow-ups

- Update this matrix whenever an adapter is added, with the commit and test evidence.
- Publish exact supported versions and the deployment image contents.
- Document known language limitations (no third-party packages for Python/Ruby/Rust/Go, no
  threads or subprocesses in guest code) in the user-facing docs.
- Replace the "Planned" statuses with dated evidence as each phase lands.

---

## Appendix A - Verification Notes

This document was written against the repository, not against the project plan alone. The
following evidence was inspected at commit `cca6061` on branch `leelavardhini-dev`:

| Evidence | Location | Finding |
|---|---|---|
| Language catalogue | `frontend/src/data/languages.ts` | Nine entries; `runtime` is non-null only for Java, Python and JavaScript; `null` for the other six. |
| Type contract | `frontend/src/types/language.ts` | `runtime` explicitly documented as "not proof that execution works". |
| Frontend status | `frontend/README.md` | Java/Python/JavaScript backends marked "planned"; six languages "Not integrated - editor only"; Run/Save/Share disabled. |
| Project status | `README.md` | Still says execution is not implemented; predates Day 10, so that line is stale for JavaScript, Python and Java. |
| Environment | `docs/DEVELOPMENT_SETUP.md` | GraalVM JDK present, no C/C++/Rust/Go/R toolchain verified. |
| Backend scope | `backend/README.md` | Day 12: JavaScript, Python and Java execute; six languages return `501`; Ruby blocked on Windows. |
| Runtime dependencies | `backend/pom.xml` | `polyglot`, `js-community` and `python-community` 23.1.12 declared; no TruffleRuby artifact; Java uses the JDK. |
| Executors | `backend/src/main/java/.../executor/` | `JavaScriptExecutor`, `PythonExecutor` and `JavaExecutor` registered; availability checked at runtime. |
| In-process sandbox | `backend/src/main/java/.../runtime/GraalVmSandbox.java` | Fresh context, host/IO/thread/process/native/environment access denied, timeout, output caps, per-language stdin bridge. |
| Java sandbox | `backend/src/main/java/.../runtime/JavaExecutionSandbox.java` | JDK compile, child JVM with 128 MB heap, cleared environment, temp workspace, timeout kill, `finally` cleanup. |
| Tests | `backend/src/test/java/.../execution/` and `.../runtime/` | Real GraalJS, GraalPy and Java execution through `/api/execute`; `mvn test` passed 83 tests, 0 failures. |

## Appendix B - What this document does not claim

- It does not claim that any language other than JavaScript, Python and Java can be executed today.
- It does not claim that CPU or memory quotas, process isolation for the in-process engines,
  container isolation, network namespaces or concurrency limits exist.
- It does not claim that Java's child process is a security sandbox; guest Java code retains
  filesystem and network access as the backend user.
- It does not claim that the native-language architecture in section 4 is implemented.
- It does not claim that TruffleRuby, GCC, Clang, `rustc`, Go or R is installed or verified on the
  development machine.

Editor support for all nine languages is real and verified in the frontend. Backend execution is
real for JavaScript, Python and Java; the other six languages are future work.
