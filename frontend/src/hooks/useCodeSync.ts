import { useEffect, useRef } from "react"
import { Client } from "@stomp/stompjs"

interface CodeChangeMessage {
  roomId: string
  fileName: string
  content: string
}

interface UseCodeSyncProps {
  roomId: string
  activeFile: string
  onRemoteChange: (fileName: string, content: string) => void
}

function useCodeSync({
  roomId,
  activeFile,
  onRemoteChange,
}: UseCodeSyncProps) {
  const clientRef = useRef<Client | null>(null)
  const onRemoteChangeRef = useRef(onRemoteChange)

  useEffect(() => {
    onRemoteChangeRef.current = onRemoteChange
  }, [onRemoteChange])

  useEffect(() => {
    const token = localStorage.getItem("token")

    if (!token) {
      console.error(
        "CodeSync WebSocket: no authentication token found"
      )
      return
    }

    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",

      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },

      reconnectDelay: 5000,

      onConnect: () => {
        console.log("CodeSync WebSocket connected")

        client.subscribe(
          `/topic/rooms/${roomId}/code`,
          (message) => {
            const change: CodeChangeMessage =
              JSON.parse(message.body)

            onRemoteChangeRef.current(
              change.fileName,
              change.content
            )
          }
        )
      },

      onDisconnect: () => {
        console.log(
          "CodeSync WebSocket disconnected"
        )
      },

      onStompError: (frame) => {
        console.error(
          "CodeSync STOMP error:",
          frame
        )
      },

      onWebSocketError: (error) => {
        console.error(
          "CodeSync WebSocket error:",
          error
        )
      },
    })

    clientRef.current = client

    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [roomId])

  const sendCodeChange = (content: string) => {
    const client = clientRef.current

    if (!client || !client.connected) {
      console.error(
        "CodeSync WebSocket is not connected"
      )
      return
    }

    const change: CodeChangeMessage = {
      roomId,
      fileName: activeFile,
      content,
    }

    client.publish({
      destination: `/app/rooms/${roomId}/code`,
      body: JSON.stringify(change),
    })
  }

  return {
    sendCodeChange,
  }
}

export default useCodeSync