import { LANGUAGES } from '../../data/languages'
import { Container } from '../layout/Container'
import { SectionHeading } from './SectionHeading'

/** The line that actually prints the greeting — the most useful single line to show. */
function outputLine(code: string) {
  return (
    code
      .split('\n')
      .find((line) => line.includes('Hello PolyglotMesh'))
      ?.trim() ?? code.split('\n')[0]
  )
}

export function LanguagesSection() {
  return (
    <section id="languages" className="scroll-mt-20 py-16 sm:py-20">
      <Container>
        <SectionHeading
          eyebrow="Supported languages"
          title="Start with the languages you already know"
          description="Each language gets its own editor mode, starter program and syntax highlighting. Java runs on the GraalVM JDK; Python and JavaScript run through the GraalVM Polyglot API."
        />

        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {LANGUAGES.map((language) => (
            <li
              key={language.id}
              className="border-line bg-surface rounded-panel hover:border-brand/40 flex flex-col gap-3 border p-5 transition-colors"
            >
              <div className="flex items-baseline justify-between gap-3">
                <h3 className="text-ink text-base font-semibold">
                  {language.name}
                </h3>
                <span className="text-faint font-mono text-xs">
                  {language.fileName}
                </span>
              </div>

              <p className="text-muted text-sm leading-relaxed">
                {language.description}
              </p>

              <pre className="border-line/70 bg-base/60 mt-auto overflow-x-auto rounded-lg border px-3 py-2.5 font-mono text-xs leading-6">
                <code className="text-muted">
                  {outputLine(language.starterCode)}
                </code>
              </pre>

              <p className="text-faint flex items-center gap-2 text-xs">
                <span className="bg-accent/80 size-1.5 rounded-full" />
                {language.runtime}
              </p>
            </li>
          ))}
        </ul>
      </Container>
    </section>
  )
}
