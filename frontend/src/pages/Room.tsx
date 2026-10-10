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
import HistoryPanel from "../components/HistoryPanel"

import useCodeSync, { type RemoteCursor } from "../hooks/useCodeSync"
import type { TextOperation } from "../ot/textOperation"
import { colorFor } from "../presence/colors"

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
  const [stdin, setStdin] = useState("")
  const [historyOpen, setHistoryOpen] = useState(false)

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

  const handleRemoteCursor = useCallback((cursor: RemoteCursor) => {
    editorRef.current?.setRemoteCursor(cursor)
  }, [])

  const handleEditorReady = useCallback(() => setEditorReady(true), [])

  const { ready, members, applyLocalOperation, sendCursor } = useCodeSync({
    roomId: roomId ?? "",
    documents,
    enabled: editorReady,
    onRemoteOperation: handleRemoteOperation,
    onResyncNeeded: handleResyncNeeded,
    onRemoteCursor: handleRemoteCursor,
  })

  // Drop the cursors of tabs that have left
  useEffect(() => {
    editorRef.current?.retainRemoteCursors(new Set(members.flatMap((member) => member.clientIds)))
  }, [members])

  const handleRun = async () => {
    if (!roomId || !activeDocumentId) return

    setRunning(true)
    setOutput("Compiling and running...")
    try {
      setOutput(formatResult(await runDocument(roomId, activeDocumentId, stdin)))
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

      <ul aria-label="People in this room" style={{ listStyle: "none", padding: 0, display: "flex", gap: 16 }}>
        {members.map((member) => (
          <li key={member.username}>
            <span
              style={{
                display: "inline-block",
                width: 10,
                height: 10,
                borderRadius: "50%",
                marginRight: 6,
                backgroundColor: colorFor(member.username),
              }}
            />
            {member.username}
            {member.clientIds.length > 1 ? ` (${member.clientIds.length} tabs)` : ""}
          </li>
        ))}
      </ul>

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
                {ready ? "" : " (connecting...)"}{" "}
                <button type="button" onClick={() => setHistoryOpen(true)} disabled={!ready}>
                  History
                </button>
              </h3>

              <CodeEditor
                ref={editorRef}
                documents={documents}
                activeDocumentId={activeDocumentId}
                readOnly={!ready}
                onLocalOperation={applyLocalOperation}
                onCursorChange={sendCursor}
                onReady={handleEditorReady}
              />
            </>
          ) : (
            <p>This room has no files yet.</p>
          )}

          {historyOpen && activeDocument && (
            <HistoryPanel
              roomId={room.id}
              documentId={activeDocument.id}
              fileName={activeDocument.fileName}
              getCurrentContent={() => editorRef.current?.getContent(activeDocument.id) ?? activeDocument.content}
              onClose={() => setHistoryOpen(false)}
            />
          )}

          <section>
            <h3>Input</h3>
            <textarea
              value={stdin}
              onChange={(event) => setStdin(event.target.value)}
              placeholder="Input for your program (stdin)"
              rows={5}
              cols={60}
              spellCheck={false}
            />
          </section>

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
