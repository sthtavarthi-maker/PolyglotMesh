import { PaneHeader } from './PaneHeader'

type EditorPaneProps = {
  fileName: string
  code: string
  onChange: (code: string) => void
}

/**
 * Temporary editing surface.
 *
 * A plain textarea is used until Monaco is integrated (Day 7); it keeps the
 * workspace layout honest and usable without pretending to be a full editor.
 */
export function EditorPane({ fileName, code, onChange }: EditorPaneProps) {
  return (
    <section className="border-line bg-surface rounded-panel flex h-[26rem] min-w-0 flex-col overflow-hidden border lg:h-full lg:min-h-[34rem]">
      <PaneHeader
        title={fileName}
        aside={
          <span className="border-line bg-raised text-faint rounded-md border px-2 py-0.5 text-[11px]">
            Preview editor
          </span>
        }
      />

      <label className="flex min-h-0 flex-1 flex-col">
        <span className="sr-only">Code editor</span>
        <textarea
          value={code}
          onChange={(event) => onChange(event.target.value)}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          className="bg-surface text-ink min-h-0 flex-1 resize-none p-4 font-mono text-[13px] leading-6 outline-none"
        />
      </label>

      <div className="border-line/80 text-faint flex items-center justify-between gap-3 border-t px-3 py-1.5 text-[11px]">
        <span>{lineCount(code)} lines</span>
        <span className="font-mono">
          Plain-text editor · Monaco integration in progress
        </span>
      </div>
    </section>
  )
}

function lineCount(code: string) {
  return code.replace(/\n$/, '').split('\n').length
}
