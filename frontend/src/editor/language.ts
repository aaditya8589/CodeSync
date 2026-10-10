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
