# PolyglotMesh

A multi-language online compiler platform for writing, running and sharing code directly in the browser.

PolyglotMesh is built on **Java 21 + GraalVM** and uses the **GraalVM Polyglot API** to execute multiple
languages inside a single JVM process — no external execution service, container runtime or third-party
judge is involved.

> **Project status:** Week 1 — project foundation and frontend development.
> Code execution is not implemented yet. Any output shown in the UI before backend integration is
> clearly marked as demonstration data.

---

## Planned Features

- Multi-language code editor (Java, Python, JavaScript)
- Language-aware starter code and syntax highlighting
- Program input (stdin) and output panels
- Save and share code snippets
- Light and dark editor themes
- Account authentication with JWT
- Execution powered by the GraalVM Polyglot API

## Technology Stack

| Layer | Technology |
|---|---|
| Primary language | Java 21 |
| Multi-language runtime | GraalVM for JDK 21 |
| Polyglot integration | GraalVM Polyglot API |
| Build tool | Maven |
| Backend framework | Spring Boot |
| Authentication | Spring Security + JWT |
| Database | MySQL |
| Frontend | React.js + TypeScript |
| Frontend build tool | Vite |
| UI styling | Tailwind CSS |
| Code editor | Monaco Editor |
| API style | REST |
| API testing | Postman |

## Repository Structure

```
PolyglotMesh/
├── frontend/       React + TypeScript + Vite client
├── backend/        Spring Boot + Java 21 service
├── docs/           Project and setup documentation
├── screenshots/    Reference screenshots of the running application
├── README.md
└── .gitignore
```

## Getting Started

The frontend and backend are introduced in later development days. Until then, the only requirement is
Git and a GitHub account.

```bash
git clone https://github.com/sthtavarthi-maker/PolyglotMesh.git
cd PolyglotMesh
```

The verified local development environment (tool versions, known PATH issues, and how to re-check
everything) is documented in [`docs/DEVELOPMENT_SETUP.md`](docs/DEVELOPMENT_SETUP.md).

## Development Roadmap

| Day | Scope |
|---|---|
| 1 | Project initialization and repository structure |
| 2 | Development environment verification |
| 3 | React + TypeScript application initialization |
| 4 | Tailwind CSS styling and routing |
| 5 | Landing page |
| 6 | Compiler workspace UI |
| 7 | Monaco Editor integration |

Further weekly plans are added as the project progresses.

## License

No license has been chosen yet. All rights reserved until a license is added.
