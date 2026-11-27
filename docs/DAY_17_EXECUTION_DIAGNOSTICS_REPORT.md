# PolyglotMesh – Day 17 Work Report

## Execution Diagnostics and Status Reporting

**Date:** 27 November 2026  
**Branch:** main  
**Commit:** `f8ae347` (`f8ae3474882f804b693559b6149d0e30f54d2410`)

### Work Completed

Day 17 standardised execution status reporting and error handling. The backend
now returns standard output and standard error separately, classifies every
execution outcome with an explicit status, preserves useful compiler
diagnostics, and keeps backend stack traces out of API responses. The change is
consistent across the GraalVM sandboxes (JavaScript and Python) and the Java
child-JVM sandbox.

### Execution Status Classification

`ExecutionStatus` is returned as the `status` field of every execution response.

| Status | Meaning |
|---|---|
| `SUCCESS` | The program compiled (when applicable) and exited successfully |
| `COMPILE_ERROR` | The compiler rejected the source; `error` carries the diagnostics |
| `SYNTAX_ERROR` | The interpreter rejected the source before running it |
| `RUNTIME_ERROR` | The program started but raised an exception or exited non-zero |
| `TIMEOUT` | A setup, compilation or execution budget was exceeded |
| `CANCELLED` | The request was interrupted or the service was shutting down |
| `INTERNAL_ERROR` | The server failed for a reason that is not the program's fault; the message is generic |

A failed program is a valid execution result, so it still returns HTTP `200` with
`success: false`. API-level failures keep their existing HTTP contract and
`ApiError` shape: validation `400`, malformed JSON `400`, oversized body `413`,
rate limit or concurrency `429`, known language without a runtime `501`, and
unexpected server failure `500`.

### API Response Changes

`ExecutionResponse` now has this shape:

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

- `output` is standard output only.
- `stderr` is standard error only.
- `error` is the diagnostic (compiler output, exception message or timeout
  notice) and is `null` on success.
- `status` is always present.
- Output produced before a failure is still returned.

### Stream Separation

- JavaScript: `console.log` writes to `output`; `console.error` writes to
  `stderr`.
- Python: `print()` writes to `output`; `sys.stderr` writes to `stderr`.
- Java: `System.out` writes to `output`; `System.err` writes to `stderr`.
- A non-empty `stderr` does not by itself imply failure; `success` and `status`
  describe the outcome.

### Diagnostics and Safety

- GraalVM `PolyglotException` instances are classified as `SYNTAX_ERROR` or
  `RUNTIME_ERROR` based on the exception type.
- Setup timeouts and execution timeouts are classified as `TIMEOUT`; interrupted
  work and shutdown rejection are classified as `CANCELLED`.
- Java compiler diagnostics keep their line numbers and are classified as
  `COMPILE_ERROR`.
- Java non-zero exits and runtime exceptions are classified as `RUNTIME_ERROR`.
- Unexpected server-side failures are classified as `INTERNAL_ERROR` and return
  a generic message.
- Backend stack traces and internal exception details are never returned to
  clients; they remain in server logs only.
- The Java child stderr cap now matches the stdout cap so diagnostics are not
  truncated earlier than normal output.

### Files Updated

1. `README.md`
2. `backend/README.md`
3. `backend/src/main/java/com/polyglotmesh/dto/ExecutionResponse.java`
4. `backend/src/main/java/com/polyglotmesh/model/ExecutionResult.java`
5. `backend/src/main/java/com/polyglotmesh/model/ExecutionStatus.java`
6. `backend/src/main/java/com/polyglotmesh/runtime/GraalVmSandbox.java`
7. `backend/src/main/java/com/polyglotmesh/runtime/JavaExecutionSandbox.java`
8. `backend/src/test/java/com/polyglotmesh/dto/ExecutionResponseTest.java`
9. `backend/src/test/java/com/polyglotmesh/execution/ExecutionDiagnosticsIntegrationTest.java`
10. `backend/src/test/java/com/polyglotmesh/execution/ExecutionTimeoutIntegrationTest.java`
11. `backend/src/test/java/com/polyglotmesh/execution/JavaExecutionIntegrationTest.java`
12. `backend/src/test/java/com/polyglotmesh/execution/JavaScriptExecutionIntegrationTest.java`
13. `backend/src/test/java/com/polyglotmesh/execution/PythonExecutionIntegrationTest.java`
14. `backend/src/test/java/com/polyglotmesh/model/ExecutionResultTest.java`
15. `backend/src/test/java/com/polyglotmesh/runtime/GraalVmPythonSandboxTest.java`
16. `backend/src/test/java/com/polyglotmesh/runtime/GraalVmSandboxTest.java`
17. `backend/src/test/java/com/polyglotmesh/runtime/JavaExecutionSandboxTest.java`
18. `backend/src/test/java/com/polyglotmesh/service/ExecutionServiceTest.java`

### Test Coverage Added or Updated

- `ExecutionResultTest`: internal result invariants, separate streams, explicit
  status, default classification and rejection of inconsistent results.
- `ExecutionResponseTest`: public DTO mapping of language, stdout, stderr,
  diagnostic, status and timing.
- `ExecutionDiagnosticsIntegrationTest`: end-to-end classification of
  JavaScript/Python syntax errors, Java compile and runtime errors, preserved
  compiler line numbers, absence of backend stack traces, and the unchanged
  `400`/`501` `ApiError` contract.
- `ExecutionTimeoutIntegrationTest`: runaway programs classified as `TIMEOUT`
  with recovery afterwards.
- Updated GraalVM, Python, Java and service tests for the new status and stream
  fields.

### Verification

Recorded in the Day 17 commit:

- `mvn -B -ntp clean verify`: **144 tests, 0 failures, 0 errors, 0 skipped**;
  `BUILD SUCCESS` in 12 min 58 s; jar written to `target/`.
- Packaged-jar smoke test: **18/18 checks passed** — health, build metadata,
  nine-language discovery, JavaScript success with separate stderr, JavaScript
  syntax/runtime classification, Python success/runtime, Java success/compile
  error/runtime error with partial stdout, a JavaScript infinite loop stopped at
  ~5.1 s with `TIMEOUT`, successful recovery immediately afterwards, unknown
  language `400`, malformed JSON `400`, Ruby `501`, and CORS.
- After shutdown: no child `java` processes, no `polyglotmesh-java-*` temporary
  directories, and no `ERROR`/`WARN` lines in the application log.
- The suite ran under heavy memory pressure on this host, which made the
  child-JVM tests slow but did not change the result.

### Known Limitations

- The status classification covers the current JavaScript, Python and Java
  runtimes. Native-language adapters will need to map their own compiler and
  runtime failures onto the same enum when they are added.
- Diagnostics are returned as human-readable text; there is no structured array
  of individual compiler messages yet.
- Timeouts remain wall-clock deadlines, not hard CPU or memory quotas.
- Java execution remains process isolation, not a security sandbox: guest Java
  code can still access files and the network as the backend user.
- Ruby, C, C++, Rust, Go and R still return `501`.
- The frontend is not wired to the execution API yet; this work is API-level.

### Git Information

**Original commit:** `f8ae3474882f804b693559b6149d0e30f54d2410`  
**Original commit message:** `feat(errors): standardize execution diagnostics`

The Day 17 implementation was committed and pushed to the GitHub `main` branch.
The working tree was clean after the push.

This report documents the completed Day 17 work. The `leelavardhini-dev` branch
carries this documentation update; the implementation remains on `main`.
