import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Link, useParams } from "react-router-dom"

import {
  getRoom,
  type Room as RoomType,
} from "../services/roomService"
import {
  createDocument,
  deleteDocument,
  getDocuments,
  renameDocument,
  type RoomDocument,
} from "../services/documentService"
import { afterSnapshot, withCurrentNames, withRename, type NameOverrides } from "../files/fileNames"
import { runDocument } from "../services/executionService"

import TopBar from "../components/TopBar"
import PresenceSeats from "../components/PresenceSeats"
import FileExplorer from "../components/FileExplorer"
import CodeEditor, { type CodeEditorHandle } from "../components/CodeEditor"
import OutputPanel, { type RunState } from "../components/OutputPanel"
import HistoryPanel from "../components/HistoryPanel"
import { currentUsername } from "../auth/session"
import RoomProblemPanel from "../arena/RoomProblemPanel"
import {
  VERDICT_TEXT,
  attachProblem,
  getProblem,
  submitDocument,
  verdictTone,
  type ProblemDetail,
  type Verdict,
} from "../services/arenaService"

import MatchPanel from "../match/MatchPanel"
import MatchButton from "../match/MatchButton"
import { isPrivateNow, serverOffset } from "../match/clock"
import { endMatch, getMatch, joinMatch, startMatch, type Match, type MatchChangedMessage } from "../services/matchService"
import useCodeSync, {
  type FileChangeMessage,
  type RemoteCursor,
  type RoomProblemMessage,
  type RoomSubmissionMessage,
} from "../hooks/useCodeSync"
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
  const [problemSlug, setProblemSlug] = useState<string | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  // The side panel: the room's problem, or the match
  const [panel, setPanel] = useState<"problem" | "match" | null>(null)
  const problemOpen = panel === "problem"
  const setProblemOpen = (open: boolean | ((was: boolean) => boolean)) =>
    setPanel((current) => {
      const next = typeof open === "function" ? open(current === "problem") : open
      return next ? "problem" : current === "problem" ? null : current
    })
  const [match, setMatch] = useState<Match | null>(null)
  // How far the server's clock is ahead of this browser's
  const [clockOffset, setClockOffset] = useState(0)
  // The latest thing that happened to the room's problem, from anyone in the room
  const [roomNews, setRoomNews] = useState<{ text: string; tone: "ok" | "err" | "limit" | "info" } | null>(null)
  const [stdin, setStdin] = useState("")
  const [historyOpen, setHistoryOpen] = useState(false)
  const [nameOverrides, setNameOverrides] = useState<NameOverrides>(() => new Map())

  const editorRef = useRef<CodeEditorHandle>(null)
  // Only the newest reload may apply its result
  const resyncRequestRef = useRef(0)
  // How many renames have been shown; see files/fileNames.ts
  const renameSeqRef = useRef(0)
  // A file this tab just created, to open as soon as a load includes it
  const openWhenLoadedRef = useRef<((doc: RoomDocument) => boolean) | null>(null)
  // Read by loadDocuments, which must not be recreated (and restart the sync) on every switch
  const activeIdRef = useRef(activeDocumentId)
  useEffect(() => {
    activeIdRef.current = activeDocumentId
  })

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
        setProblemSlug(roomData.problemSlug ?? null)
        setProblemOpen(Boolean(roomData.problemSlug))
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

  // Loads the documents; if a newer load starts meanwhile, only that one is applied
  const loadDocuments = useCallback(async () => {
    if (!roomId) return

    const request = ++resyncRequestRef.current
    const renamesBefore = renameSeqRef.current
    const fresh = await getDocuments(roomId)
    if (request !== resyncRequestRef.current) return

    editorRef.current?.reset(fresh)
    setDocuments(fresh)
    setNameOverrides((overrides) => afterSnapshot(overrides, renamesBefore))
    const wanted = openWhenLoadedRef.current ? fresh.find(openWhenLoadedRef.current) : undefined
    if (wanted) {
      openWhenLoadedRef.current = null
      setActiveDocumentId(wanted.id)
    } else if (!fresh.some((doc) => doc.id === activeIdRef.current)) {
      // The open file was deleted: show another one
      setActiveDocumentId(fresh[0]?.id ?? "")
      setHistoryOpen(false)
    }
  }, [roomId])

  const resync = useCallback(async (reason: string) => {
    console.info(`CodeSync: loading latest documents (${reason})`)
    try {
      await loadDocuments()
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to reload documents")
    }
  }, [loadDocuments])

  const handleRemoteOperation = useCallback((documentId: string, operation: TextOperation) => {
    editorRef.current?.applyRemote(documentId, operation)
  }, [])

  const handleResyncNeeded = useCallback((reason: string) => {
    void resync(reason)
  }, [resync])

  const handleCreateFile = async (fileName: string) => {
    if (!roomId) return
    const created = await createDocument(roomId, fileName)

    // Switching needs the editor to have a model for the new file, so it happens in whichever
    // load first includes it: this one, or the newer one started by the server's "file added"
    // message, which then replaces this one
    openWhenLoadedRef.current = (doc) => doc.id === created.id
    await loadDocuments()
  }

  const showRename = useCallback((documentId: string, fileName: string) => {
    const seq = ++renameSeqRef.current
    setNameOverrides((overrides) => withRename(overrides, documentId, fileName, seq))
  }, [])

  const handleRenameFile = async (documentId: string, fileName: string) => {
    if (!roomId) return
    const renamed = await renameDocument(roomId, documentId, fileName)
    // Shown now rather than when the room's message arrives
    showRename(renamed.id, renamed.fileName)
  }

  const handleDeleteFile = async (documentId: string) => {
    if (!roomId) return
    await deleteDocument(roomId, documentId)
    await loadDocuments()
  }

  const handleRemoteCursor = useCallback((cursor: RemoteCursor) => {
    editorRef.current?.setRemoteCursor(cursor)
  }, [])

  const handleEditorReady = useCallback(() => setEditorReady(true), [])

  const me = currentUsername()

  const showMatch = useCallback((found: Match | null) => {
    setMatch(found)
    if (found) setClockOffset(serverOffset(found.serverTime, Date.now()))
  }, [])

  const loadMatch = useCallback(async () => {
    if (!roomId) return
    try {
      showMatch(await getMatch(roomId))
    } catch {
      // The panel keeps what it showed; the next change or reload brings it up to date
    }
  }, [roomId, showMatch])

  useEffect(() => {
    if (!roomId) return
    let cancelled = false
    getMatch(roomId)
      .then((found) => {
        if (cancelled) return
        showMatch(found)
        if (found?.running) setPanel("match")
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [roomId, showMatch])

  // When time runs out every private file becomes visible: reload them and the final standings.
  // The server decides; this only asks again just after the moment it said.
  useEffect(() => {
    if (!match?.running) return
    const wait = Date.parse(match.endsAt) - (Date.now() + clockOffset) + 500
    const timer = setTimeout(() => {
      void loadMatch()
      void resync("the match ended")
    }, Math.max(0, wait))
    return () => clearTimeout(timer)
  }, [match?.running, match?.endsAt, clockOffset, loadMatch, resync])

  // After starting or joining, open this player's file for problem A once the load includes it
  const openMyFirstMatchFile = (started: Match) => {
    const first = started.problems[0]?.slug
    openWhenLoadedRef.current = (doc) => doc.owner === me && doc.problemSlug === first
  }

  const handleStartMatch = async (slugs: string[], minutes: number) => {
    if (!roomId) return
    const started = await startMatch(roomId, slugs, minutes)
    showMatch(started)
    openMyFirstMatchFile(started)
    await loadDocuments()
  }

  const handleJoinMatch = async () => {
    if (!roomId) return
    const joined = await joinMatch(roomId)
    showMatch(joined)
    openMyFirstMatchFile(joined)
    await loadDocuments()
  }

  const handleEndMatch = async () => {
    if (!roomId) return
    showMatch(await endMatch(roomId))
    await loadDocuments()
  }

  const handleMatchChanged = useCallback((message: MatchChangedMessage) => {
    void loadMatch()
    if (message.event === "ENDED") void resync("the match was ended")
    if (message.username === me) return
    if (message.event === "STARTED") {
      setPanel("match")
      setRoomNews({ text: `${message.username} started a match`, tone: "info" })
    } else if (message.event === "JOINED") {
      setRoomNews({ text: `${message.username} joined the match`, tone: "info" })
    } else if (message.event === "ENDED") {
      setRoomNews({ text: `${message.username} ended the match`, tone: "info" })
    }
  }, [loadMatch, resync, me])

  const handleProblemChanged = useCallback((message: RoomProblemMessage) => {
    setProblemSlug(message.slug)
    if (message.slug) setProblemOpen(true)
    if (message.changedBy !== me) {
      setRoomNews({ text: message.slug ? `${message.changedBy} picked a problem` : `${message.changedBy} removed the problem`, tone: "info" })
    }
  }, [me])

  const handleFileChange = useCallback((message: FileChangeMessage) => {
    if (message.type === "RENAMED" && message.fileName) showRename(message.documentId, message.fileName)
    if (message.changedBy === me) return
    const text = {
      CREATED: `${message.changedBy} added ${message.fileName}`,
      RENAMED: `${message.changedBy} renamed ${message.previousName} to ${message.fileName}`,
      DELETED: `${message.changedBy} deleted ${message.previousName}`,
    }[message.type]
    setRoomNews({ text, tone: "info" })
  }, [me, showRename])

  const handleSubmission = useCallback((message: RoomSubmissionMessage) => {
    if (message.username === me) return
    const verdict = message.verdict as Verdict
    setRoomNews({
      text: `${message.username} submitted ${message.fileName}: ${VERDICT_TEXT[verdict]} (${message.passed}/${message.total})`,
      tone: verdictTone(verdict),
    })
  }, [me])

  // Load the statement whenever the room's problem changes
  useEffect(() => {
    if (!problemSlug) return
    let cancelled = false
    getProblem(problemSlug)
      .then((detail) => { if (!cancelled) setProblem(detail) })
      .catch(() => { if (!cancelled) setProblem(null) })
    return () => { cancelled = true }
  }, [problemSlug])

  const { ready, members, applyLocalOperation, sendCursor } = useCodeSync({
    roomId: roomId ?? "",
    documents,
    enabled: editorReady,
    onRemoteOperation: handleRemoteOperation,
    onResyncNeeded: handleResyncNeeded,
    onRemoteCursor: handleRemoteCursor,
    onProblemChanged: handleProblemChanged,
    onSubmission: handleSubmission,
    onFileChange: handleFileChange,
    onMatchChanged: handleMatchChanged,
  })

  // A private match file's caret would tell other players where its owner is working, so it is
  // never sent. The server cannot filter it: it is the owner's own browser that would send it.
  const documentsRef = useRef(documents)
  useEffect(() => {
    documentsRef.current = documents
  })
  const offsetRef = useRef(clockOffset)
  useEffect(() => {
    offsetRef.current = clockOffset
  })
  const sendVisibleCursor = useCallback((documentId: string, anchor: number, head: number) => {
    const doc = documentsRef.current?.find((d) => d.id === documentId)
    if (doc && isPrivateNow(doc.privateUntil, offsetRef.current, Date.now())) return
    sendCursor(documentId, anchor, head)
  }, [sendCursor])

  // The documents as shown: the last snapshot with any renames since applied
  const shownDocuments = useMemo(
    () => (documents ? withCurrentNames(documents, nameOverrides) : null),
    [documents, nameOverrides]
  )

  useEffect(() => {
    if (shownDocuments) editorRef.current?.updateLanguages(shownDocuments)
  }, [shownDocuments])

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

  const handleSubmit = async () => {
    if (!roomId || !activeDocumentId) return
    setRun({ kind: "judging" })
    try {
      setRun({ kind: "judged", result: await submitDocument(roomId, activeDocumentId) })
    } catch (error) {
      setRun({ kind: "failed", message: error instanceof Error ? error.message : "Submit failed" })
    }
  }

  const chooseProblem = async (slug: string | null) => {
    if (!roomId) return
    try {
      const result = await attachProblem(roomId, slug)
      setProblemSlug(result.slug)
    } catch (error) {
      setRoomNews({ text: error instanceof Error ? error.message : "Couldn't change the problem", tone: "err" })
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

  if (loading) {
    return <p className="centered-note">Opening the room...</p>
  }

  if (error || !room || !shownDocuments) {
    return (
      <div className="centered-note">
        <h1>Can't open this room</h1>
        <p>{error || "The room was not found."}</p>
        <p><Link to="/dashboard" className="button">Back to your rooms</Link></p>
      </div>
    )
  }

  const activeDocument = shownDocuments.find((doc) => doc.id === activeDocumentId)
  const runnable = activeDocument ? isRunnable(activeDocument.fileName) : false
  const running = run.kind === "running" || run.kind === "judging"
  const shownProblem = problemSlug && problem?.slug === problemSlug ? problem : null
  // A match file is judged against its own problem; any other file against the room's
  const matchProblem = activeDocument?.problemSlug
    ? match?.problems.find((p) => p.slug === activeDocument.problemSlug)
    : undefined
  const canSubmit = Boolean(problemSlug || activeDocument?.problemSlug)
  const submitLabel = matchProblem && match?.running && activeDocument?.owner === me
    ? `Submit ${matchProblem.letter}`
    : "Submit"

  return (
    <div className="room">
      <TopBar title={room.name}>
        <PresenceSeats members={members} me={me} />
        <button type="button" className="button" onClick={() => void copyRoomId()}>
          {copied ? "Copied" : "Copy room ID"}
        </button>
      </TopBar>

      <div className={`room__body${panel ? " room__body--with-problem" : ""}`}>
        <FileExplorer
          files={shownDocuments.map((doc) => ({
            id: doc.id,
            name: doc.fileName,
            // Private while the match it belongs to is running; the reload at the end unlocks it
            locked: Boolean(match?.running && doc.privateUntil && Date.parse(doc.privateUntil) === Date.parse(match.endsAt)),
          }))}
          activeId={activeDocumentId}
          onSelect={setActiveDocumentId}
          onCreate={handleCreateFile}
          onRename={handleRenameFile}
          onDelete={handleDeleteFile}
        />

        <main className="workspace">
          <div className="editor-bar">
            <span className="editor-bar__file">{activeDocument?.fileName ?? "No file"}</span>
            <span className={`status-dot${ready ? " status-dot--live" : ""}`}>
              {ready ? "Live" : "Connecting..."}
            </span>
            {roomNews && (
              <span className={`room-news room-news--${roomNews.tone}`} role="status">{roomNews.text}</span>
            )}
            <div className="editor-bar__actions">
              <MatchButton
                match={match}
                offset={clockOffset}
                pressed={panel === "match"}
                onClick={() => setPanel((current) => current === "match" ? null : "match")}
              />
              <button
                type="button"
                className="button button--quiet"
                aria-pressed={problemOpen}
                onClick={() => setProblemOpen((open) => !open)}
              >
                Problem
              </button>
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
                className={`button${canSubmit ? "" : " button--primary"}`}
                onClick={handleRun}
                disabled={running || !ready || !runnable}
                title={runnable ? "Run with the input below" : "Only .cpp, .py and .java files can be run"}
              >
                {run.kind === "running" ? "Running..." : `Run ${activeDocument ? runLabel(activeDocument.fileName) : ""}`}
              </button>
              {canSubmit && (
                <button
                  type="button"
                  className="button button--primary"
                  onClick={handleSubmit}
                  disabled={running || !ready || !runnable}
                  title={matchProblem ? `Judge this file on ${matchProblem.title}` : "Judge this file on all of the problem's tests"}
                >
                  {run.kind === "judging" ? "Judging..." : submitLabel}
                </button>
              )}
            </div>
          </div>

          <div className="editor-host">
            {activeDocument ? (
              <CodeEditor
                ref={editorRef}
                documents={shownDocuments}
                activeDocumentId={activeDocumentId}
                readOnly={!ready}
                onLocalOperation={applyLocalOperation}
                onCursorChange={sendVisibleCursor}
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

        {panel === "match" && (
          <MatchPanel
            match={match}
            offset={clockOffset}
            me={me}
            onStart={handleStartMatch}
            onJoin={handleJoinMatch}
            onEnd={handleEndMatch}
            onClose={() => setPanel(null)}
          />
        )}

        {problemOpen && (
          <RoomProblemPanel
            problem={shownProblem}
            onChoose={(slug) => void chooseProblem(slug)}
            onClose={() => setProblemOpen(false)}
          />
        )}
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
