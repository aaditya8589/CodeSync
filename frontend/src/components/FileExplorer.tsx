interface FileExplorerProps {
  files: string[]
  activeFile: string
  onFileSelect: (fileName: string) => void
}

function FileExplorer({
  files,
  activeFile,
  onFileSelect,
}: FileExplorerProps) {
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
    </aside>
  )
}

export default FileExplorer