import { Link, NavLink } from 'react-router-dom'
import { BrandMark } from '../BrandMark'
import { Container } from './Container'

const NAV_ITEMS = [
  { to: '/', label: 'Home', end: true },
  { to: '/workspace', label: 'Workspace', end: false },
]

export function SiteHeader() {
  return (
    <header className="bg-base/85 border-line/80 sticky top-0 z-40 border-b backdrop-blur">
      <Container className="flex h-16 items-center justify-between gap-4">
        <Link
          to="/"
          className="text-ink flex items-center gap-2.5 rounded-lg text-[0.95rem] font-semibold tracking-tight"
        >
          <BrandMark className="size-8" />
          <span>PolyglotMesh</span>
        </Link>

        <nav aria-label="Primary" className="flex items-center gap-1">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                [
                  'rounded-lg px-3 py-1.5 text-sm transition-colors',
                  isActive
                    ? 'bg-raised text-ink'
                    : 'text-muted hover:text-ink hover:bg-raised/60',
                ].join(' ')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </Container>
    </header>
  )
}
