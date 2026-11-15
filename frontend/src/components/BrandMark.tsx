type BrandMarkProps = {
  className?: string
}

/**
 * PolyglotMesh mesh mark. Decorative by default: always render it next to the
 * "PolyglotMesh" wordmark, which carries the accessible name.
 */
export function BrandMark({ className = 'size-8' }: BrandMarkProps) {
  return (
    <svg
      viewBox="0 0 32 32"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <rect width="32" height="32" rx="7" fill="#0f172a" />
      <path
        d="M10 11.5 16 8l6 3.5v9L16 24l-6-3.5v-9Z"
        fill="none"
        stroke="#4f8cff"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M10 11.5 16 15l6-3.5M16 15v9"
        fill="none"
        stroke="#34d3b5"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <circle cx="16" cy="15" r="2" fill="#93c5fd" />
    </svg>
  )
}
