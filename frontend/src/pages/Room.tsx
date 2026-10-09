import { useCallback, useEffect, useRef, useState } from "react"
import { useParams, Link } from "react-router-dom"

import {
  getRoom,
  type Room as RoomType,
} from "../services/roomService"
import { getDocuments, type RoomDocument } from "../services/documentService"
import { formatResult, runDocument } from "../services/executionService"

import RoomHeader from "../components/RoomHeader"
import FileExplorer from "../components/FileExplorer"
import CodeEditor, { type CodeEditorHandle } from "../components/CodeEditor"
import OutputPanel from "../components/OutputPanel"

import useCodeSync from "../hooks/useCodeSync"
import type { TextOperation } from "../ot/textOperation"

function Room() {
  const { roomId } = useParams<{ roomId: string }>()

  const [room, setRoom] = useState<RoomType | null>(null)
  const [documents, setDocuments] = useState<RoomDocument[] | null>(null)
  const [activeDocumentId, setActiveDocumentId] = useState("")
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [editorReady, setEditorReady] = useState(false)
  const [output, setOutput] = useState("No output yet.")
  const [running, setRunning] = useState(false)

  const editorRef = useRef<CodeEditorHandle>(null)
  // Only the newest reload may apply its result
  const resyncRequestRef = useRef(0)

  useEffect(() => {
    const fetchRoomAndDocuments = async () => {
      if (!roomId) {
        setError("Room ID is missing")
        setLoading(false)
        return
      }

      try {
        const [roomData, docs] = await Promise.all([
          getRoom(roomId),
          getDocuments(roomId),
        ])

        setRoom(roomData)
        setDocuments(docs)
        setActiveDocumentId(docs[0]?.id ?? "")
      } catch (error) {
        setError(
          error instanceof Error
            ? error.message
            : "Failed to load room"
        )
      } finally {
        setLoading(false)
      }
    }

    fetchRoomAndDocuments()
  }, [roomId])

  const resync = useCallback(async (reason: string) => {
    if (!roomId) return

    const request = ++resyncRequestRef.current
    console.info(`CodeSync: loading latest documents (${reason})`)

    try {
      const fresh = await getDocuments(roomId)
      if (request !== resyncRequestRef.current) return

      editorRef.current?.reset(fresh)
      setDocuments(fresh)
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to reload documents")
    }
  }, [roomId])

  const handleRemoteOperation = useCallback((documentId: string, operation: TextOperation) => {
    editorRef.current?.applyRemote(documentId, operation)
  }, [])

  const handleResyncNeeded = useCallback((reason: string) => {
    void resync(reason)
  }, [resync])

  const handleEditorReady = useCallback(() => setEditorReady(true), [])

  const { ready, applyLocalOperation } = useCodeSync({
    roomId: roomId ?? "",
    documents,
    enabled: editorReady,
    onRemoteOperation: handleRemoteOperation,
    onResyncNeeded: handleResyncNeeded,
  })

  const handleRun = async () => {
    if (!roomId || !activeDocumentId) return

    setRunning(true)
    setOutput("Compiling and running...")
    try {
      setOutput(formatResult(await runDocument(roomId, activeDocumentId)))
    } catch (error) {
      setOutput(error instanceof Error ? error.message : "Run failed")
    } finally {
      setRunning(false)
    }
  }

  if (loading) {
    return <p>Loading room...</p>
  }

  if (error) {
    return (
      <div>
        <h1>Unable to open room</h1>
        <p>{error}</p>
        <Link to="/dashboard">Back to Dashboard</Link>
      </div>
    )
  }

  if (!room || !documents) {
    return <p>Room not found</p>
  }

  const activeDocument = documents.find((doc) => doc.id === activeDocumentId)

  return (
    <div>
      <RoomHeader
        roomName={room.name}
        roomId={room.id}
      />

      <div>
        <FileExplorer
          files={documents.map((doc) => doc.fileName)}
          activeFile={activeDocument?.fileName ?? ""}
          onFileSelect={(fileName) => {
            const doc = documents.find((d) => d.fileName === fileName)
            if (doc) setActiveDocumentId(doc.id)
          }}
        />

        <main>
          {activeDocument ? (
            <>
              <h3>
                {activeDocument.fileName}
                {ready ? "" : " (connecting...)"}
              </h3>

              <CodeEditor
                ref={editorRef}
                documents={documents}
                activeDocumentId={activeDocumentId}
                readOnly={!ready}
                onLocalOperation={applyLocalOperation}
                onReady={handleEditorReady}
              />
            </>
          ) : (
            <p>This room has no files yet.</p>
          )}

          <OutputPanel output={output} />
        </main>
      </div>

      <button
        type="button"
        onClick={handleRun}
        disabled={running || !ready || !activeDocument}
      >
        {running ? "Running..." : "Run Code"}
      </button>

      <br />

      <Link to="/dashboard">
        Back to Dashboard
      </Link>
    </div>
  )
}

export default Room
