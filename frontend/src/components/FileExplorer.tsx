import { useState, type FormEvent } from "react"

interface FileExplorerProps {
  files: string[]
  activeFile: string
  onFileSelect: (fileName: string) => void
  // Resolves when the file exists; rejects with a message to show
  onCreateFile: (fileName: string) => Promise<void>
}

const extensionOf = (fileName: string) => {
  const dot = fileName.lastIndexOf(".")
  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase()
}

function FileExplorer({ files, activeFile, onFileSelect, onCreateFile }: FileExplorerProps) {
  const [newName, setNewName] = useState("")
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState("")

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    if (!newName.trim()) return

    setCreating(true)
    setError("")
    try {
      await onCreateFile(newName.trim())
      setNewName("")
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not add the file")
    } finally {
      setCreating(false)
    }
  }

  return (
    <aside className="files" aria-label="Files">
      <h2>Files</h2>

      <ul className="files__list">
        {files.map((file) => (
          <li key={file}>
            <button
              type="button"
              className="files__item"
              aria-current={file === activeFile}
              onClick={() => onFileSelect(file)}
            >
              <span className="files__ext" aria-hidden="true">{extensionOf(file)}</span>
              {file}
            </button>
          </li>
        ))}
      </ul>

      <form className="files__add" onSubmit={(event) => void handleCreate(event)}>
        <input
          className="input"
          aria-label="New file name"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          placeholder="solve.py, Main.java..."
          maxLength={64}
          disabled={creating}
        />
        <button type="submit" className="button" disabled={creating || !newName.trim()}>
          {creating ? "Adding..." : "Add file"}
        </button>
        {error && <p className="message message--error" role="alert">{error}</p>}
      </form>
    </aside>
  )
}

export default FileExplorer
