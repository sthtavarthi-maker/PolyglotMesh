import { Link } from 'react-router-dom'
import { Container } from '../components/layout/Container'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * Entry route. Day 5 replaces the body of this page with the full landing
 * page (hero, supported languages, features, how it works).
 */
export function HomePage() {
  useDocumentTitle()

  return (
    <Container className="py-16 sm:py-24">
      <div className="max-w-2xl space-y-6">
        <p className="border-line bg-surface text-muted w-fit rounded-full border px-3 py-1 text-xs font-medium tracking-wide uppercase">
          Week 1 · frontend foundation
        </p>

        <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          One workspace for Java, Python and JavaScript.
        </h1>

        <p className="text-muted text-lg leading-relaxed">
          PolyglotMesh is a multi-language online compiler. Write code in the
          browser, run it on a GraalVM-backed service, and share working
          snippets with a link.
        </p>

        <div className="flex flex-wrap items-center gap-3 pt-2">
          <Link
            to="/workspace"
            className="bg-brand-strong hover:bg-brand-deep rounded-lg px-5 py-2.5 text-sm font-medium text-white transition-colors"
          >
            Open workspace
          </Link>
          <a
            href="https://github.com/sthtavarthi-maker/PolyglotMesh"
            target="_blank"
            rel="noreferrer"
            className="border-line bg-surface hover:border-brand/60 hover:text-ink text-muted rounded-lg border px-5 py-2.5 text-sm font-medium transition-colors"
          >
            View on GitHub
          </a>
        </div>

        <p className="text-faint pt-4 text-sm">
          Code execution is not connected yet. The editor and run pipeline
          arrive in later development days.
        </p>
      </div>
    </Container>
  )
}
