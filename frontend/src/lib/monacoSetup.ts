import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'

/*
 * Monaco is self-hosted, not loaded from a CDN.
 *
 * The engine and its web workers are bundled by Vite, so the editor works
 * offline, always uses the version pinned in package.json, and makes no
 * third-party request at runtime.
 */
/*
 * Monaco 0.57 exposes subpaths through its package `exports` map, so workers
 * are imported as `monaco-editor/editor/editor.worker` rather than the older
 * `monaco-editor/esm/vs/editor/editor.worker` specifier.
 */
import editorWorker from 'monaco-editor/editor/editor.worker?worker'
import tsWorker from 'monaco-editor/language/typescript/ts.worker?worker'

type MonacoEnvironment = {
  getWorker: (workerId: string, label: string) => Worker
}

const environment: MonacoEnvironment = {
  getWorker(_workerId, label) {
    // JavaScript/TypeScript get a language-service worker; Java and Python use
    // Monaco's built-in Monarch tokenizers, which run on the main thread.
    if (label === 'typescript' || label === 'javascript') {
      return new tsWorker()
    }
    return new editorWorker()
  },
}

;(self as unknown as { MonacoEnvironment: MonacoEnvironment }).MonacoEnvironment =
  environment

// Use the locally bundled engine instead of @monaco-editor/react's default CDN loader.
loader.config({ monaco })

/** Theme names defined below, keyed by the app's editor theme. */
export const MONACO_THEMES = {
  dark: 'polyglotmesh-dark',
  light: 'polyglotmesh-light',
} as const

/*
 * Themes mirror the CSS design tokens so the editor sits seamlessly inside a
 * panel: same background as `bg-surface`, same ink and accent colours.
 */
monaco.editor.defineTheme('polyglotmesh-dark', {
  base: 'vs-dark',
  inherit: true,
  rules: [
    { token: 'comment', foreground: '6f81a3', fontStyle: 'italic' },
    { token: 'keyword', foreground: 'c792ea' },
    { token: 'string', foreground: '9ad48d' },
    { token: 'number', foreground: 'f0b27a' },
    { token: 'type', foreground: '7ce0c0' },
    { token: 'type.identifier', foreground: '7ce0c0' },
    { token: 'annotation', foreground: '9ad48d' },
  ],
  colors: {
    'editor.background': '#0d1424',
    'editor.foreground': '#e8eefc',
    'editorLineNumber.foreground': '#6b7d9e',
    'editorLineNumber.activeForeground': '#e8eefc',
    'editor.lineHighlightBackground': '#131c30',
    'editor.lineHighlightBorder': '#00000000',
    'editor.selectionBackground': '#25406b',
    'editor.inactiveSelectionBackground': '#1b3050',
    'editorCursor.foreground': '#4f8cff',
    'editorIndentGuide.background1': '#1f2b45',
    'editorIndentGuide.activeBackground1': '#2f3f5e',
    'editorWidget.background': '#131c30',
    'editorWidget.border': '#1f2b45',
    'editorGutter.background': '#0d1424',
    'editorBracketMatch.background': '#1f2b4566',
    'editorBracketMatch.border': '#4f8cff',
  },
})

monaco.editor.defineTheme('polyglotmesh-light', {
  base: 'vs',
  inherit: true,
  rules: [
    { token: 'comment', foreground: '64748b', fontStyle: 'italic' },
    { token: 'keyword', foreground: '7c3aed' },
    { token: 'string', foreground: '15803d' },
    { token: 'number', foreground: 'b45309' },
    { token: 'type', foreground: '0f766e' },
    { token: 'type.identifier', foreground: '0f766e' },
    { token: 'annotation', foreground: '15803d' },
  ],
  colors: {
    'editor.background': '#ffffff',
    'editor.foreground': '#0f1b2d',
    'editorLineNumber.foreground': '#8a97ab',
    'editorLineNumber.activeForeground': '#334155',
    'editor.lineHighlightBackground': '#f4f7fc',
    'editor.lineHighlightBorder': '#00000000',
    'editor.selectionBackground': '#d7e5ff',
    'editor.inactiveSelectionBackground': '#e8f0ff',
    'editorCursor.foreground': '#2563eb',
    'editorIndentGuide.background1': '#e2e8f2',
    'editorIndentGuide.activeBackground1': '#c3cede',
    'editorWidget.background': '#ffffff',
    'editorWidget.border': '#d3dcea',
    'editorGutter.background': '#ffffff',
    'editorBracketMatch.background': '#dbe7fb',
    'editorBracketMatch.border': '#2563eb',
  },
})
