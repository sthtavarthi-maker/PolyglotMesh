import { useEffect, useRef } from 'react'
import { Editor, type OnMount } from '@monaco-editor/react'
import type { editor as monacoEditor } from 'monaco-editor'
import { MONACO_THEMES } from '../../lib/monacoSetup'
import type { EditorTheme } from '../../types/workspace'

type CodeEditorProps = {
  fileName: string
  language: string
  tabSize: number
  insertSpaces: boolean
  theme: EditorTheme
  value: string
  onChange: (value: string) => void
}

const THEME_NAMES: Record<EditorTheme, string> = {
  dark: MONACO_THEMES.dark,
  light: MONACO_THEMES.light,
}

/**
 * Monaco wrapper. Loaded lazily from EditorPane so the editor engine (and its
 * workers) never reaches users who only visit the landing page.
 */
export function CodeEditor({
  fileName,
  language,
  tabSize,
  insertSpaces,
  theme,
  value,
  onChange,
}: CodeEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const editorRef = useRef<monacoEditor.IStandaloneCodeEditor | null>(null)

  /*
   * Layout is driven from here instead of relying on Monaco's own measurement.
   *
   * @monaco-editor/react creates the editor inside a wrapper that is
   * `display: none` until it reports ready, so Monaco measures a 0x0 container,
   * clamps it to its 5px minimum (ElementSizeObserver uses Math.max(5, size))
   * and then keeps rendering that 5px box.
   *
   * The fix is to feed Monaco the real panel size ourselves: once on mount,
   * once on the next task (the wrapper is visible by then) and on every resize
   * of the panel. No requestAnimationFrame: it never fires in a background tab,
   * which is exactly when the editor would otherwise stay collapsed.
   */
  function layoutEditor(instance: monacoEditor.IStandaloneCodeEditor) {
    const container = containerRef.current
    if (!container) return
    const width = container.clientWidth
    const height = container.clientHeight
    if (width === 0 || height === 0) return
    instance.layout({ width, height })
  }

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const observer = new ResizeObserver(() => {
      const instance = editorRef.current
      if (instance) layoutEditor(instance)
    })
    observer.observe(container)

    return () => observer.disconnect()
  }, [])

  const handleMount: OnMount = (instance) => {
    editorRef.current = instance
    layoutEditor(instance)
    // The wrapper is still hidden while it mounts; re-measure once it is shown.
    setTimeout(() => layoutEditor(instance), 0)
  }

  return (
    <div ref={containerRef} className="h-full w-full">
      <Editor
        path={fileName}
        language={language}
        value={value}
        theme={THEME_NAMES[theme]}
        onChange={(next) => onChange(next ?? '')}
        onMount={handleMount}
        height="100%"
        loading={<EditorLoading />}
        options={{
          ariaLabel: `Code editor, ${fileName}`,
          automaticLayout: false,
          lineNumbers: 'on',
          minimap: { enabled: false },
          fontFamily:
            "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
          fontSize: 13,
          lineHeight: 24,
          tabSize,
          insertSpaces,
          autoIndent: 'full',
          detectIndentation: false,
          wordWrap: 'off',
          scrollBeyondLastLine: false,
          smoothScrolling: true,
          renderLineHighlight: 'line',
          padding: { top: 16, bottom: 16 },
          overviewRulerLanes: 0,
          hideCursorInOverviewRuler: true,
          scrollbar: {
            verticalScrollbarSize: 10,
            horizontalScrollbarSize: 10,
          },
          bracketPairColorization: { enabled: true },
          guides: { indentation: true, bracketPairs: false },
          fixedOverflowWidgets: true,
          contextmenu: true,
          quickSuggestions: { other: false, comments: false, strings: false },
          suggestOnTriggerCharacters: false,
        }}
      />
    </div>
  )
}

function EditorLoading() {
  return (
    <div className="text-faint flex h-full items-center justify-center font-mono text-xs">
      Loading editor…
    </div>
  )
}
