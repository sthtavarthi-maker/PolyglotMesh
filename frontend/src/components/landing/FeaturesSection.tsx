import type { ReactNode } from 'react'
import { Container } from '../layout/Container'
import { SectionHeading } from './SectionHeading'

type Feature = {
  title: string
  description: string
  icon: ReactNode
}

const ICON_CLASS = 'size-5'

const FEATURES: Feature[] = [
  {
    title: 'Three languages, one editor',
    description:
      'Switch between Java, Python and JavaScript without leaving the page. Each mode brings its own starter program.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <path
          d="m8 6-5 6 5 6M16 6l5 6-5 6"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    title: 'One JVM behind everything',
    description:
      'Java runs on the GraalVM JDK; Python and JavaScript run through the Polyglot API in the same process. No external judge.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <rect
          x="7"
          y="7"
          width="10"
          height="10"
          rx="2"
          stroke="currentColor"
          strokeWidth="1.6"
        />
        <path
          d="M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
        />
      </svg>
    ),
  },
  {
    title: 'Input next to output',
    description:
      'Provide stdin for programs that read input and read the result in a dedicated panel, without a terminal.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <rect
          x="3"
          y="4"
          width="18"
          height="16"
          rx="2"
          stroke="currentColor"
          strokeWidth="1.6"
        />
        <path
          d="m7 9 3 3-3 3M13 15h4"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    title: 'Save and share snippets',
    description:
      'Keep programs in your account and send a working snippet to someone with a single link.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <path
          d="M12 3v11m0 0 4-4m-4 4-4-4M5 17v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    title: 'Light and dark editing',
    description:
      'Choose the editor theme that suits your screen. The surrounding interface keeps a consistent contrast.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <circle cx="12" cy="12" r="8" stroke="currentColor" strokeWidth="1.6" />
        <path d="M12 4a8 8 0 0 1 0 16V4Z" fill="currentColor" opacity="0.35" />
      </svg>
    ),
  },
  {
    title: 'Accounts you can trust',
    description:
      'Spring Security with JWT protects saved snippets. Passwords are hashed and tokens are short-lived.',
    icon: (
      <svg viewBox="0 0 24 24" className={ICON_CLASS} fill="none" aria-hidden="true">
        <path
          d="M12 3l7 3v5.5c0 4.2-2.8 7.7-7 9.5-4.2-1.8-7-5.3-7-9.5V6l7-3Z"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinejoin="round"
        />
        <path
          d="m9 12 2 2 4-4"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
]

export function FeaturesSection() {
  return (
    <section id="features" className="scroll-mt-20 py-16 sm:py-20">
      <Container>
        <SectionHeading
          eyebrow="Platform features"
          title="Built for quick experiments, not just one demo"
          description="Everything on this page exists to shorten the path between an idea and a working program."
        />

        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => (
            <li
              key={feature.title}
              className="border-line bg-surface rounded-panel border p-5 transition-colors hover:border-line/80 hover:bg-raised/40"
            >
              <span className="border-line bg-raised text-brand grid size-9 place-items-center rounded-lg border">
                {feature.icon}
              </span>
              <h3 className="text-ink mt-4 text-base font-semibold">
                {feature.title}
              </h3>
              <p className="text-muted mt-2 text-sm leading-relaxed">
                {feature.description}
              </p>
            </li>
          ))}
        </ul>
      </Container>
    </section>
  )
}
