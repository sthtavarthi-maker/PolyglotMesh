import { Container } from '../layout/Container'
import { SectionHeading } from './SectionHeading'

const STEPS = [
  {
    title: 'Choose a language',
    description:
      'Pick Java, Python or JavaScript. The editor loads a working starter program for that language.',
  },
  {
    title: 'Write your program',
    description:
      'Use syntax highlighting, line numbers and automatic indentation. Add any input the program reads from stdin.',
  },
  {
    title: 'Run, save, share',
    description:
      'Send the program to the backend and read the output. Save the snippet to your account or share it with a link.',
  },
]

export function HowItWorksSection() {
  return (
    <section
      id="how-it-works"
      className="border-line/60 scroll-mt-20 border-y py-16 sm:py-20"
    >
      <Container>
        <SectionHeading
          eyebrow="How it works"
          title="From empty editor to shared snippet"
          description="Three steps, the same for every language."
          align="center"
        />

        <ol className="mt-12 grid gap-4 md:grid-cols-3">
          {STEPS.map((step, index) => (
            <li
              key={step.title}
              className="border-line bg-surface rounded-panel relative border p-6"
            >
              <span className="border-line bg-raised text-brand grid size-8 place-items-center rounded-lg border font-mono text-sm">
                {index + 1}
              </span>
              <h3 className="text-ink mt-4 text-base font-semibold">
                {step.title}
              </h3>
              <p className="text-muted mt-2 text-sm leading-relaxed">
                {step.description}
              </p>
            </li>
          ))}
        </ol>

        <p className="text-faint mt-8 text-center text-xs">
          Execution is still being connected. Until then the workspace runs in
          interface-only mode.
        </p>
      </Container>
    </section>
  )
}
