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
| GET | `/actuator/health` | Actuator liveness/readiness | `{"status":"UP"}` |
| GET | `/actuator/info` | Application metadata | build/app info |

Only `health` and `info` actuator endpoints are exposed; health details are hidden.

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

## What Day 8 does not do

- No code execution of any kind. `/api/execute` arrives on Day 9–14.
- No GraalVM dependency or runtime is wired yet (Day 10).
- No authentication, database or persistence.
- No claim of sandbox security: execution isolation is designed in later days and
  is not production-strength merely because a process or `Context` exists.
