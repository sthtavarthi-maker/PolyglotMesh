import { Link } from 'react-router-dom'
import { Container } from '../layout/Container'

export function CtaSection() {
  return (
    <section className="py-16 sm:py-20">
      <Container>
        <div className="border-line bg-surface rounded-panel relative overflow-hidden border px-6 py-10 sm:px-10 sm:py-12">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(40rem_18rem_at_20%_0%,rgba(52,211,181,0.12),transparent_70%)]"
          />
          <div className="relative flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="max-w-xl">
              <h2 className="text-2xl font-semibold tracking-tight text-balance">
                Ready to write your first program?
              </h2>
              <p className="text-muted mt-2 text-sm leading-relaxed">
                The workspace route is live. Language selection, the editor and
                run controls are being added next.
              </p>
            </div>
            <Link
              to="/workspace"
              className="bg-brand-strong hover:bg-brand-deep w-fit rounded-lg px-5 py-2.5 text-sm font-medium whitespace-nowrap text-white transition-colors"
            >
              Open workspace
            </Link>
          </div>
        </div>
      </Container>
    </section>
  )
}
