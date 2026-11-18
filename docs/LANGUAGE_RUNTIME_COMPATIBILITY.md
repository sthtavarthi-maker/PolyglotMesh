# Language Runtime Compatibility Matrix - PolyglotMesh

Status of this document: **reference documentation**. It describes the intended language runtime
architecture of PolyglotMesh and records, language by language, what the repository actually
implements today.

- **Repository:** `https://github.com/sthtavarthi-maker/PolyglotMesh`
- **Documented branch:** `leelavardhini-dev`
- **Baseline commit:** `3067ae0` ("Expand editor to nine languages")
- **Last reviewed:** 18 November 2026
- **Scope:** language runtimes, execution adapters, host toolchain requirements and sandbox
  controls. No application code is changed by this document.

> **Read this first.** PolyglotMesh currently has **no working code execution**. Nine languages
> are available as editor modes only. Every runtime integration described below is either
> *planned* or, at most, *configured as metadata*. Nothing is implemented, and nothing has been
> tested end to end. Sections that describe architecture or security controls are **design
> intent**, not descriptions of shipped behaviour.

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

- **JVM-embedded languages** (Java, Python, JavaScript, and later Ruby) run inside the backend
  JVM through the GraalVM Polyglot API or the JDK compiler API. No external process is needed.
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

The repository currently reaches **Configured** for all nine editor modes and **Planned** for all
nine execution runtimes. **No language is Implemented or Tested.**

---

## 2. Language Runtime Compatibility Matrix

Execution Method and Integration Type describe the **intended** design. Implementation Status
describes the **current** state of the repository at the baseline commit.

| Programming Language | Runtime / Compiler | Execution Method | Integration Type | Implementation Status |
|---|---|---|---|---|
| Java | GraalVM JDK 21 / `javax.tools.JavaCompiler` | In-process compile, then load and invoke in an isolated class loader | In-process (JVM) | Editor **Configured**; runtime **Planned** - no compiler adapter or execution endpoint exists |
| Python | GraalPy (GraalVM Python) | In-process `Context.eval("python", source)` | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Planned** - GraalPy is named in docs, no dependency wired |
| JavaScript | GraalJS (GraalVM JavaScript) | In-process `Context.eval("js", source)` | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Planned** - GraalJS is named in docs, no dependency wired |
| Ruby | TruffleRuby | In-process `Context.eval("ruby", source)` | In-process (Truffle / Polyglot API) | Editor **Configured**; runtime **Planned** - TruffleRuby is not referenced anywhere in the repository |
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

For Ruby, C, C++, Rust, Go and R, the runtime label is `null`, so even the *intended* engine is
not recorded in the application. The engines in the matrix above come from the project plan
(README, roadmap) and this design document, not from code.

### 2.2 What is absent

At the baseline commit, the repository contains:

- no GraalVM Polyglot dependency (`org.graalvm.polyglot`) in any build file;
- no execution endpoint (no `/api/execute`);
- no execution service, adapter, registry or dispatcher;
- no compiler or interpreter invocation code;
- no execution-related automated test.

The `backend/` directory on this branch is a placeholder tracked only by `backend/.gitkeep`. The
backend skeleton on `main` (Day 8) exposes a health endpoint only and its own README states:
"No code execution of any kind. `/api/execute` arrives on Day 9-14."

---

## 3. Runtime Technologies

This section explains the engine chosen for each language and the constraints that come with it.
None of these engines is bundled with the current installation.

### 3.1 Java - JavaCompiler API on GraalVM JDK 21

Java is compiled and executed in-process with `javax.tools.JavaCompiler`, part of the JDK, and
then loaded through a dedicated `ClassLoader`.

- **Why it fits:** no subprocess, no fork per run, and compilation diagnostics can be returned as
  structured errors rather than raw stderr.
- **Runtime basis:** the project targets Java 21 on GraalVM. `docs/DEVELOPMENT_SETUP.md` records
  Oracle GraalVM 21.0.12+7.1 as verified on the development machine.
- **Caveat:** in-process execution shares the backend JVM. Untrusted code must be constrained by
  a hardened class loader, a restricted thread, and a compiled-code cache policy, or the process
  must be moved out of process. This is a design problem that is **not yet solved** in the
  repository.

### 3.2 Python - GraalPy

GraalPy is the GraalVM Python runtime and is intended to run inside the backend JVM through the
Polyglot API (`Context.eval("python", ...)`).

- **Why it fits:** keeps Python in the same process as the Java service and avoids a second
  runtime deployment.
- **Repository evidence:** `frontend/src/data/languages.ts` labels Python `GraalVM Polyglot`, and
  `frontend/README.md` marks the backend as "GraalVM Polyglot / GraalPy (planned)".
- **Caveat:** the verified GraalVM install is a JDK-level install without language runtimes on
  `PATH`; `docs/DEVELOPMENT_SETUP.md` explains that language artifacts are added per project as
  Maven dependencies. That wiring does not exist yet.

### 3.3 JavaScript - GraalJS

GraalJS is the GraalVM JavaScript engine, also embedded through the Polyglot API
(`Context.eval("js", ...)`).

- **Why it fits:** ECMAScript execution with no Node.js process, no npm install and no browser
  dependency on the server.
- **Repository evidence:** labelled `GraalVM Polyglot` in the language metadata and "GraalJS
  (planned)" in the frontend README.
- **Caveat:** GraalJS is not guaranteed to be present in a stock GraalVM distribution; it must be
  added as an explicit dependency, and host access must be disabled.

### 3.4 Ruby - TruffleRuby

TruffleRuby is the GraalVM Ruby implementation and would be embedded the same way
(`Context.eval("ruby", ...)`).

- **Status:** the least-defined integration. TruffleRuby is **not mentioned** in the repository's
  code, README or setup documentation; Ruby is currently editor-only with a `null` runtime label.
- **Caveat:** embedding TruffleRuby from Maven is more involved than GraalJS or GraalPy, and
  gem availability is limited compared with CRuby. Choosing between embedded TruffleRuby and an
  out-of-process CRuby is an open decision.

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
is that adding a language never changes the HTTP layer or the frontend.

### 4.1 Request flow (design)

```text
Browser (React workspace)
        |
        | POST /api/execute  { language, source, stdin, limits }
        v
ExecutionController            (HTTP validation, request identity)
        |
        v
ExecutionService               (orchestration, timeouts, result assembly)
        |
        v
LanguageAdapterRegistry        (language id -> adapter, fail closed on unknown ids)
        |
        +--> JavaExecutionAdapter        in-process (JavaCompiler + ClassLoader)
        +--> GraalPyExecutionAdapter     in-process (Polyglot Context, python)
        +--> GraalJSExecutionAdapter     in-process (Polyglot Context, js)
        +--> TruffleRubyExecutionAdapter in-process (Polyglot Context, ruby)
        +--> CExecutionAdapter           out-of-process (gcc/clang)
        +--> CppExecutionAdapter         out-of-process (g++/clang++)
        +--> RustExecutionAdapter        out-of-process (rustc)
        +--> GoExecutionAdapter          out-of-process (go build)
        +--> RExecutionAdapter           out-of-process (Rscript)
```

### 4.2 The adapter contract

Every adapter implements the same small interface, so the service layer is language-agnostic:

```java
public interface LanguageExecutionAdapter {
    String languageId();                       // "java", "python", "c", ...
    boolean isAvailable();                     // toolchain/dependency present?
    ExecutionResult execute(ExecutionRequest request);
}
```

Shared request fields (proposed):

| Field | Purpose |
|---|---|
| `language` | Adapter selection key; unknown values are rejected, never defaulted. |
| `source` | Program text, size-limited before it reaches any adapter. |
| `stdin` | Optional program input, size-limited. |
| `timeoutMillis` | Requested limit, clamped to a server-side maximum. |
| `memoryLimitMb` | Requested limit, clamped to a server-side maximum. |

Shared result fields (proposed):

| Field | Purpose |
|---|---|
| `status` | `SUCCESS`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIMEOUT`, `MEMORY_EXCEEDED`, `UNAVAILABLE`. |
| `stdout` / `stderr` | Captured output, truncated to a fixed byte cap. |
| `exitCode` | Process exit code, or a synthetic code for in-process failures. |
| `durationMillis` | Wall-clock execution time. |
| `diagnostics` | Optional structured compiler messages. |

`UNAVAILABLE` is the important status: when a compiler or language artifact is missing, the API
reports the runtime as unavailable instead of failing obscurely.

### 4.3 Two adapter families

**In-process adapters (Java, Python, JavaScript, Ruby)**

- Use the GraalVM Polyglot API with `allowAllAccess(false)`, host access disabled and IO disabled.
- Each run gets a fresh `Context` (or a fresh class loader for Java), never a shared mutable one.
- Cancellation uses the Polyglot `Context` close mechanism plus a watchdog timeout.
- The JVM's own heap and the GraalVM isolate are shared with the backend, so memory limits are
  advisory at best. Hard isolation requires a separate worker JVM or process.

**Out-of-process adapters (C, C++, Rust, Go, R)**

- Create a unique temporary workspace per request.
- Write the source with a fixed, generated file name (never a user-supplied path).
- Compile via a direct `ProcessBuilder` call with an argument vector, no shell.
- Run with a minimal environment, a working directory inside the temp workspace, and a resource
  watchdog.
- Capture bounded stdout/stderr, then delete the workspace in a `finally` block.

### 4.4 Shared execution policy layer

Timeouts, output caps, source-size caps and concurrency limits belong in `ExecutionService`, not
in individual adapters, so they cannot be forgotten when a new adapter is added. The policy layer
also owns the mapping from internal failures to the `status` enum, keeping error messages free of
host paths and stack traces.

### 4.5 Current state

None of the classes in this section exist at the baseline commit. This branch has no backend
source at all (`backend/` holds only `.gitkeep`); on `main`, the backend is a Spring Boot skeleton
whose only application endpoint is a health check. Neither branch has an `ExecutionController`,
`ExecutionService`, `LanguageAdapterRegistry` or adapter implementation. Section 4 is therefore a
**design proposal** for the next development phase, not a description of current code.

---

## 5. Runtime Requirements

The backend host must provide the toolchains below. The JVM-embedded group needs Maven
dependencies and a GraalVM-based JDK; the native group needs real compilers installed on the host
or inside the execution image.

### 5.1 Requirements per language

| Language | Required component | Minimum expectation | Where it runs |
|---|---|---|---|
| Java | GraalVM for JDK 21 (includes `javac`/JavaCompiler) | JDK 21+; project baseline is 21.0.12 | Backend JVM |
| Python | GraalPy language artifact | Version matching the GraalVM release line | Backend JVM |
| JavaScript | GraalJS language artifact | Version matching the GraalVM release line | Backend JVM |
| Ruby | TruffleRuby artifact (or CRuby, if the design changes) | Version decision still open | Backend JVM (or child process) |
| C | `gcc` or `clang` | C11 or later | Child process |
| C++ | `g++` or `clang++` | C++17 or later | Child process |
| Rust | `rustc` | Current stable, edition 2021 | Child process |
| Go | `go` | Go 1.21 or later | Child process |
| R | `Rscript` | R 4.x | Child process |

### 5.2 Environment notes from the repository

`docs/DEVELOPMENT_SETUP.md` records the verified development machine and is the authority for
local versions. Relevant points:

- Oracle GraalVM 21.0.12+7.1 is installed but **not on `PATH`**, and `GRAALVM_HOME` is unset.
- The default `java` on `PATH` is JDK 17, not JDK 21.
- The installed GraalVM is a **JDK-level** install. It does not bundle the JavaScript, Python or
  Ruby language runtimes; those are expected to arrive as project dependencies.
- Only Java, Maven, Node.js, npm and Git were verified. **No C, C++, Rust, Go or R toolchain was
  verified**, so the native-language group has no environment evidence at all.

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
**requirements for any future implementation**. At the baseline commit none of them is
implemented, because no execution path exists.

### 6.1 Execution timeout

- Every request gets a wall-clock deadline enforced by the service layer, not by the submitted
  program.
- The deadline covers **compile and run** for native languages; a compile step that never finishes
  is as harmful as a runaway program.
- In-process runs must be cancellable; an interrupt alone is insufficient for tight loops, so a
  watchdog must close the `Context` or terminate the worker.
- Suggested starting values: 1-2 s compile allowance for C/C++, 5 s run allowance, hard server
  maximum of 10 s. Values are policy, not constants in adapter code.

### 6.2 Memory restrictions

- Native child processes should be started with an address-space or cgroup limit; exceeding it
  must yield `MEMORY_EXCEEDED`, not a host OOM kill.
- JVM-embedded execution is the weak point: GraalVM contexts share the backend heap. A memory cap
  inside one JVM is advisory. Real isolation requires a dedicated worker JVM or process with
  `-Xmx` set, or a container-level limit.
- Compiler memory counts. C++ and Rust compilation can consume far more memory than the resulting
  program, so compile and run phases need separate limits.
- Output must be capped independently (for example 64 KiB per stream) to prevent memory
  exhaustion through printing.

### 6.3 Filesystem restrictions

- Each run gets a fresh temporary directory and may read/write only inside it.
- Adapters must never join user-supplied paths; file names are generated by the server.
- In-process languages must run with IO access disabled in the Polyglot context, so the guest
  cannot open host files.
- The backend, its configuration and its credentials must not be reachable from the execution
  directory. Temporary workspaces must live outside the application tree.
- Symlink traversal must be considered when cleaning up or when passing paths to a compiler.

### 6.4 Process isolation

- Native languages run in a separate process, ideally under a dedicated low-privilege OS user.
- The process must not inherit the backend's environment variables, file descriptors or working
  directory.
- Argument vectors are used directly; user input is never interpolated into a shell command.
- A process-group kill is required so that compiler or program child processes do not survive a
  timeout.
- Container-level isolation (separate namespace, read-only root filesystem, dropped capabilities)
  is preferred over relying on OS flags alone. Process isolation is **not** equivalent to a
  security boundary unless it is configured as one.

### 6.5 Network restrictions

- Executed programs must have no outbound network access by default.
- This must be enforced at the sandbox/container level. Language-level bans (for example removing
  a socket API) are bypassable and are not a control.
- Package managers (`pip`, `gem`, `cargo`, `go get`) must not be reachable at run time; that also
  protects reproducibility.
- The backend's own network access is separate and should not be exposed to guest code through
  loopback.

### 6.6 Temporary file cleanup

- Workspaces are deleted in a `finally` block after every run, including compile failures,
  timeouts and crashes.
- A startup sweep removes orphaned workspaces older than a fixed age, covering process kills that
  skip normal cleanup.
- Cleanup failures are logged and alerted; silent accumulation is a disk-exhaustion path.
- Compiled artefacts are not reused across users. Any cache must be keyed by content and scoped to
  a single language and toolchain version.

### 6.7 Additional controls to design in from the start

- **Concurrency limits** per user and globally, to prevent resource exhaustion through volume.
- **Request size limits** on source and stdin, enforced before parsing.
- **No secrets in the execution image**, and no backend credentials on the child process
  environment.
- **Audit logging** of language, duration, exit status and limit violations, without logging full
  source by default.
- **Fail closed**: an unknown language, missing toolchain or disabled runtime returns an error; it
  never falls back to another adapter.

---

## 7. Future Improvements

### 7.1 Planned runtime integrations

The realistic sequence follows the project roadmap and the existing metadata:

| Phase | Scope | Rationale |
|---|---|---|
| 1 | Unified execution API, adapter interface, policy layer | Everything else depends on the contract. |
| 2 | JavaScript (GraalJS), then Python (GraalPy) | Lowest integration cost; artifacts are Maven dependencies; already named in project docs. |
| 3 | Java (JavaCompiler + isolated class loader) | In-process, but needs a class-loader and isolation design. |
| 4 | C and C++ (GCC/Clang, G++/Clang++) | First out-of-process adapters; establishes the native sandbox pattern. |
| 5 | Rust, Go, R | Reuse the native pattern with per-toolchain specifics. |
| 6 | Ruby (TruffleRuby or CRuby) | Engine choice still open; deliberately last. |

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

Until this suite exists and passes, no language may be marked **Tested** in this document.

### 7.3 Documentation follow-ups

- Update this matrix whenever an adapter is added, with the commit and test evidence.
- Publish exact supported versions and the deployment image contents.
- Document known language limitations (no third-party packages for Python/Ruby/Rust/Go, no
  threads or subprocesses in guest code) in the user-facing docs.
- Replace the "Planned" statuses with dated evidence as each phase lands.

---

## Appendix A - Verification Notes

This document was written against the repository, not against the project plan alone. The
following evidence was inspected at commit `3067ae0` on branch `leelavardhini-dev`:

| Evidence | Location | Finding |
|---|---|---|
| Language catalogue | `frontend/src/data/languages.ts` | Nine entries; `runtime` is non-null only for Java, Python and JavaScript; `null` for the other six. |
| Type contract | `frontend/src/types/language.ts` | `runtime` explicitly documented as "not proof that execution works". |
| Frontend status | `frontend/README.md` | Java/Python/JavaScript backends marked "planned"; six languages "Not integrated - editor only"; Run/Save/Share disabled. |
| Project status | `README.md` | "Code execution is not implemented yet"; only Java, Python and JavaScript are execution targets. |
| Environment | `docs/DEVELOPMENT_SETUP.md` | GraalVM JDK present, language runtimes absent, no C/C++/Rust/Go/R toolchain verified. |
| Backend scope | `backend/README.md` (on `main`) | Health endpoint only; "No code execution of any kind"; polyglot dependency not wired. |
| Source search | frontend and backend sources | No adapter, registry, execution service, `/api/execute` or polyglot dependency. |

## Appendix B - What this document does not claim

- It does not claim that any of the nine languages can be executed today.
- It does not claim that the security controls in section 6 exist.
- It does not claim that the architecture in section 4 is implemented.
- It does not claim that TruffleRuby, GCC, Clang, `rustc`, Go or R is installed or verified on the
  development machine.

Editor support for all nine languages is real and verified in the frontend. Execution support for
all nine languages is future work.
