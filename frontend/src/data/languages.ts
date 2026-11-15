import type { Language, LanguageId } from '../types/language'

/**
 * Single source of truth for supported languages.
 *
 * The starter programs are the ones the editor loads (Day 7) and the ones the
 * landing page illustrates, so they live here instead of being duplicated.
 * Only languages that the backend can actually execute are listed.
 */
export const LANGUAGES: Language[] = [
  {
    id: 'java',
    name: 'Java',
    fileName: 'Main.java',
    runtime: 'GraalVM JDK 21',
    description:
      'Compile and run Java 21 on the GraalVM JDK, using the standard toolchain.',
    starterCode: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello PolyglotMesh!");
    }
}
`,
  },
  {
    id: 'python',
    name: 'Python',
    fileName: 'main.py',
    runtime: 'GraalVM Polyglot',
    description:
      'Run Python on GraalPy inside the same JVM as the rest of the platform.',
    starterCode: `print("Hello PolyglotMesh!")
`,
  },
  {
    id: 'javascript',
    name: 'JavaScript',
    fileName: 'main.js',
    runtime: 'GraalVM Polyglot',
    description:
      'Execute JavaScript on GraalJS with no separate runtime to install.',
    starterCode: `console.log("Hello PolyglotMesh!");
`,
  },
]

export const DEFAULT_LANGUAGE_ID = LANGUAGES[0].id

export function getLanguage(id: LanguageId): Language {
  return LANGUAGES.find((language) => language.id === id) ?? LANGUAGES[0]
}
