import { PaneHeader } from './PaneHeader'

/**
 * Output panel.
 *
 * Shows an empty state only. No demonstration output is rendered, because
 * nothing is executed until the backend is connected.
 */
export function OutputPanel() {
  return (
    <section className="border-line bg-surface rounded-panel flex min-h-[13rem] min-w-0 flex-1 flex-col overflow-hidden border">
      <PaneHeader
        title="Output"
        aside={
          <span className="border-line bg-raised text-faint rounded-md border px-2 py-0.5 text-[11px]">
            Not connected
          </span>
        }
      />

      <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6 py-8 text-center">
        <p className="text-ink text-sm font-medium">No output yet</p>
        <p className="text-faint max-w-xs text-xs leading-relaxed">
          Execution runs on the GraalVM backend, which is not connected. Once it
          is, program output and errors will appear here.
        </p>
      </div>
    </section>
  )
}
