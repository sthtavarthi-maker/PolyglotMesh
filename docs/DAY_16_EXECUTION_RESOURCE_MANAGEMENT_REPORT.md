# PolyglotMesh – Day 16 Work Report

## Execution Resource Management and Timeouts

**Date:** 26 November 2026  
**Branch:** main  
**Commit:** `abff955` (`abff955ba07a68ada15325bc3c7a6addd344930d`)

### Work Completed

Day 16 improved how the backend manages execution resources. The main change is
that work which used to share one timeout is now split into separate, measurable
budgets: GraalVM context setup, GraalVM guest execution, Java compilation, and
Java child-JVM execution. The sandbox worker pool is also bounded and tracked so
that executions cannot create unbounded threads or leave contexts behind.

### Timeout and Resource Model

| Budget | Property | Default | Measured on the development host |
|---|---|---|---|
| GraalVM context setup plus stdin installation | `polyglotmesh.graalvm.setup-timeout` | `60s` | 5.8 s first JavaScript context; 21.6 s first Python context |
| GraalVM guest execution | `polyglotmesh.graalvm.execution-timeout` | `5s` | ~32 ms warm JavaScript; ~1.0 s warm Python |
| Java compilation | `polyglotmesh.execution.compile-timeout` | `20s` | ~3 s for a trivial program; 8–13 s under load in Week 2 |
| Java child-JVM execution | `polyglotmesh.execution.java-execution-timeout` | `10s` | ~0.8 s warm |
| Graceful shutdown phase | `spring.lifecycle.timeout-per-shutdown-phase` | `45s` | Covers a Java compile plus child run |

The setup budget is deliberately separate from guest execution. Creating the
first GraalPy context on a cold JVM takes far longer than running the program,
so the program is not charged for that one-time startup cost. Guest code still
receives only the configured execution budget.

The Java compile budget is also separate from the Java run budget. The larger
compile allowance never becomes the program's run time.

### Key Changes

- Added `setupTimeout` to `GraalVmProperties`, with validation and a safe default.
- Added `javaExecutionTimeout` to `ExecutionProperties`, separate from
  `compileTimeout`.
- `GraalVmSandbox` now creates the context and installs stdin on a worker thread
  under the setup budget, then evaluates guest code under the execution budget.
- Replaced the unbounded cached sandbox thread pool with a fixed pool sized by
  `polyglotmesh.execution.max-concurrent-executions`.
- Added an ownership handoff so exactly one side closes a context when setup
  times out.
- Tracked active GraalVM contexts and closed them during `@PreDestroy` shutdown.
- Executions submitted after sandbox shutdown fail fast with
  `The execution service is shutting down.`
- `JavaExecutionSandbox` now uses the Java run budget for the child JVM instead
  of reusing the GraalVM execution timeout.
- Raised the graceful shutdown phase from `30s` to `45s` so a Java compile plus
  child run can finish normally.
- Updated `application.properties` with the new budgets and explanatory
  comments.
- Added and updated tests for defaults, independent budgets, runaway programs
  and post-shutdown rejection.

### Configuration Added or Changed

| Property | Value |
|---|---|
| `polyglotmesh.graalvm.setup-timeout` | `60s` |
| `polyglotmesh.graalvm.execution-timeout` | `5s` |
| `polyglotmesh.execution.compile-timeout` | `20s` |
| `polyglotmesh.execution.java-execution-timeout` | `10s` |
| `spring.lifecycle.timeout-per-shutdown-phase` | `45s` |

### Files Updated

1. `README.md`
2. `backend/README.md`
3. `backend/src/main/java/com/polyglotmesh/config/ExecutionProperties.java`
4. `backend/src/main/java/com/polyglotmesh/config/GraalVmProperties.java`
5. `backend/src/main/java/com/polyglotmesh/runtime/GraalVmSandbox.java`
6. `backend/src/main/java/com/polyglotmesh/runtime/JavaExecutionSandbox.java`
7. `backend/src/main/resources/application.properties`
8. `backend/src/test/java/com/polyglotmesh/config/ExecutionPropertiesTest.java`
9. `backend/src/test/java/com/polyglotmesh/config/GraalVmPropertiesTest.java`
10. `backend/src/test/java/com/polyglotmesh/execution/ExecutionTimeoutIntegrationTest.java`
11. `backend/src/test/java/com/polyglotmesh/runtime/GraalVmEngineTest.java`
12. `backend/src/test/java/com/polyglotmesh/runtime/GraalVmPythonSandboxTest.java`
13. `backend/src/test/java/com/polyglotmesh/runtime/GraalVmSandboxTest.java`
14. `backend/src/test/java/com/polyglotmesh/runtime/JavaExecutionSandboxTest.java`
15. `backend/src/test/java/com/polyglotmesh/service/ExecutionGateTest.java`

### Test Coverage Added

- `ExecutionPropertiesTest`: safe defaults and independent compile versus Java
  run budgets.
- `GraalVmPropertiesTest`: setup timeout defaults separately from guest
  execution.
- `ExecutionTimeoutIntegrationTest`: end-to-end runaway JavaScript, Python and
  Java programs are stopped at their configured budget, and a later execution
  still succeeds.
- `GraalVmSandboxTest`: rejection of executions after sandbox shutdown.
- Updated Python and Java sandbox tests for the new constructor and timeout
  behaviour.

### Verification

Recorded in the Day 16 commit:

- `mvn -B -ntp clean verify`: **115 tests, 0 failures, 0 errors, 0 skipped**;
  `BUILD SUCCESS` in 2 min 52 s; jar written to `target/`.
- Packaged-jar smoke test: **10/10 checks passed** — health, build metadata,
  nine-language discovery, JavaScript, Python, Java, a JavaScript infinite loop
  stopped at 5000 ms, a successful execution immediately afterwards, Ruby `501`,
  and CORS.
- After shutdown: no child `java` processes, no `polyglotmesh-java-*` temporary
  directories, and no `ERROR`/`WARN` lines in the application log.

### Cleanup Behaviour

- A GraalVM context is closed in a `finally` block after success, failure or
  timeout.
- Timed-out setups are cancelled, and the handoff guarantees that the context is
  closed exactly once.
- Tracked contexts are closed during application shutdown.
- A timed-out Java child JVM is force-killed and its temporary directory is
  removed.
- Stale Java work directories older than one hour are swept at startup.
- New executions are rejected once the sandbox worker pool is shut down.

### Known Limitations

- The in-process GraalVM engines still have no hard per-request CPU or memory
  quota; the timeout is a wall-clock deadline, not a cgroup limit.
- Cold GraalPy setup takes roughly 22 seconds on this host. It is bounded by the
  60-second setup budget and is not charged to the program's execution budget,
  but the first Python request after a restart is still slow. No warm-up is
  implemented yet.
- Java execution remains process isolation, not a security sandbox: guest Java
  code can still access files and the network as the backend user.
- Admission control is in-memory and per instance; it protects capacity, not
  fairness.
- Ruby, C, C++, Rust, Go and R still return `501`.

### Git Information

**Original commit:** `abff955ba07a68ada15325bc3c7a6addd344930d`  
**Original commit message:** `feat(execution): improve resource management and timeouts`

The Day 16 implementation was committed and pushed to the GitHub `main` branch.

This report documents the completed Day 16 work. The `leelavardhini-dev` branch
carries this documentation update; the implementation remains on `main`.
