import { useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"

function WebSocketTest() {
  const [message, setMessage] = useState("")
  const [receivedMessage, setReceivedMessage] = useState("")
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
          `/topic/rooms/${roomId}`,
          (message) => {
            console.log("Received:", message.body)
            setReceivedMessage(message.body)
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

  const sendMessage = () => {
    if (!message.trim()) {
      return
    }

    const client = clientRef.current

    if (!client || !client.connected) {
      console.error("WebSocket is not connected")
      return
    }

    client.publish({
      destination: `/app/rooms/${roomId}/test`,
      body: message,
    })

    setMessage("")
  }

  return (
    <div>
      <h2>WebSocket Room Test</h2>

      <p>
        Room: {roomId}
      </p>

      <p>
        Status: {connected ? "Connected" : "Disconnected"}
      </p>

      <input
        type="text"
        value={message}
        onChange={(event) => setMessage(event.target.value)}
        placeholder="Enter a message"
      />

      <button
        type="button"
        onClick={sendMessage}
        disabled={!connected}
      >
        Send
      </button>

      <p>
        Received: {receivedMessage || "No message yet"}
      </p>
    </div>
  )
}

export default WebSocketTest