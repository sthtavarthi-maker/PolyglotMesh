export type LanguageId = 'java' | 'python' | 'javascript'

export type Language = {
  id: LanguageId
  /** Display name, e.g. "JavaScript". */
  name: string
  /** File name shown in editor tabs, e.g. "Main.java". */
  fileName: string
  /** Execution engine, kept accurate to the project architecture. */
  runtime: string
  /** One-line description used on cards. */
  description: string
  /** Default program loaded when this language is selected. */
  starterCode: string
}
