import { useEffect, useRef, useState } from "react"
import { useParams, Link } from "react-router-dom"

import {
  getRoom,
  type Room as RoomType,
} from "../services/roomService"
import { getDocuments } from "../services/documentService"

import RoomHeader from "../components/RoomHeader"
import FileExplorer from "../components/FileExplorer"
import CodeEditor from "../components/CodeEditor"
import OutputPanel from "../components/OutputPanel"

import useCodeSync from "../hooks/useCodeSync"

function Room() {
  const { roomId } = useParams<{ roomId: string }>()

  const [room, setRoom] = useState<RoomType | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  // Files now come from the server, not hardcoded
  const [files, setFiles] = useState<string[]>([])
  const [activeFile, setActiveFile] = useState("")
  const [fileContents, setFileContents] = useState<Record<string, string>>({})
  const [documentIds, setDocumentIds] = useState<Record<string, string>>({})

  const [output, setOutput] = useState("No output yet.")
  const isRemoteUpdate = useRef(false)

  useEffect(() => {
    const fetchRoomAndDocuments = async () => {
      if (!roomId) {
        setError("Room ID is missing")
        setLoading(false)
        return
      }

      try {
        // Both requests run at the same time
        const [roomData, documents] = await Promise.all([
          getRoom(roomId),
          getDocuments(roomId),
        ])

        setRoom(roomData)

        const fileNames = documents.map((doc) => doc.fileName)
        const contents: Record<string, string> = {}
        const ids: Record<string, string> = {}
        for (const doc of documents) {
          contents[doc.fileName] = doc.content
          ids[doc.fileName] = doc.id
        }

        setFiles(fileNames)
        setFileContents(contents)
        setDocumentIds(ids)
        setActiveFile(fileNames[0] ?? "")
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

  const handleRemoteChange = (
    fileName: string,
    content: string
  ) => {
    isRemoteUpdate.current = true

    setFileContents((currentFiles) => ({
      ...currentFiles,
      [fileName]: content,
    }))
  }

  const { sendCodeChange } = useCodeSync({
    roomId: roomId ?? "",
    activeFile,
    activeDocumentId: documentIds[activeFile] ?? "",
    onRemoteChange: handleRemoteChange,
  })

  const handleCodeChange = (newCode: string) => {
    if (isRemoteUpdate.current) {
      isRemoteUpdate.current = false

      setFileContents((currentFiles) => ({
        ...currentFiles,
        [activeFile]: newCode,
      }))

      return
    }

    setFileContents((currentFiles) => ({
      ...currentFiles,
      [activeFile]: newCode,
    }))

    sendCodeChange(newCode)
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

  if (!room) {
    return <p>Room not found</p>
  }

  return (
    <div>
      <RoomHeader
        roomName={room.name}
        roomId={room.id}
      />

      <div>
        <FileExplorer
          files={files}
          activeFile={activeFile}
          onFileSelect={setActiveFile}
        />

        <main>
          {activeFile ? (
            <>
              <h3>{activeFile}</h3>

              <CodeEditor
                code={fileContents[activeFile] ?? ""}
                onCodeChange={handleCodeChange}
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
        onClick={() => {
          setOutput("Code execution is not connected yet.")
        }}
      >
        Run Code
      </button>

      <br />

      <Link to="/dashboard">
        Back to Dashboard
      </Link>
    </div>
  )
}

export default Room