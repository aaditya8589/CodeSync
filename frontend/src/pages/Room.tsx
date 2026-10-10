import { useCallback, useEffect, useRef, useState } from "react"
import { Link, Navigate, useParams } from "react-router-dom"

import {
  getRoom,
  type Room as RoomType,
} from "../services/roomService"
import { createDocument, getDocuments, type RoomDocument } from "../services/documentService"
import { runDocument } from "../services/executionService"

import TopBar from "../components/TopBar"
import PresenceSeats from "../components/PresenceSeats"
import FileExplorer from "../components/FileExplorer"
import CodeEditor, { type CodeEditorHandle } from "../components/CodeEditor"
import OutputPanel, { type RunState } from "../components/OutputPanel"
import HistoryPanel from "../components/HistoryPanel"
import { currentUsername } from "../auth/session"

import useCodeSync, { type RemoteCursor } from "../hooks/useCodeSync"
import type { TextOperation } from "../ot/textOperation"
import { isRunnable, runLabel } from "../editor/language"

function Room() {
  const { roomId } = useParams<{ roomId: string }>()

  const [room, setRoom] = useState<RoomType | null>(null)
  const [documents, setDocuments] = useState<RoomDocument[] | null>(null)
  const [activeDocumentId, setActiveDocumentId] = useState("")
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [editorReady, setEditorReady] = useState(false)
  const [run, setRun] = useState<RunState>({ kind: "idle" })
  const [copied, setCopied] = useState(false)
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

  const handleCreateFile = async (fileName: string) => {
    if (!roomId) return
    const created = await createDocument(roomId, fileName)

    // Load the list here instead of through resync(): a reload triggered by the server's
    // "file added" message could otherwise win, and the switch below would happen before
    // the editor has a model for the new file. Counting this as the newest reload discards
    // any older one still in flight.
    const request = ++resyncRequestRef.current
    const fresh = await getDocuments(roomId)
    // If a newer reload started meanwhile, it shows the new file; stay on the current one
    // rather than switch to a file this render does not have yet
    if (request !== resyncRequestRef.current) return
    editorRef.current?.reset(fresh)
    setDocuments(fresh)
    setActiveDocumentId(created.id)
  }

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

    setRun({ kind: "running" })
    try {
      setRun({ kind: "done", result: await runDocument(roomId, activeDocumentId, stdin) })
    } catch (error) {
      setRun({ kind: "failed", message: error instanceof Error ? error.message : "Run failed" })
    }
  }

  const copyRoomId = async () => {
    if (!roomId) return
    try {
      await navigator.clipboard.writeText(roomId)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard blocked (e.g. plain http): show the ID so it can be copied by hand
      window.prompt("Room ID", roomId)
    }
  }

  if (!localStorage.getItem("token")) {
    return <Navigate to="/login" replace />
  }

  if (loading) {
    return <p className="centered-note">Opening the room...</p>
  }

  if (error || !room || !documents) {
    return (
      <div className="centered-note">
        <h1>Can't open this room</h1>
        <p>{error || "The room was not found."}</p>
        <p><Link to="/dashboard" className="button">Back to your rooms</Link></p>
      </div>
    )
  }

  const activeDocument = documents.find((doc) => doc.id === activeDocumentId)
  const runnable = activeDocument ? isRunnable(activeDocument.fileName) : false
  const running = run.kind === "running"

  return (
    <div className="room">
      <TopBar title={room.name}>
        <PresenceSeats members={members} me={currentUsername()} />
        <button type="button" className="button" onClick={() => void copyRoomId()}>
          {copied ? "Copied" : "Copy room ID"}
        </button>
      </TopBar>

      <div className="room__body">
        <FileExplorer
          files={documents.map((doc) => doc.fileName)}
          activeFile={activeDocument?.fileName ?? ""}
          onFileSelect={(fileName) => {
            const doc = documents.find((d) => d.fileName === fileName)
            if (doc) setActiveDocumentId(doc.id)
          }}
          onCreateFile={handleCreateFile}
        />

        <main className="workspace">
          <div className="editor-bar">
            <span className="editor-bar__file">{activeDocument?.fileName ?? "No file"}</span>
            <span className={`status-dot${ready ? " status-dot--live" : ""}`}>
              {ready ? "Live" : "Connecting..."}
            </span>
            <div className="editor-bar__actions">
              <button
                type="button"
                className="button button--quiet"
                onClick={() => setHistoryOpen(true)}
                disabled={!ready || !activeDocument}
              >
                History
              </button>
              <button
                type="button"
                className="button button--primary"
                onClick={handleRun}
                disabled={running || !ready || !runnable}
                title={runnable ? undefined : "Only .cpp, .py and .java files can be run"}
              >
                {running ? "Running..." : `Run ${activeDocument ? runLabel(activeDocument.fileName) : ""}`}
              </button>
            </div>
          </div>

          <div className="editor-host">
            {activeDocument ? (
              <CodeEditor
                ref={editorRef}
                documents={documents}
                activeDocumentId={activeDocumentId}
                readOnly={!ready}
                onLocalOperation={applyLocalOperation}
                onCursorChange={sendCursor}
                onReady={handleEditorReady}
              />
            ) : (
              <p className="centered-note">This room has no files yet. Add one on the left.</p>
            )}
          </div>

          <div className="console">
            <section className="console__pane" aria-label="Input">
              <div className="console__head">
                <label htmlFor="stdin">Input</label>
              </div>
              <textarea
                id="stdin"
                className="textarea console__input"
                value={stdin}
                onChange={(event) => setStdin(event.target.value)}
                placeholder="What your program reads from standard input"
                spellCheck={false}
              />
            </section>
            <OutputPanel state={run} />
          </div>
        </main>
      </div>

      {historyOpen && activeDocument && (
        <HistoryPanel
          roomId={room.id}
          documentId={activeDocument.id}
          fileName={activeDocument.fileName}
          getCurrentContent={() => editorRef.current?.getContent(activeDocument.id) ?? activeDocument.content}
          onClose={() => setHistoryOpen(false)}
        />
      )}
    </div>
  )
}

export default Room
