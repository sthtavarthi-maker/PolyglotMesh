# PolyglotMesh Backend

Spring Boot service for PolyglotMesh, the GraalVM multi-language runtime sandbox.
Week 2 builds this backend incrementally; Day 8 is the project skeleton only.

## Stack

| Item | Version |
|---|---|
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Build tool | Maven 3.9.16 |
| Modules | Spring Web, Validation, Actuator |

Week 2 will add the GraalVM Polyglot API (`org.graalvm.polyglot`), JUnit 5 and
Mockito. JUnit 5 and Mockito already arrive through `spring-boot-starter-test`.

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
| POST | `/api/execute` | Unified execution endpoint (architecture only on Day 9) | `501` until an engine is registered; `400` for invalid input |
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

The intended success shape is:

```json
{
  "success": true,
  "language": "javascript",
  "output": "Hello PolyglotMesh",
  "error": null,
  "executionTimeMs": 15
}
```

Validation limits (enforced before any executor is reached):

| Field | Rule |
|---|---|
| `language` | required, at most 32 characters; one of the nine known ids, case-insensitive |
| `code` | required, at most 100,000 characters |
| `input` | optional, at most 10,000 characters; null is treated as empty |

Current behaviour and status codes:

| Situation | Status | Body |
|---|---|---|
| Valid request, no engine for that language | `501 Not Implemented` | `ApiError` explaining that no engine is available yet |
| Unknown language id | `400 Bad Request` | `ApiError` listing the known ids |
| Blank/missing/oversized field | `400 Bad Request` | `ApiError` with the failing field(s) |
| Malformed or missing JSON | `400 Bad Request` | `ApiError` |
| Unexpected server failure | `500 Internal Server Error` | generic `ApiError`, details only in logs |

There is deliberately **no execution logic on Day 9**. The endpoint validates,
resolves the language and routes to a `CodeExecutor`; because none is registered
yet, every valid request gets an honest `501` instead of invented output. As of
Day 9 no language — including JavaScript, Python and Java — is executable. Ruby,
C, C++, Rust, Go and R stay editor-only until adapters exist and are tested.

## Architecture

```
HTTP -> ExecutionController   (validation, status codes)
          |
          v
        ExecutionService      (language resolution, executor routing)
          |
          v
        CodeExecutor          (interface; one bean per language, none yet)
          |
          v
        ExecutionResult       (internal) -> ExecutionResponse (API)
```

- `ExecutionRequest` / `ExecutionResponse` / `ApiError` are the API contract.
- `ExecutionResult` stays internal so adapters can evolve without breaking clients.
- Executors are injected as a list and indexed by `Language`; adding a language
  is a new bean, not a change to the controller or service. `isAvailable()` lets
  an adapter fail closed when its runtime or toolchain is missing.
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
| `ExecutionControllerTest` | 501 without engines, 400 for invalid/blank/unknown/malformed requests, 405 |
| `GlobalExceptionHandlerTest` | Generic 500 that does not leak exception details |

## Current limitations

- No execution engine is registered yet, so `/api/execute` answers `501` for all
  nine languages. This is not simulated execution.
- No GraalVM dependency or runtime is wired yet (Day 10).
- No authentication, database or persistence.
- No claim of sandbox security: execution isolation is designed in later days and
  is not production-strength merely because a process or `Context` exists.
- Limits enforced today are request-size and routing checks at the API boundary;
  CPU/memory limits belong to the executors and their isolation layer.
