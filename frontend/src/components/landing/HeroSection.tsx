import { Link } from 'react-router-dom'
import { Container } from '../layout/Container'
import { EditorPreview } from './EditorPreview'

const HERO_FACTS = [
  { value: '3', label: 'languages' },
  { value: '1', label: 'runtime' },
  { value: '0', label: 'installs' },
]

export function HeroSection() {
  return (
    <section className="border-line/60 relative overflow-hidden border-b">
      {/* Subtle depth without an image asset */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60rem_28rem_at_70%_-10%,rgba(79,140,255,0.16),transparent_70%)]"
      />

      <Container className="relative py-16 sm:py-20 lg:py-24">
        <div className="grid items-center gap-12 lg:grid-cols-[1.05fr_1fr] lg:gap-16">
          <div className="max-w-xl">
            <p className="border-line bg-surface text-muted inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium">
              <span className="bg-accent size-1.5 rounded-full" />
              Multi-language online compiler
            </p>

            <h1 className="mt-5 text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-[3.25rem] lg:leading-[1.08]">
              One workspace for Java, Python and JavaScript.
            </h1>

            <p className="text-muted mt-5 text-lg leading-relaxed">
              PolyglotMesh runs your code on a GraalVM-backed service, so three
              languages share one environment. Write in the browser, keep your
              input next to your output, and share a snippet with a link.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                to="/workspace"
                className="bg-brand-strong hover:bg-brand-deep rounded-lg px-5 py-2.5 text-sm font-medium text-white transition-colors"
              >
                Start coding
              </Link>
              <a
                href="#how-it-works"
                className="border-line bg-surface/60 hover:border-brand/60 hover:text-ink text-muted rounded-lg border px-5 py-2.5 text-sm font-medium transition-colors"
              >
                See how it works
              </a>
            </div>

            <dl className="border-line/70 mt-10 flex max-w-sm gap-8 border-t pt-6">
              {HERO_FACTS.map((fact) => (
                <div key={fact.label}>
                  <dt className="text-faint text-xs uppercase tracking-wide">
                    {fact.label}
                  </dt>
                  <dd className="text-ink mt-0.5 text-xl font-semibold">
                    {fact.value}
                  </dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="lg:pl-4">
            <EditorPreview />
          </div>
        </div>
      </Container>
    </section>
  )
}
