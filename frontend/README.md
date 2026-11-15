# PolyglotMesh Frontend

React + TypeScript + Vite client for [PolyglotMesh](../README.md).

## Requirements

- Node.js 20.19+ (verified with 25.7.0)
- npm 10+ (verified with 11.10.1)

## Commands

```bash
npm install      # install dependencies
npm run dev      # start the Vite dev server on http://localhost:5173
npm run build    # type-check (tsc -b) and bundle into dist/
npm run preview  # serve the production build locally
npm run lint     # run oxlint
```

## Source Layout

```
src/
├── components/   Reusable presentational components
├── pages/        Route-level page components
├── services/     API clients and backend communication
├── hooks/        Custom React hooks
├── context/      React context providers
├── types/        Shared TypeScript types
├── App.tsx       Root application component
├── main.tsx      Application entry point
└── index.css     Global base styles
```

Folders are created as their first real content arrives during Week 1. Empty folders are
tracked with a `.gitkeep` file.

## Status

Week 1, Day 3 — the React application is initialized and running. Styling (Tailwind CSS),
routing, the landing page, the compiler workspace and Monaco Editor are added on Days 4–7.

**No code execution is implemented.** Nothing in this client talks to a backend yet; any output
shown in the UI before backend integration is demonstration data.
