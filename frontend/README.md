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

## Landing Page

`pages/HomePage.tsx` composes self-contained sections from `components/landing/`:

| Component | Section |
|---|---|
| `HeroSection` | Headline, description, Start coding button, supporting facts |
| `EditorPreview` | Static CSS-only illustration of the editor (marked as an illustration) |
| `LanguagesSection` | Java, Python and JavaScript cards, generated from `data/languages.ts` |
| `FeaturesSection` | Six platform capabilities with inline SVG icons |
| `HowItWorksSection` | Choose → write → run, save, share |
| `CtaSection` | Closing call to action |

Language metadata and starter programs live in `src/data/languages.ts` (typed by
`src/types/language.ts`) so the landing page and, later, the editor use one source of truth.

## Source Layout

```
src/
├── components/   Reusable presentational components
├── pages/        Route-level page components
├── lib/          Third-party setup (Monaco loader, themes, workers)
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

## Routes

| Path | Component | Purpose |
|---|---|---|
| `/` | `pages/HomePage.tsx` | Landing page (full content arrives on Day 5) |
| `/workspace` | `pages/WorkspacePage.tsx` | Compiler workspace with the Monaco editor |
| `*` | `pages/NotFoundPage.tsx` | 404 fallback |

Routing uses `react-router-dom` with `BrowserRouter` mounted in `main.tsx`. Routes are declared
in `App.tsx` as children of `components/layout/AppLayout.tsx`, so every page automatically gets
the shared header, footer and skip link. Each page sets its own tab title through the
`hooks/useDocumentTitle.ts` hook.

## Styling

Tailwind CSS **v4** is configured through the `@tailwindcss/vite` plugin — there is no
`tailwind.config.js` and no `@tailwind base/components/utilities` directive. Design tokens are
declared in the `@theme` block of `src/index.css` and become utilities automatically:

| Token | Utility | Use |
|---|---|---|
| `--color-base` | `bg-base` | Page background |
| `--color-surface` | `bg-surface` | Panels and cards |
| `--color-raised` | `bg-raised` | Hover and active surfaces |
| `--color-line` | `border-line` | Borders and dividers |
| `--color-ink` / `--color-muted` / `--color-faint` | `text-ink` etc. | Text hierarchy |
| `--color-brand` / `--color-brand-strong` / `--color-brand-deep` | `text-brand`, `bg-brand-strong` | Links, accents, buttons |
| `--color-accent` | `text-accent` | Secondary accent |
| `--radius-panel` | `rounded-panel` | Panel corners |

The palette is dark-only for now; a theme switcher belongs to the workspace (Day 6). Every
colour combination used for text meets WCAG AA (4.5:1), verified with Lighthouse.

## Workspace

`pages/WorkspacePage.tsx` owns the workspace state and composes `components/workspace/`:

| Component | Role |
|---|---|
| `WorkspaceToolbar` | Compiler navigation bar: language, theme, Save / Share / Run |
| `LanguageSelect` / `ThemeSelect` | Native selects, styled to match the tokens |
| `EditorPane` | Editor panel: file name, Monaco surface, line count |
| `CodeEditor` | Monaco wrapper (loaded lazily, see below) |
| `InputPanel` | Program input (stdin) |
| `OutputPanel` | Output area with an empty state |
| `PaneHeader` | Shared pane header |

Behaviour implemented today:

- **Language switching** loads that language's starter program. Drafts are kept per language, so
  switching back does not discard typed code.
- **Theme switching** retints the editor, input and output surfaces through the
  `.code-surface[data-theme]` custom-property overrides in `src/index.css`; the rest of the site
  stays dark.
- **Run, Save and Share are disabled.** There is no backend, so they are rendered with neutral
  styling and an explanatory note rather than pretending to work.
- **The output panel shows an empty state only** — never simulated program output.

## Editor

The code editor is **Monaco self-hosted through Vite**, not loaded from a CDN.

- `src/lib/monacoSetup.ts` points `@monaco-editor/react`'s loader at the locally installed
  `monaco-editor` package, registers the editor and TypeScript workers with Vite (`?worker`), and
  defines the `polyglotmesh-dark` / `polyglotmesh-light` themes. Those themes reuse the same
  colours as the CSS tokens so editor and panel look like one surface.
- `src/components/workspace/CodeEditor.tsx` holds the editor options: line numbers, tab size per
  language, spaces instead of tabs, full auto-indent, no minimap, oversized-widget bounds.
- Monaco is **code-split**: `EditorPane` imports `CodeEditor` with `React.lazy`, so the engine
  (~4 MB of JS with its workers) is fetched only when someone opens `/workspace`. Every language
  definition is its own chunk as well.
- **Layout is driven explicitly.** `@monaco-editor/react` creates the editor inside a wrapper that
  is `display: none` until ready. Monaco measures that container, gets 0×0, clamps it to its 5px
  minimum (`ElementSizeObserver` uses `Math.max(5, size)`) and then keeps rendering a 5px box. The
  wrapper therefore measures the real panel itself and calls `editor.layout({ width, height })` on
  mount, on the next task and on every `ResizeObserver` callback. It does not use
  `requestAnimationFrame`, because rAF never fires in a background tab.
- `monaco-editor@0.57` ships a vulnerable `dompurify`; `package.json` pins the patched
  `dompurify@^3.4.18` through `overrides` so `npm audit` stays clean.

## Status

Week 1, Day 7 — the compiler workspace runs on Monaco: line numbers, syntax highlighting,
auto-indent, per-language tab sizes, Java / Python / JavaScript modes, language switching with
drafts kept per language, and dark / light editor themes.

**No code execution is implemented.** Nothing in this client talks to a backend yet; any output
shown in the UI before backend integration is demonstration data.
