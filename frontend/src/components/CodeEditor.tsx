import Editor, { type OnMount } from "@monaco-editor/react"
import type { editor } from "monaco-editor"
import { useEffect, useImperativeHandle, useRef, type Ref } from "react"
import type { RemoteCursor } from "../hooks/useCodeSync"
import { editsFromOperation, operationFromChanges } from "../ot/monacoAdapter"
import type { TextOperation } from "../ot/textOperation"
import { colorClassRules, colorFor, colorIndex } from "../presence/colors"
import { languageFor } from "../editor/language"
import type { RoomDocument } from "../services/documentService"
import "./RemoteCursors.css"

export interface CodeEditorHandle {
  applyRemote: (documentId: string, operation: TextOperation) => void
  reset: (documents: RoomDocument[]) => void
  // The text this tab currently shows, including edits not yet confirmed by the server
  getContent: (documentId: string) => string | null
  setRemoteCursor: (cursor: RemoteCursor) => void
  // Removes the cursors of tabs that are no longer in the room
  retainRemoteCursors: (clientIds: Set<string>) => void
}

interface CodeEditorProps {
  documents: RoomDocument[]
  activeDocumentId: string
  readOnly: boolean
  onLocalOperation: (documentId: string, operation: TextOperation) => void
  onCursorChange: (documentId: string, anchor: number, head: number) => void
  onReady: () => void
  ref?: Ref<CodeEditorHandle>
}

// A remote tab's selection is a decoration on the model of the file it is in. Monaco moves
// decorations along with every edit, so they stay in place between cursor updates.
// The name label is a content widget that follows the caret decoration.
interface RemoteCursorView {
  documentId: string
  decorationIds: string[]
  // True when the caret is at the start of the selection
  backwards: boolean
  widget: editor.IContentWidget
}

function installColorClasses() {
  if (document.getElementById("remote-cursor-colors")) return
  const style = document.createElement("style")
  style.id = "remote-cursor-colors"
  style.textContent = colorClassRules()
  document.head.appendChild(style)
}

type RemoteCursorViews = Map<string, RemoteCursorView>

function removeRemoteCursor(
  views: RemoteCursorViews,
  models: Map<string, editor.ITextModel>,
  editorInstance: editor.IStandaloneCodeEditor | null,
  clientId: string
) {
  const view = views.get(clientId)
  if (!view) return
  models.get(view.documentId)?.deltaDecorations(view.decorationIds, [])
  editorInstance?.removeContentWidget(view.widget)
  views.delete(clientId)
}

// Monaco positions labels only when asked, so this runs after anything that moves text
function layoutLabels(editorInstance: editor.IStandaloneCodeEditor | null, views: RemoteCursorViews) {
  if (!editorInstance) return
  for (const view of views.values()) editorInstance.layoutContentWidget(view.widget)
}

function selectionOffsets(model: editor.ITextModel, selection: {
  selectionStartLineNumber: number
  selectionStartColumn: number
  positionLineNumber: number
  positionColumn: number
}): [number, number] {
  return [
    model.getOffsetAt({ lineNumber: selection.selectionStartLineNumber, column: selection.selectionStartColumn }),
    model.getOffsetAt({ lineNumber: selection.positionLineNumber, column: selection.positionColumn }),
  ]
}


function CodeEditor({
  documents,
  activeDocumentId,
  readOnly,
  onLocalOperation,
  onCursorChange,
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
  const remoteCursorsRef = useRef(new Map<string, RemoteCursorView>())

  const callbacksRef = useRef({ onLocalOperation, onCursorChange, onReady })
  useEffect(() => {
    callbacksRef.current = { onLocalOperation, onCursorChange, onReady }
  })

  const removeCursor = (clientId: string) =>
    removeRemoteCursor(remoteCursorsRef.current, modelsRef.current, editorRef.current, clientId)

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

    setRemoteCursor({ clientId, username, documentId, anchor, head }) {
      const model = modelsRef.current.get(documentId)
      const editorInstance = editorRef.current
      if (!model || !editorInstance) return

      const length = model.getValueLength()
      const anchorPosition = model.getPositionAt(Math.min(anchor, length))
      const headPosition = model.getPositionAt(Math.min(head, length))
      const color = `remote-color-${colorIndex(username)}`
      const backwards = head < anchor
      const [start, end] = backwards ? [headPosition, anchorPosition] : [anchorPosition, headPosition]

      const decorations: editor.IModelDeltaDecoration[] = [{
        range: {
          startLineNumber: start.lineNumber,
          startColumn: start.column,
          endLineNumber: end.lineNumber,
          endColumn: end.column,
        },
        options: {
          className: `remote-selection ${color}`,
          // The caret sits at the head end of the selection
          [backwards ? "beforeContentClassName" : "afterContentClassName"]: `remote-caret ${color}`,
          hoverMessage: { value: username },
          // A caret moves forward when text is typed at it, the same as transformIndex and as
          // that user's own caret. A selection does not grow to include text typed at its edges.
          stickiness: anchor === head ? 3 : 1, // GrowsOnlyWhenTypingAfter : NeverGrowsWhenTypingAtEdges
        },
      }]

      const existing = remoteCursorsRef.current.get(clientId)
      if (existing && existing.documentId !== documentId) removeCursor(clientId)

      const view = remoteCursorsRef.current.get(clientId)
      if (view) {
        view.decorationIds = model.deltaDecorations(view.decorationIds, decorations)
        view.backwards = backwards
        editorInstance.layoutContentWidget(view.widget)
        return
      }

      const label = document.createElement("div")
      label.className = "remote-cursor-label"
      label.textContent = username
      label.style.backgroundColor = colorFor(username)

      const created: RemoteCursorView = {
        documentId,
        decorationIds: model.deltaDecorations([], decorations),
        backwards,
        widget: {
          getId: () => `remote-cursor-${clientId}`,
          getDomNode: () => label,
          getPosition: () => {
            // The label is only shown in the file the cursor is in
            if (editorInstance.getModel() !== model) return null
            const range = model.getDecorationRange(created.decorationIds[0])
            if (!range) return null
            const caret = created.backwards ? range.getStartPosition() : range.getEndPosition()
            return { position: caret, preference: [1] } // ContentWidgetPositionPreference.ABOVE
          },
        },
      }
      remoteCursorsRef.current.set(clientId, created)
      editorInstance.addContentWidget(created.widget)
    },

    getContent(documentId) {
      return modelsRef.current.get(documentId)?.getValue() ?? null
    },

    retainRemoteCursors(clientIds) {
      for (const clientId of [...remoteCursorsRef.current.keys()]) {
        if (!clientIds.has(clientId)) removeCursor(clientId)
      }
    },

    reset(freshDocuments) {
      // Replacing the text loses where everyone was; their tabs send it again after the reload
      for (const clientId of [...remoteCursorsRef.current.keys()]) removeCursor(clientId)

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

      // setValue moved our caret without a selection event, so report where it is now
      const editorInstance = editorRef.current
      const model = editorInstance?.getModel()
      const selection = editorInstance?.getSelection()
      if (model && selection) {
        callbacksRef.current.onCursorChange(activeRef.current, ...selectionOffsets(model, selection))
      }
    },
  }), [])

  const handleMount: OnMount = (editorInstance, monaco) => {
    editorRef.current = editorInstance
    installColorClasses()

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

    // Labels are positioned by Monaco, which needs asking again after text moves them
    editorInstance.onDidChangeModelContent(() => layoutLabels(editorInstance, remoteCursorsRef.current))

    editorInstance.onDidChangeCursorSelection((event) => {
      // A remote edit shifting our caret is not a move; other tabs work that out themselves
      if (applyingRemoteRef.current) return

      const model = editorInstance.getModel()
      if (!model) return

      callbacksRef.current.onCursorChange(activeRef.current, ...selectionOffsets(model, event.selection))
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
    layoutLabels(editorInstance, remoteCursorsRef.current)
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
