import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Container } from '../components/layout/Container'
import { EditorPane } from '../components/workspace/EditorPane'
import { InputPanel } from '../components/workspace/InputPanel'
import { OutputPanel } from '../components/workspace/OutputPanel'
import { WorkspaceToolbar } from '../components/workspace/WorkspaceToolbar'
import {
  DEFAULT_LANGUAGE_ID,
  LANGUAGES,
  getLanguage,
} from '../data/languages'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import type { LanguageId } from '../types/language'
import type { EditorTheme } from '../types/workspace'

/**
 * Drafts are kept per language so switching the language selector does not
 * throw away work in progress. Each language starts from its starter program.
 */
function createInitialDrafts(): Record<LanguageId, string> {
  return LANGUAGES.reduce(
    (drafts, language) => {
      drafts[language.id] = language.starterCode
      return drafts
    },
    {} as Record<LanguageId, string>,
  )
}

export function WorkspacePage() {
  useDocumentTitle('Workspace')

  const [languageId, setLanguageId] = useState<LanguageId>(DEFAULT_LANGUAGE_ID)
  const [theme, setTheme] = useState<EditorTheme>('dark')
  const [drafts, setDrafts] = useState(createInitialDrafts)
  const [stdin, setStdin] = useState('')

  const language = getLanguage(languageId)
  const code = drafts[languageId]

  function updateCode(next: string) {
    setDrafts((current) => ({ ...current, [languageId]: next }))
  }

  return (
    <Container className="py-6 sm:py-8">
      <header className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <nav aria-label="Breadcrumb" className="text-faint mb-1 text-xs">
            <Link to="/" className="hover:text-ink">
              Home
            </Link>
            <span className="mx-1.5">/</span>
            <span className="text-muted">Workspace</span>
          </nav>
          <h1 className="text-2xl font-semibold tracking-tight">
            Compiler workspace
          </h1>
        </div>
        <p className="text-faint text-xs">
          Java, Python and JavaScript · one editor
        </p>
      </header>

      <WorkspaceToolbar
        languageId={languageId}
        onLanguageChange={setLanguageId}
        theme={theme}
        onThemeChange={setTheme}
      />

      <div
        data-theme={theme}
        className="code-surface mt-4 grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)]"
      >
        <EditorPane
          language={language}
          code={code}
          theme={theme}
          onChange={updateCode}
        />

        <div className="flex min-w-0 flex-col gap-4">
          <InputPanel value={stdin} onChange={setStdin} />
          <OutputPanel />
        </div>
      </div>
    </Container>
  )
}
