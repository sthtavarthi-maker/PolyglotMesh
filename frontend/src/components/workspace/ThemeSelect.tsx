import type { EditorTheme } from '../../types/workspace'

type ThemeSelectProps = {
  value: EditorTheme
  onChange: (theme: EditorTheme) => void
}

const THEMES: { id: EditorTheme; label: string }[] = [
  { id: 'dark', label: 'Dark' },
  { id: 'light', label: 'Light' },
]

export function ThemeSelect({ value, onChange }: ThemeSelectProps) {
  return (
    <label className="relative flex items-center">
      <span className="sr-only">Editor theme</span>
      <svg
        viewBox="0 0 24 24"
        className="text-faint pointer-events-none absolute left-2.5 size-3.5"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="8" stroke="currentColor" strokeWidth="1.6" />
        <path d="M12 4a8 8 0 0 1 0 16V4Z" fill="currentColor" opacity="0.4" />
      </svg>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value as EditorTheme)}
        className="border-line bg-raised text-ink focus-visible:outline-brand appearance-none rounded-lg border py-1.5 pr-8 pl-8 text-sm font-medium"
      >
        {THEMES.map((theme) => (
          <option key={theme.id} value={theme.id}>
            {theme.label}
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
