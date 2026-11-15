import { LANGUAGES } from '../../data/languages'
import type { LanguageId } from '../../types/language'

type LanguageSelectProps = {
  value: LanguageId
  onChange: (id: LanguageId) => void
}

/**
 * Native select on purpose: it is keyboard and screen-reader accessible by
 * default and keeps the bundle free of a popover dependency.
 */
export function LanguageSelect({ value, onChange }: LanguageSelectProps) {
  return (
    <label className="relative flex items-center">
      <span className="sr-only">Programming language</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value as LanguageId)}
        className="border-line bg-raised text-ink focus-visible:outline-brand appearance-none rounded-lg border py-1.5 pr-8 pl-3 text-sm font-medium"
      >
        {LANGUAGES.map((language) => (
          <option key={language.id} value={language.id}>
            {language.name}
          </option>
        ))}
      </select>
      <svg
        viewBox="0 0 16 16"
        className="text-faint pointer-events-none absolute right-2.5 size-3.5"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="m4 6 4 4 4-4"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </label>
  )
}
