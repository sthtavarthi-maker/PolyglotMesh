import { Link } from 'react-router-dom'
import { Container } from '../components/layout/Container'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * Route reserved for the compiler workspace. The real UI (language selector,
 * editor, input and output panels) is built on Days 6 and 7.
 */
export function WorkspacePage() {
  useDocumentTitle('Workspace')

  return (
    <Container className="py-12 sm:py-16">
      <header className="mb-8 space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Compiler workspace
        </h1>
        <p className="text-muted max-w-2xl text-sm leading-relaxed">
          This route is wired up and ready. The editor, input/output panels and
          run controls are added on Days 6 and 7.
        </p>
      </header>

      <div className="border-line bg-surface rounded-panel border border-dashed p-8 text-center">
        <p className="text-ink text-sm font-medium">Workspace UI not built yet</p>
        <p className="text-faint mx-auto mt-2 max-w-md text-sm">
          Nothing here pretends to execute code. Until the backend is
          integrated, the workspace contains no run button and no simulated
          output.
        </p>
        <Link
          to="/"
          className="text-brand mt-6 inline-block text-sm font-medium underline-offset-4 hover:underline"
        >
          Back to home
        </Link>
      </div>
    </Container>
  )
}
