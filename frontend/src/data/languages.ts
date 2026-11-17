import type { Language, LanguageId } from '../types/language'

/**
 * Single source of truth for languages the frontend can edit.
 *
 * The starter programs are the ones the editor loads and the ones the landing
 * page illustrates, so they live here instead of being duplicated.
 *
 * Editor support and execution support are deliberately not the same thing:
 * `runtime` is null for languages that only have an editor mode today. Nothing
 * executes while the backend is not connected, whatever `runtime` says.
 */
export const LANGUAGES: Language[] = [
  {
    id: 'java',
    name: 'Java',
    monacoLanguage: 'java',
    fileName: 'Main.java',
    runtime: 'GraalVM JDK 21',
    description:
      'Compile and run Java 21 on the GraalVM JDK, using the standard toolchain.',
    tabSize: 4,
    insertSpaces: true,
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
    monacoLanguage: 'python',
    fileName: 'main.py',
    runtime: 'GraalVM Polyglot',
    description:
      'Run Python on GraalPy inside the same JVM as the rest of the platform.',
    tabSize: 4,
    insertSpaces: true,
    starterCode: `print("Hello PolyglotMesh!")
`,
  },
  {
    id: 'javascript',
    name: 'JavaScript',
    monacoLanguage: 'javascript',
    fileName: 'main.js',
    runtime: 'GraalVM Polyglot',
    description:
      'Execute JavaScript on GraalJS with no separate runtime to install.',
    tabSize: 2,
    insertSpaces: true,
    starterCode: `console.log("Hello PolyglotMesh!");
`,
  },
  {
    id: 'ruby',
    name: 'Ruby',
    monacoLanguage: 'ruby',
    fileName: 'main.rb',
    runtime: null,
    description:
      'Ruby editing with string interpolation, block and comment highlighting.',
    tabSize: 2,
    insertSpaces: true,
    starterCode: `name = "PolyglotMesh"
puts "Hello from #{name}!"
`,
  },
  {
    id: 'c',
    name: 'C',
    monacoLanguage: 'c',
    fileName: 'main.c',
    runtime: null,
    description:
      'C editing with preprocessor directives, types and comment highlighting.',
    tabSize: 4,
    insertSpaces: true,
    starterCode: `#include <stdio.h>

int main() {
    printf("Hello from PolyglotMesh!\\n");
    return 0;
}
`,
  },
  {
    id: 'cpp',
    name: 'C++',
    monacoLanguage: 'cpp',
    fileName: 'main.cpp',
    runtime: null,
    description:
      'C++ editing with includes, streams and comment highlighting.',
    tabSize: 4,
    insertSpaces: true,
    starterCode: `#include <iostream>
using namespace std;

int main() {
    cout << "Hello from PolyglotMesh!" << endl;
    return 0;
}
`,
  },
  {
    id: 'rust',
    name: 'Rust',
    monacoLanguage: 'rust',
    fileName: 'main.rs',
    runtime: null,
    description:
      'Rust editing with macros, lifetimes and comment highlighting.',
    tabSize: 4,
    insertSpaces: true,
    starterCode: `fn main() {
    println!("Hello from PolyglotMesh!");
}
`,
  },
  {
    id: 'go',
    name: 'Go',
    monacoLanguage: 'go',
    fileName: 'main.go',
    runtime: null,
    description:
      'Go editing with packages, imports and gofmt-style tab indentation.',
    tabSize: 4,
    insertSpaces: false,
    starterCode: `package main

import "fmt"

func main() {
    fmt.Println("Hello from PolyglotMesh!")
}
`,
  },
  {
    id: 'r',
    name: 'R',
    monacoLanguage: 'r',
    fileName: 'main.r',
    runtime: null,
    description:
      'R editing with assignment, vector and comment highlighting.',
    tabSize: 2,
    insertSpaces: true,
    starterCode: `message <- "Hello from PolyglotMesh!"
print(message)
`,
  },
]

export const DEFAULT_LANGUAGE_ID = LANGUAGES[0].id

export function getLanguage(id: LanguageId): Language {
  return LANGUAGES.find((language) => language.id === id) ?? LANGUAGES[0]
}
