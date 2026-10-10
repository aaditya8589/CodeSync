const LANGUAGES: Record<string, string> = {
  cpp: "cpp",
  cc: "cpp",
  h: "cpp",
  hpp: "cpp",
  java: "java",
  py: "python",
  js: "javascript",
  ts: "typescript",
}

export function languageFor(fileName: string): string {
  return LANGUAGES[fileName.split(".").pop() ?? ""] ?? "plaintext"
}

const RUNNABLE: Record<string, string> = {
  cpp: "C++",
  cc: "C++",
  py: "Python",
  java: "Java",
}

const extensionOf = (fileName: string) => fileName.split(".").pop()?.toLowerCase() ?? ""

// Must match the languages the backend can run (Language.java)
export function isRunnable(fileName: string): boolean {
  return extensionOf(fileName) in RUNNABLE
}

export function runLabel(fileName: string): string {
  return RUNNABLE[extensionOf(fileName)] ?? "Code"
}
