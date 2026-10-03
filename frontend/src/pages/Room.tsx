import { useEffect, useRef, useState } from "react"
import { useParams, Link } from "react-router-dom"

import {
  getRoom,
  type Room as RoomType,
} from "../services/roomService"

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

  const [files] = useState<string[]>([
    "main.cpp",
    "solution.cpp",
  ])

  const [activeFile, setActiveFile] = useState("main.cpp")

  const [fileContents, setFileContents] = useState<
    Record<string, string>
  >({
    "main.cpp": `#include <iostream>

using namespace std;

int main() {
    cout << "Hello, CodeSync!" << endl;

    return 0;
}`,
    "solution.cpp": `#include <iostream>

using namespace std;

int main() {
    // Write your solution here

    return 0;
}`,
  })

  const [output, setOutput] = useState("No output yet.")
  const isRemoteUpdate = useRef(false)

  useEffect(() => {
    const fetchRoom = async () => {
      if (!roomId) {
        setError("Room ID is missing")
        setLoading(false)
        return
      }

      try {
        const data = await getRoom(roomId)
        setRoom(data)
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

    fetchRoom()
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
          <h3>{activeFile}</h3>

          <CodeEditor
            code={fileContents[activeFile]}
            onCodeChange={handleCodeChange}
          />

          <OutputPanel output={output} />
        </main>
      </div>

      <button
        type="button"
        onClick={() => {
          setOutput(
            "Code execution is not connected yet."
          )
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