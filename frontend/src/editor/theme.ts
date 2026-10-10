import type { Monaco } from "@monaco-editor/react"

export const EDITOR_THEME = "codesync-night"

// Matches the --editor / --line tokens in index.css, so the editor sits inside the page
// instead of looking like a grey box dropped onto it
export function defineEditorTheme(monaco: Monaco): void {
  monaco.editor.defineTheme(EDITOR_THEME, {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "comment", foreground: "6d7d96", fontStyle: "italic" },
      { token: "keyword", foreground: "8fb6ff" },
      { token: "string", foreground: "f0c38a" },
      { token: "number", foreground: "b9a2ff" },
      { token: "type", foreground: "7fd4d0" },
    ],
    colors: {
      "editor.background": "#131d2e",
      "editor.foreground": "#dce3ee",
      "editorLineNumber.foreground": "#4a5870",
      "editorLineNumber.activeForeground": "#a6b2c6",
      "editor.lineHighlightBackground": "#182439",
      "editor.lineHighlightBorder": "#182439",
      "editor.selectionBackground": "#2e4466",
      "editorCursor.foreground": "#f4b740",
      "editorIndentGuide.background1": "#22304a",
      "editorWidget.background": "#142033",
      "editorWidget.border": "#34445f",
      "diffEditor.insertedTextBackground": "#6fd39a26",
      "diffEditor.removedTextBackground": "#f2757a26",
      "diffEditor.insertedLineBackground": "#6fd39a14",
      "diffEditor.removedLineBackground": "#f2757a14",
      "scrollbarSlider.background": "#34445f80",
    },
  })
}

export const EDITOR_FONT = {
  fontFamily: '"JetBrains Mono", ui-monospace, Consolas, monospace',
  fontSize: 14,
  lineHeight: 22,
}
