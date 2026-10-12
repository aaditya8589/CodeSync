import { useRef, useState, type FormEvent, type KeyboardEvent } from "react"

export interface ExplorerFile {
  id: string
  name: string
}

interface FileExplorerProps {
  files: ExplorerFile[]
  activeId: string
  onSelect: (documentId: string) => void
  // Each resolves when the change is saved, or rejects with a message to show
  onCreate: (fileName: string) => Promise<void>
  onRename: (documentId: string, fileName: string) => Promise<void>
  onDelete: (documentId: string) => Promise<void>
}

type Mode =
  | { kind: "idle" }
  | { kind: "renaming"; id: string; value: string }
  | { kind: "confirm-delete"; id: string }

const extensionOf = (fileName: string) => {
  const dot = fileName.lastIndexOf(".")
  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase()
}

const message = (reason: unknown, fallback: string) => (reason instanceof Error && reason.message) || fallback

function PencilIcon() {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
      <path d="M11.5 2.5l2 2L6 12H4v-2z M10 4l2 2" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
      <path d="M3 4.5h10 M6.5 4.5V3h3v1.5 M4.5 4.5l.7 8.5h5.6l.7-8.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  )
}

function FileExplorer({ files, activeId, onSelect, onCreate, onRename, onDelete }: FileExplorerProps) {
  const [newName, setNewName] = useState("")
  const [chosenMode, setMode] = useState<Mode>({ kind: "idle" })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  // Enter and the blur that follows (or disabling the focused box) would both save the rename;
  // state updates are not visible to the second call yet, a ref is
  const savingRef = useRef(false)

  // Someone else may delete or the room may reload away the file being renamed or deleted
  const mode: Mode = chosenMode.kind === "idle" || files.some((file) => file.id === chosenMode.id)
    ? chosenMode
    : { kind: "idle" }

  const canDelete = files.length > 1
  const nameOf = (id: string) => files.find((file) => file.id === id)?.name ?? ""

  const run = async (action: () => Promise<void>, fallback: string): Promise<boolean> => {
    if (savingRef.current) return false
    savingRef.current = true
    setBusy(true)
    setError("")
    try {
      await action()
      return true
    } catch (reason) {
      setError(message(reason, fallback))
      return false
    } finally {
      savingRef.current = false
      setBusy(false)
    }
  }

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    const name = newName.trim()
    if (!name) return
    if (await run(() => onCreate(name), "Could not add the file")) setNewName("")
  }

  const startRename = (file: ExplorerFile) => {
    setError("")
    setMode({ kind: "renaming", id: file.id, value: file.name })
  }

  const finishRename = async () => {
    if (mode.kind !== "renaming" || savingRef.current) return
    const name = mode.value.trim()
    if (!name || name === nameOf(mode.id)) {
      setMode({ kind: "idle" })
      return
    }
    // On failure the box stays open with the message, so the name can be corrected
    if (await run(() => onRename(mode.id, name), "Could not rename the file")) setMode({ kind: "idle" })
  }

  const confirmDelete = async (id: string) => {
    if (await run(() => onDelete(id), "Could not delete the file")) setMode({ kind: "idle" })
  }

  // F2 and Delete work on the focused file, as in most editors
  const handleFileKey = (event: KeyboardEvent, file: ExplorerFile) => {
    if (event.key === "F2") {
      event.preventDefault()
      startRename(file)
    } else if (event.key === "Delete" && canDelete) {
      event.preventDefault()
      setError("")
      setMode({ kind: "confirm-delete", id: file.id })
    }
  }

  return (
    <aside className="files" aria-label="Files">
      <h2>Files</h2>

      <ul className="files__list">
        {files.map((file) => {
          if (mode.kind === "renaming" && mode.id === file.id) {
            // Only the part before the extension is selected, so typing replaces the name
            const selectBaseName = (input: HTMLInputElement | null) => {
              if (!input || input.dataset.ready) return
              input.dataset.ready = "1"
              input.focus()
              const dot = input.value.lastIndexOf(".")
              input.setSelectionRange(0, dot > 0 ? dot : input.value.length)
            }
            return (
              <li key={file.id} className="files__row files__row--editing">
                <span className="files__ext" aria-hidden="true">{extensionOf(mode.value)}</span>
                <input
                  ref={selectBaseName}
                  className="input files__rename"
                  aria-label={`New name for ${file.name}`}
                  value={mode.value}
                  maxLength={64}
                  // Not disabled: that would take the focus away, and after an error the
                  // name should be fixable straight away
                  readOnly={busy}
                  onChange={(event) => setMode({ ...mode, value: event.target.value })}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault()
                      void finishRename()
                    } else if (event.key === "Escape") {
                      setError("")
                      setMode({ kind: "idle" })
                    }
                  }}
                  onBlur={() => void finishRename()}
                />
              </li>
            )
          }

          if (mode.kind === "confirm-delete" && mode.id === file.id) {
            return (
              <li key={file.id} className="files__row files__confirm" role="group" aria-label={`Delete ${file.name}?`}>
                <p>Delete <strong>{file.name}</strong> for everyone in the room?</p>
                <div className="files__confirm-actions">
                  <button
                    type="button"
                    className="button button--danger"
                    disabled={busy}
                    autoFocus
                    onClick={() => void confirmDelete(file.id)}
                  >
                    {busy ? "Deleting..." : "Delete"}
                  </button>
                  <button type="button" className="button button--quiet" disabled={busy} onClick={() => setMode({ kind: "idle" })}>
                    Cancel
                  </button>
                </div>
              </li>
            )
          }

          return (
            <li key={file.id} className="files__row">
              <button
                type="button"
                className="files__item"
                aria-current={file.id === activeId}
                onClick={() => onSelect(file.id)}
                onDoubleClick={() => startRename(file)}
                onKeyDown={(event) => handleFileKey(event, file)}
              >
                <span className="files__ext" aria-hidden="true">{extensionOf(file.name)}</span>
                <span className="files__name">{file.name}</span>
              </button>
              <span className="files__actions">
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Rename ${file.name}`}
                  title="Rename (F2)"
                  disabled={busy}
                  onClick={() => startRename(file)}
                >
                  <PencilIcon />
                </button>
                <button
                  type="button"
                  className="icon-button icon-button--danger"
                  aria-label={`Delete ${file.name}`}
                  title={canDelete ? "Delete" : "A room needs at least one file"}
                  disabled={busy || !canDelete}
                  onClick={() => {
                    setError("")
                    setMode({ kind: "confirm-delete", id: file.id })
                  }}
                >
                  <TrashIcon />
                </button>
              </span>
            </li>
          )
        })}
      </ul>

      {error && mode.kind !== "idle" && <p className="message message--error" role="alert">{error}</p>}

      <form className="files__add" onSubmit={(event) => void handleCreate(event)}>
        <input
          className="input"
          aria-label="New file name"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          placeholder="solve.py, Main.java..."
          maxLength={64}
          disabled={busy}
        />
        <button type="submit" className="button" disabled={busy || !newName.trim()}>
          {busy && mode.kind === "idle" ? "Adding..." : "Add file"}
        </button>
        {error && mode.kind === "idle" && <p className="message message--error" role="alert">{error}</p>}
      </form>
    </aside>
  )
}

export default FileExplorer
