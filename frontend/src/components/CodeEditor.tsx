import Editor, { type OnMount } from "@monaco-editor/react"
import type { editor } from "monaco-editor"
import { useEffect, useImperativeHandle, useRef, type Ref } from "react"
import { editsFromOperation, operationFromChanges } from "../ot/monacoAdapter"
import type { TextOperation } from "../ot/textOperation"
import type { RoomDocument } from "../services/documentService"

export interface CodeEditorHandle {
  applyRemote: (documentId: string, operation: TextOperation) => void
  reset: (documents: RoomDocument[]) => void
}

interface CodeEditorProps {
  documents: RoomDocument[]
  activeDocumentId: string
  readOnly: boolean
  onLocalOperation: (documentId: string, operation: TextOperation) => void
  onReady: () => void
  ref?: Ref<CodeEditorHandle>
}

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

function languageFor(fileName: string): string {
  return LANGUAGES[fileName.split(".").pop() ?? ""] ?? "plaintext"
}

function CodeEditor({
  documents,
  activeDocumentId,
  readOnly,
  onLocalOperation,
  onReady,
  ref,
}: CodeEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null)
  // One Monaco model per document, so each file keeps its own undo history
  const modelsRef = useRef(new Map<string, editor.ITextModel>())
  const viewStatesRef = useRef(new Map<string, editor.ICodeEditorViewState | null>())
  const activeRef = useRef(activeDocumentId)
  // Set while applying a remote edit, so it is not mistaken for local typing
  const applyingRemoteRef = useRef(false)

  const callbacksRef = useRef({ onLocalOperation, onReady })
  useEffect(() => {
    callbacksRef.current = { onLocalOperation, onReady }
  })

  useImperativeHandle(ref, () => ({
    applyRemote(documentId, operation) {
      const model = modelsRef.current.get(documentId)
      if (!model) return

      const edits = editsFromOperation(operation).map((edit) => {
        const start = model.getPositionAt(edit.offset)
        const end = model.getPositionAt(edit.offset + edit.length)
        return {
          range: {
            startLineNumber: start.lineNumber,
            startColumn: start.column,
            endLineNumber: end.lineNumber,
            endColumn: end.column,
          },
          text: edit.text,
        }
      })

      applyingRemoteRef.current = true
      try {
        // Monaco shifts cursors and selections around these edits, so local carets stay put
        model.pushEditOperations([], edits, () => null)
      } finally {
        applyingRemoteRef.current = false
      }
    },

    reset(freshDocuments) {
      for (const doc of freshDocuments) {
        const model = modelsRef.current.get(doc.id)
        if (!model || model.getValue() === doc.content) continue

        applyingRemoteRef.current = true
        try {
          model.setValue(doc.content)
        } finally {
          applyingRemoteRef.current = false
        }
      }
    },
  }), [])

  const handleMount: OnMount = (editorInstance, monaco) => {
    editorRef.current = editorInstance

    for (const doc of documents) {
      const uri = monaco.Uri.parse(`inmemory://codesync/${doc.id}`)
      monaco.editor.getModel(uri)?.dispose()
      modelsRef.current.set(doc.id, monaco.editor.createModel(doc.content, languageFor(doc.fileName), uri))
    }

    editorInstance.setModel(modelsRef.current.get(activeRef.current) ?? null)

    editorInstance.onDidChangeModelContent((event) => {
      if (applyingRemoteRef.current || event.isFlush) return

      const model = editorInstance.getModel()
      if (!model) return

      callbacksRef.current.onLocalOperation(
        activeRef.current,
        operationFromChanges(event.changes, model.getValueLength())
      )
    })

    callbacksRef.current.onReady()
  }

  useEffect(() => {
    const editorInstance = editorRef.current
    const previous = activeRef.current
    activeRef.current = activeDocumentId

    if (!editorInstance || previous === activeDocumentId) return

    viewStatesRef.current.set(previous, editorInstance.saveViewState())

    const model = modelsRef.current.get(activeDocumentId)
    if (!model) return

    editorInstance.setModel(model)
    const viewState = viewStatesRef.current.get(activeDocumentId)
    if (viewState) editorInstance.restoreViewState(viewState)
    editorInstance.focus()
  }, [activeDocumentId])

  return (
    <Editor
      height="500px"
      theme="vs-dark"
      onMount={handleMount}
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        automaticLayout: true,
        readOnly,
      }}
    />
  )
}

export default CodeEditor
