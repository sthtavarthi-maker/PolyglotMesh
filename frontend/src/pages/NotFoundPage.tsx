import { Link } from 'react-router-dom'
import { Container } from '../components/layout/Container'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')

  return (
    <Container className="py-20 sm:py-28">
      <div className="max-w-lg">
        <p className="text-brand font-mono text-sm">404</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">
          This page does not exist
        </h1>
        <p className="text-muted mt-3 text-sm leading-relaxed">
          The link may be mistyped or the page may not have been built yet.
        </p>
        <Link
          to="/"
          className="bg-brand-strong hover:bg-brand-deep mt-8 inline-block rounded-lg px-5 py-2.5 text-sm font-medium text-white transition-colors"
        >
          Back to home
        </Link>
      </div>
    </Container>
  )
}
