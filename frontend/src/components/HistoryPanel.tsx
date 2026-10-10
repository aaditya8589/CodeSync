import { DiffEditor } from "@monaco-editor/react"
import { useEffect, useState } from "react"
import { languageFor } from "../editor/language"
import { historyEntries } from "../history/entries"
import {
  getHistory,
  getRevisionContent,
  restoreRevision,
  type DocumentHistory,
} from "../services/historyService"

interface HistoryPanelProps {
  roomId: string
  documentId: string
  fileName: string
  // The text currently in the editor, compared against the selected version
  getCurrentContent: () => string
  onClose: () => void
}

function HistoryPanel({ roomId, documentId, fileName, getCurrentContent, onClose }: HistoryPanelProps) {
  const [history, setHistory] = useState<DocumentHistory | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [original, setOriginal] = useState("")
  const [current, setCurrent] = useState("")
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    let cancelled = false
    getHistory(roomId, documentId)
      .then((result) => { if (!cancelled) setHistory(result) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [roomId, documentId])

  const select = async (revision: number) => {
    setSelected(revision)
    setConfirming(false)
    setError("")
    try {
      const content = await getRevisionContent(roomId, documentId, revision)
      setOriginal(content)
      setCurrent(getCurrentContent())
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not load that version")
    }
  }

  const restore = async () => {
    if (selected === null) return
    setBusy(true)
    setError("")
    try {
      // The server sends the restore to every open editor, this one included
      await restoreRevision(roomId, documentId, selected)
      onClose()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Restore failed")
      setBusy(false)
    }
  }

  const entries = history ? historyEntries(history) : []

  return (
    <div
      role="dialog"
      aria-label={`History of ${fileName}`}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0, 0, 0, 0.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 100,
      }}
    >
      <div style={{ background: "#fff", color: "#111", width: "min(1200px, 95vw)", padding: 16, borderRadius: 8 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 style={{ margin: 0 }}>History of {fileName}</h3>
          <button type="button" onClick={onClose}>Close</button>
        </div>

        {error && <p style={{ color: "#b00020" }}>{error}</p>}

        <div style={{ display: "flex", gap: 16, marginTop: 12 }}>
          <ul style={{ listStyle: "none", padding: 0, margin: 0, width: 260, maxHeight: 480, overflowY: "auto" }}>
            {!history && !error && <li>Loading…</li>}
            {history && entries.length === 0 && (
              <li>No history yet. It starts with the next edit to this file.</li>
            )}
            {entries.map((entry) => (
              <li key={entry.revision}>
                <button
                  type="button"
                  onClick={() => void select(entry.revision)}
                  aria-pressed={selected === entry.revision}
                  style={{
                    width: "100%",
                    textAlign: "left",
                    padding: 8,
                    marginBottom: 4,
                    background: selected === entry.revision ? "#dbeafe" : "#f4f4f5",
                    border: "1px solid #ddd",
                    borderRadius: 4,
                    cursor: "pointer",
                  }}
                >
                  <strong>{entry.title}</strong>
                  <br />
                  <small>{entry.detail}</small>
                </button>
              </li>
            ))}
          </ul>

          <div style={{ flex: 1, minWidth: 0 }}>
            {selected === null ? (
              <p>Pick a version to compare it with the current file.</p>
            ) : (
              <>
                <p style={{ margin: "0 0 8px" }}>
                  Left: revision {selected}. Right: current file.
                </p>
                <DiffEditor
                  height="420px"
                  theme="vs-dark"
                  language={languageFor(fileName)}
                  original={original}
                  modified={current}
                  // Letting the component dispose its models on close throws "TextModel got
                  // disposed before DiffEditorWidget model got reset". Keeping them instead, at
                  // fixed paths, means the same two models are reused every time.
                  originalModelPath="inmemory://codesync-history/original"
                  modifiedModelPath="inmemory://codesync-history/current"
                  keepCurrentOriginalModel
                  keepCurrentModifiedModel
                  // The gutter menu (revert arrows) is pointless in a read-only view, and its lazily built
                  // actions threw "AbstractContextKeyService has been disposed" when the dialog closed
                  options={{
                    readOnly: true,
                    originalEditable: false,
                    renderSideBySide: true,
                    renderGutterMenu: false,
                    renderMarginRevertIcon: false,
                    minimap: { enabled: false },
                  }}
                />
                <div style={{ marginTop: 8 }}>
                  {confirming ? (
                    <>
                      <span>Replace the current file for everyone in the room? </span>
                      <button type="button" onClick={() => void restore()} disabled={busy}>
                        {busy ? "Restoring..." : "Yes, restore"}
                      </button>{" "}
                      <button type="button" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirming(true)}
                      disabled={original === current}
                    >
                      Restore this version
                    </button>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default HistoryPanel
