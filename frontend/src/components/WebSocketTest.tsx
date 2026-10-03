import { useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"

interface CodeChangeMessage {
  roomId: string
  fileName: string
  content: string
}

function WebSocketTest() {
  const [fileName, setFileName] = useState("main.cpp")
  const [code, setCode] = useState("")
  const [receivedChange, setReceivedChange] =
    useState<CodeChangeMessage | null>(null)
  const [connected, setConnected] = useState(false)

  const clientRef = useRef<Client | null>(null)

  const roomId = "ad35e907-be64-4c5d-8472-112a9479e1c5"

  useEffect(() => {
    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",

      reconnectDelay: 5000,

      onConnect: () => {
        console.log("WebSocket connected")
        setConnected(true)

        client.subscribe(
          `/topic/rooms/${roomId}/code`,
          (message) => {
            const change: CodeChangeMessage =
              JSON.parse(message.body)

            console.log("Received code change:", change)

            setReceivedChange(change)
          }
        )
      },

      onDisconnect: () => {
        console.log("WebSocket disconnected")
        setConnected(false)
      },

      onStompError: (frame) => {
        console.error("STOMP error:", frame)
      },

      onWebSocketError: (error) => {
        console.error("WebSocket error:", error)
      },
    })

    clientRef.current = client

    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [])

  const sendCodeChange = () => {
    const client = clientRef.current

    if (!client || !client.connected) {
      console.error("WebSocket is not connected")
      return
    }

    const change: CodeChangeMessage = {
      roomId,
      fileName,
      content: code,
    }

    client.publish({
      destination: `/app/rooms/${roomId}/code`,
      body: JSON.stringify(change),
    })
  }

  return (
    <div>
      <h2>Code Change WebSocket Test</h2>

      <p>
        Room: {roomId}
      </p>

      <p>
        Status: {connected ? "Connected" : "Disconnected"}
      </p>

      <div>
        <label htmlFor="file-name">
          File:
        </label>

        <input
          id="file-name"
          type="text"
          value={fileName}
          onChange={(event) =>
            setFileName(event.target.value)
          }
        />
      </div>

      <br />

      <div>
        <label htmlFor="code">
          Code:
        </label>

        <br />

        <textarea
          id="code"
          rows={10}
          cols={60}
          value={code}
          onChange={(event) =>
            setCode(event.target.value)
          }
          placeholder="Enter code"
        />
      </div>

      <br />

      <button
        type="button"
        onClick={sendCodeChange}
        disabled={!connected}
      >
        Send Code Change
      </button>

      <h3>Received Change</h3>

      {receivedChange ? (
        <div>
          <p>
            <strong>File:</strong>{" "}
            {receivedChange.fileName}
          </p>

          <pre>
            {receivedChange.content}
          </pre>
        </div>
      ) : (
        <p>No code change received yet.</p>
      )}
    </div>
  )
}

export default WebSocketTest