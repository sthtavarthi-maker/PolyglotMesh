type SectionHeadingProps = {
  eyebrow: string
  title: string
  description?: string
  align?: 'left' | 'center'
}

/**
 * Shared section header: small eyebrow label, headline and optional
 * supporting paragraph. Keeps vertical rhythm identical across sections.
 */
export function SectionHeading({
  eyebrow,
  title,
  description,
  align = 'left',
}: SectionHeadingProps) {
  const alignment =
    align === 'center' ? 'mx-auto text-center items-center' : 'items-start'

  return (
    <div className={`flex max-w-2xl flex-col gap-3 ${alignment}`}>
      <p className="text-brand font-mono text-xs tracking-[0.16em] uppercase">
        {eyebrow}
      </p>
      <h2 className="text-2xl font-semibold tracking-tight text-balance sm:text-3xl">
        {title}
      </h2>
      {description ? (
        <p className="text-muted text-base leading-relaxed">{description}</p>
      ) : null}
    </div>
  )
}
