import { Link } from 'react-router-dom'
import { BrandMark } from '../BrandMark'
import { Container } from './Container'

const CURRENT_YEAR = new Date().getFullYear()

export function SiteFooter() {
  return (
    <footer className="border-line/80 border-t">
      <Container className="flex flex-col gap-6 py-10 sm:flex-row sm:items-start sm:justify-between">
        <div className="max-w-sm space-y-3">
          <div className="text-ink flex items-center gap-2 text-sm font-semibold">
            <BrandMark className="size-6" />
            <span>PolyglotMesh</span>
          </div>
          <p className="text-muted text-sm leading-relaxed">
            A multi-language online compiler for writing, running and sharing
            code in the browser.
          </p>
        </div>

        <nav aria-label="Footer" className="flex flex-col gap-2 text-sm">
          <Link to="/" className="text-muted hover:text-ink w-fit">
            Home
          </Link>
          <Link to="/workspace" className="text-muted hover:text-ink w-fit">
            Workspace
          </Link>
          <a
            href="https://github.com/sthtavarthi-maker/PolyglotMesh"
            target="_blank"
            rel="noreferrer"
            className="text-muted hover:text-ink w-fit"
          >
            GitHub repository
          </a>
        </nav>
      </Container>

      <Container className="border-line/60 text-faint flex flex-col gap-1 border-t py-5 text-xs sm:flex-row sm:justify-between">
        <p>© {CURRENT_YEAR} PolyglotMesh</p>
        <p>Week 1 · frontend under active development</p>
      </Container>
    </footer>
  )
}
