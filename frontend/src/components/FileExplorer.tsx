import { useState, type FormEvent } from "react"

interface FileExplorerProps {
  files: string[]
  activeFile: string
  onFileSelect: (fileName: string) => void
  // Resolves when the file exists; rejects with a message to show
  onCreateFile: (fileName: string) => Promise<void>
}

function FileExplorer({
  files,
  activeFile,
  onFileSelect,
  onCreateFile,
}: FileExplorerProps) {
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
      setError(reason instanceof Error ? reason.message : "Could not create the file")
    } finally {
      setCreating(false)
    }
  }

  return (
    <aside>
      <h3>Files</h3>

      <ul>
        {files.map((file) => (
          <li key={file}>
            <button
              type="button"
              onClick={() => onFileSelect(file)}
              disabled={file === activeFile}
            >
              {file}
            </button>
          </li>
        ))}
      </ul>

      <form onSubmit={(event) => void handleCreate(event)}>
        <input
          aria-label="New file name"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          placeholder="e.g. solve.py or Main.java"
          maxLength={64}
          disabled={creating}
        />{" "}
        <button type="submit" disabled={creating || !newName.trim()}>
          {creating ? "Adding..." : "Add file"}
        </button>
      </form>
      {error && <p role="alert" style={{ color: "#b00020" }}>{error}</p>}
    </aside>
  )
}

export default FileExplorer
