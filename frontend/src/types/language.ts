export type LanguageId =
  | 'java'
  | 'python'
  | 'javascript'
  | 'ruby'
  | 'c'
  | 'cpp'
  | 'rust'
  | 'go'
  | 'r'

export type Language = {
  id: LanguageId
  /** Display name, e.g. "JavaScript". */
  name: string
  /**
   * Identifier Monaco uses to tokenise this language, e.g. "cpp".
   *
   * Kept separate from `id` because the two namespaces are not identical in
   * practice (C and C++ are both served by Monaco's `cpp` tokenizer family).
   */
  monacoLanguage: string
  /** File name shown in the editor pane; carries the language's extension. */
  fileName: string
  /**
   * Runtime that will execute this language, or null while it is editor-only.
   *
   * This is a description of the execution target, not proof that execution
   * works: the backend is not connected yet, so every language keeps Run
   * disabled in the workspace.
   */
  runtime: string | null
  /** One-line description used on cards. */
  description: string
  /** Indentation width for this language. */
  tabSize: number
  /** Whether Tab inserts spaces. Go formatting conventions use real tabs. */
  insertSpaces: boolean
  /** Default program loaded when this language is selected. */
  starterCode: string
}
