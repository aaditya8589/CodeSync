import { DiffEditor } from "@monaco-editor/react"
import { useEffect, useState } from "react"
import { languageFor } from "../editor/language"
import { defineEditorTheme, EDITOR_FONT, EDITOR_THEME } from "../editor/theme"
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

  const selectedEntry = entries.find((entry) => entry.revision === selected)

  return (
    <div className="dialog-backdrop" onKeyDown={(event) => event.key === "Escape" && onClose()}>
      <div role="dialog" aria-modal="true" aria-label={`History of ${fileName}`} className="dialog">
        <div className="dialog__head">
          <h2>History of {fileName}</h2>
          {error && <p className="message message--error" role="alert">{error}</p>}
          <button type="button" className="button button--quiet" onClick={onClose} autoFocus>Close</button>
        </div>

        <div className="dialog__body">
          <ul className="versions">
            {!history && !error && <li className="compare__empty">Loading...</li>}
            {history && entries.length === 0 && (
              <li className="compare__empty">No history yet. It starts with the next edit to this file.</li>
            )}
            {entries.map((entry) => (
              <li key={entry.revision}>
                <button
                  type="button"
                  onClick={() => void select(entry.revision)}
                  aria-pressed={selected === entry.revision}
                >
                  <span className="versions__title">{entry.title}</span>
                  <span className="versions__detail">{entry.detail}</span>
                </button>
              </li>
            ))}
          </ul>

          <div className="compare">
            {selected === null ? (
              <p className="compare__empty">Pick a version to compare it with the file as it is now.</p>
            ) : (
              <>
                <div className="compare__labels">
                  <span>{selectedEntry?.title ?? `Revision ${selected}`}</span>
                  <span>Now</span>
                </div>
                <DiffEditor
                  height="100%"
                  theme={EDITOR_THEME}
                  beforeMount={defineEditorTheme}
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
                    ...EDITOR_FONT,
                    readOnly: true,
                    originalEditable: false,
                    renderSideBySide: true,
                    renderGutterMenu: false,
                    renderMarginRevertIcon: false,
                    minimap: { enabled: false },
                    scrollBeyondLastLine: false,
                  }}
                />
                <div className="compare__foot">
                  {original === current ? (
                    <span className="console__note">This version is the same as the file now.</span>
                  ) : confirming ? (
                    <>
                      <span>Replace the file for everyone in the room?</span>
                      <button type="button" className="button button--primary" onClick={() => void restore()} disabled={busy}>
                        {busy ? "Restoring..." : "Yes, restore"}
                      </button>
                      <button type="button" className="button button--quiet" onClick={() => setConfirming(false)} disabled={busy}>
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button type="button" className="button" onClick={() => setConfirming(true)}>
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
