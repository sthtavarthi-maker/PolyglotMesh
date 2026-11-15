import { PaneHeader } from './PaneHeader'

type InputPanelProps = {
  value: string
  onChange: (value: string) => void
}

/** Program input (stdin). Stored locally; nothing is sent anywhere yet. */
export function InputPanel({ value, onChange }: InputPanelProps) {
  return (
    <section className="border-line bg-surface rounded-panel flex h-40 min-w-0 flex-col overflow-hidden border">
      <PaneHeader
        title="Input"
        aside={
          <span className="text-faint text-[11px]">stdin · optional</span>
        }
      />

      <label className="flex min-h-0 flex-1 flex-col">
        <span className="sr-only">Standard input</span>
        <textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="Programs that read input will receive this text."
          spellCheck={false}
          className="bg-surface text-ink placeholder:text-faint min-h-0 flex-1 resize-none p-3 font-mono text-[13px] leading-6 outline-none"
        />
      </label>
    </section>
  )
}
