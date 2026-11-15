import type { ReactNode } from 'react'
import { LANGUAGES } from '../../data/languages'

/**
 * Decorative editor illustration for the hero.
 *
 * Deliberately static: it renders markup only, has no Run control and is
 * hidden from assistive technology. It must never be mistaken for a working
 * editor, so the caption below the card says so explicitly.
 */
export function EditorPreview() {
  return (
    <figure className="m-0">
      <div
        aria-hidden="true"
        className="border-line bg-surface rounded-panel overflow-hidden border shadow-2xl shadow-black/50"
      >
        {/* Window chrome */}
        <div className="border-line/80 bg-raised/50 flex items-center gap-2 border-b px-4 py-2.5">
          <span className="size-2.5 rounded-full bg-[#ff5f57]/80" />
          <span className="size-2.5 rounded-full bg-[#febc2e]/80" />
          <span className="size-2.5 rounded-full bg-[#28c840]/80" />
          <span className="text-faint ml-3 font-mono text-xs">
            polyglotmesh / workspace
          </span>
        </div>

        {/* File tabs */}
        <div className="border-line/80 flex items-center gap-1 border-b px-2 py-1.5">
          {LANGUAGES.map((language, index) => (
            <span
              key={language.id}
              className={[
                'rounded-md px-2.5 py-1 font-mono text-xs',
                index === 0
                  ? 'bg-raised text-ink'
                  : 'text-faint',
              ].join(' ')}
            >
              {language.fileName}
            </span>
          ))}
        </div>

        {/* Code */}
        <pre className="overflow-x-auto px-4 py-4 font-mono text-[13px] leading-6">
          <code>
            <CodeLine number={1}>
              <span className="text-syn-keyword">public</span>{' '}
              <span className="text-syn-keyword">class</span>{' '}
              <span className="text-syn-type">Main</span> {'{'}
            </CodeLine>
            <CodeLine number={2}>
              {'    '}
              <span className="text-syn-keyword">public</span>{' '}
              <span className="text-syn-keyword">static</span>{' '}
              <span className="text-syn-keyword">void</span>{' '}
              <span className="text-syn-type">main</span>(
              <span className="text-syn-type">String</span>[] args) {'{'}
            </CodeLine>
            <CodeLine number={3}>
              {'        '}
              <span className="text-syn-type">System</span>.out.
              <span className="text-syn-type">println</span>(
              <span className="text-syn-string">"Hello PolyglotMesh!"</span>);
            </CodeLine>
            <CodeLine number={4}>{'    }'}</CodeLine>
            <CodeLine number={5}>{'}'}</CodeLine>
            <CodeLine number={6}>
              <span className="text-syn-comment">
                // Run output appears in the output panel
              </span>
            </CodeLine>
          </code>
        </pre>

        {/* Status bar */}
        <div className="border-line/80 text-faint flex items-center justify-between border-t px-4 py-2 text-[11px]">
          <span className="font-mono">Java 21 · GraalVM JDK</span>
          <span className="font-mono">Ln 3, Col 9</span>
        </div>
      </div>

      <figcaption className="text-faint mt-3 text-center text-xs">
        Illustration of the editor. Running code is not available yet.
      </figcaption>
    </figure>
  )
}

function CodeLine({
  number,
  children,
}: {
  number: number
  children: ReactNode
}) {
  return (
    <span className="flex">
      <span className="text-faint/70 w-8 shrink-0 text-right tabular-nums select-none">
        {number}
      </span>
      <span className="pl-4 whitespace-pre">{children}</span>
    </span>
  )
}
