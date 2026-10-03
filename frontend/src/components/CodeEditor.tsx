import Editor from "@monaco-editor/react"

interface CodeEditorProps {
  code: string
  onCodeChange: (code: string) => void
}

function CodeEditor({
  code,
  onCodeChange,
}: CodeEditorProps) {
  return (
    <Editor
      height="500px"
      defaultLanguage="cpp"
      theme="vs-dark"
      value={code}
      onChange={(value) => onCodeChange(value ?? "")}
      options={{
        minimap: {
          enabled: false,
        },
        fontSize: 14,
        automaticLayout: true,
      }}
    />
  )
}

export default CodeEditor