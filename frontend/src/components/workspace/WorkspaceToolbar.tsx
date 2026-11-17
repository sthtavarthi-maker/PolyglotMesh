import { getLanguage } from '../../data/languages'
import type { LanguageId } from '../../types/language'
import type { EditorTheme } from '../../types/workspace'
import { LanguageSelect } from './LanguageSelect'
import { ThemeSelect } from './ThemeSelect'

type WorkspaceToolbarProps = {
  languageId: LanguageId
  onLanguageChange: (id: LanguageId) => void
  theme: EditorTheme
  onThemeChange: (theme: EditorTheme) => void
}

const NOTE_ID = 'execution-note'
const DISABLED_REASON =
  'Available once the execution service is connected'

/*
 * Action buttons are rendered disabled because no backend exists yet. The Run
 * button uses neutral styling rather than the brand colour on purpose: a bright
 * button would look runnable. Swap to the brand styles when execution lands.
 */
const secondaryButton =
  'inline-flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-sm font-medium text-muted opacity-55 cursor-not-allowed'
const primaryButton =
  'inline-flex items-center gap-1.5 rounded-lg border border-line bg-raised px-4 py-1.5 text-sm font-medium text-faint cursor-not-allowed'

/**
 * Compiler navigation bar: language, theme and run actions.
 *
 * The action buttons are intentionally disabled. There is no backend yet, so
 * enabling them would mean faking a run, a save or a share.
 */
export function WorkspaceToolbar({
  languageId,
  onLanguageChange,
  theme,
  onThemeChange,
}: WorkspaceToolbarProps) {
  const language = getLanguage(languageId)

  return (
    <div className="border-line bg-surface/70 rounded-panel border">
      <div className="flex flex-wrap items-center justify-between gap-3 p-2.5">
        <div className="flex items-center gap-2">
          <LanguageSelect value={languageId} onChange={onLanguageChange} />
          <ThemeSelect value={theme} onChange={onThemeChange} />
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled
            title={DISABLED_REASON}
            aria-describedby={NOTE_ID}
            className={secondaryButton}
          >
            <svg
              viewBox="0 0 24 24"
              className="size-4"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M12 3v11m0 0 4-4m-4 4-4-4M5 17v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            Save
          </button>

          <button
            type="button"
            disabled
            title={DISABLED_REASON}
            aria-describedby={NOTE_ID}
            className={secondaryButton}
          >
            <svg
              viewBox="0 0 24 24"
              className="size-4"
              fill="none"
              aria-hidden="true"
            >
              <circle cx="6" cy="12" r="2.5" stroke="currentColor" strokeWidth="1.6" />
              <circle cx="18" cy="6" r="2.5" stroke="currentColor" strokeWidth="1.6" />
              <circle cx="18" cy="18" r="2.5" stroke="currentColor" strokeWidth="1.6" />
              <path
                d="m8.2 10.9 7.6-3.8M8.2 13.1l7.6 3.8"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
            </svg>
            Share
          </button>

          <button
            type="button"
            disabled
            title={DISABLED_REASON}
            aria-describedby={NOTE_ID}
            className={primaryButton}
          >
            <svg
              viewBox="0 0 24 24"
              className="size-4"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M8 5.5v13l10-6.5-10-6.5Z"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinejoin="round"
              />
            </svg>
            Run
          </button>
        </div>
      </div>

      <p
        id={NOTE_ID}
        className="border-line/70 text-faint border-t px-3 py-2 text-[11px]"
      >
        {language.runtime === null
          ? `${language.name} is editor-only: no runtime is integrated for it yet. Execution is not connected, so Run, Save and Share stay disabled.`
          : 'Execution service not connected. Run, Save and Share stay disabled until the GraalVM backend is available.'}
      </p>
    </div>
  )
}
