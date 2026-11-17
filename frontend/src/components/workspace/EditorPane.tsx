import { lazy, Suspense } from 'react'
import type { Language } from '../../types/language'
import type { EditorTheme } from '../../types/workspace'
import { PaneHeader } from './PaneHeader'

/*
 * Monaco is code-split: it downloads only when the workspace is opened, not on
 * the landing page or any other route.
 */
const CodeEditor = lazy(() =>
  import('./CodeEditor').then((module) => ({ default: module.CodeEditor })),
)

type EditorPaneProps = {
  language: Language
  code: string
  theme: EditorTheme
  onChange: (code: string) => void
}

export function EditorPane({
  language,
  code,
  theme,
  onChange,
}: EditorPaneProps) {
  return (
    <section className="border-line bg-surface rounded-panel flex h-[26rem] min-w-0 flex-col overflow-hidden border lg:h-full lg:min-h-[34rem]">
      <PaneHeader
        title={language.fileName}
        aside={
          <span className="border-line bg-raised text-faint rounded-md border px-2 py-0.5 text-[11px]">
            Monaco
          </span>
        }
      />

      <div className="min-h-0 flex-1">
        <Suspense fallback={<EditorLoading />}>
          <CodeEditor
            fileName={language.fileName}
            language={language.id}
            tabSize={language.tabSize}
            theme={theme}
            value={code}
            onChange={onChange}
          />
        </Suspense>
      </div>

      <div className="border-line/80 text-faint flex items-center justify-between gap-3 border-t px-3 py-1.5 text-[11px]">
        <span>{lineCount(code)} lines</span>
        <span className="font-mono">
          {language.name} · tab size {language.tabSize} · spaces
        </span>
      </div>
    </section>
  )
}

function EditorLoading() {
  return (
    <div className="text-faint flex h-full items-center justify-center font-mono text-xs">
      Loading editor…
    </div>
  )
}

function lineCount(code: string) {
  return code.replace(/\n$/, '').split('\n').length
}
