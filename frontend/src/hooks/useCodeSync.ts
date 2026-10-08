import { useCallback, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { OtClient, type ServerOperation } from "../ot/otClient"
import type { TextOperation } from "../ot/textOperation"
import type { RoomDocument } from "../services/documentService"

interface AppliedOperationMessage extends ServerOperation {
  documentId: string
  author: string
}

interface OperationErrorMessage {
  clientId: string | null
  documentId: string | null
  code: string
  message: string
}

interface UseCodeSyncProps {
  roomId: string
  // The snapshot the editor currently shows. A new array means a fresh snapshot.
  documents: RoomDocument[] | null
  enabled: boolean
  onRemoteOperation: (documentId: string, operation: TextOperation) => void
  onResyncNeeded: (reason: string) => void
}

const GAP_TIMEOUT_MS = 3000

function useCodeSync({
  roomId,
  documents,
  enabled,
  onRemoteOperation,
  onResyncNeeded,
}: UseCodeSyncProps) {
  // One ID per tab: the same user can have the room open twice
  const [clientId] = useState(() => crypto.randomUUID())
  const [ready, setReady] = useState(false)

  const stompRef = useRef<Client | null>(null)
  const connectedRef = useRef(false)
  const otClientsRef = useRef(new Map<string, OtClient>())
  // While waiting for a fresh snapshot, broadcasts are queued instead of applied
  const syncingRef = useRef(false)
  const queuedRef = useRef<AppliedOperationMessage[]>([])
  const gapTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const callbacksRef = useRef({ onRemoteOperation, onResyncNeeded })
  useEffect(() => {
    callbacksRef.current = { onRemoteOperation, onResyncNeeded }
  })

  const requestResync = useCallback((reason: string) => {
    syncingRef.current = true
    setReady(false)
    callbacksRef.current.onResyncNeeded(reason)
  }, [])

  // Every new snapshot gets fresh OT state, then any broadcasts that arrived meanwhile
  useEffect(() => {
    if (!documents) return

    const publish = (documentId: string, operation: TextOperation, baseRevision: number) => {
      const stomp = stompRef.current
      if (!stomp || !stomp.connected) {
        requestResync("not connected")
        return
      }
      stomp.publish({
        destination: `/app/rooms/${roomId}/code`,
        body: JSON.stringify({ documentId, baseRevision, operation: operation.toJSON(), clientId }),
      })
    }

    const clients = new Map<string, OtClient>()
    for (const doc of documents) {
      clients.set(doc.id, new OtClient(
        doc.revision,
        clientId,
        (operation, baseRevision) => publish(doc.id, operation, baseRevision),
        (operation) => callbacksRef.current.onRemoteOperation(doc.id, operation)
      ))
    }
    otClientsRef.current = clients
    syncingRef.current = false

    const backlog = queuedRef.current
    queuedRef.current = []
    for (const message of backlog) {
      clients.get(message.documentId)?.receive(message)
    }

    setReady(connectedRef.current)
  }, [documents, roomId, clientId, requestResync])

  useEffect(() => {
    if (!enabled) return

    const token = localStorage.getItem("token")
    if (!token) {
      console.error("CodeSync WebSocket: no authentication token found")
      return
    }

    const handleBroadcast = (message: AppliedOperationMessage) => {
      if (syncingRef.current) {
        queuedRef.current.push(message)
        return
      }

      const otClient = otClientsRef.current.get(message.documentId)
      if (!otClient) return

      otClient.receive(message)

      // A missing revision normally arrives within milliseconds; if not, reload
      if (otClient.hasGap && !gapTimerRef.current) {
        gapTimerRef.current = setTimeout(() => {
          gapTimerRef.current = null
          if (otClient.hasGap) requestResync("missed an update")
        }, GAP_TIMEOUT_MS)
      }
    }

    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,

      onConnect: () => {
        client.subscribe(`/topic/rooms/${roomId}/code`, (frame) => {
          handleBroadcast(JSON.parse(frame.body))
        })
        client.subscribe("/user/queue/errors", (frame) => {
          const error: OperationErrorMessage = JSON.parse(frame.body)
          // Errors go to every tab of this user; only the tab that sent it reacts
          if (error.clientId === clientId) {
            console.warn(`CodeSync: edit ${error.code}: ${error.message}`)
            requestResync(error.message)
          }
        })
        connectedRef.current = true
        // Edits made before this subscription were not received, so start from a fresh snapshot
        requestResync("connected")
      },

      onWebSocketClose: () => {
        connectedRef.current = false
        syncingRef.current = true
        setReady(false)
      },

      onStompError: (frame) => {
        console.error("CodeSync STOMP error:", frame.headers.message)
      },
    })

    stompRef.current = client
    client.activate()

    return () => {
      if (gapTimerRef.current) clearTimeout(gapTimerRef.current)
      gapTimerRef.current = null
      connectedRef.current = false
      stompRef.current = null
      client.deactivate()
    }
  }, [enabled, roomId, clientId, requestResync])

  const applyLocalOperation = useCallback((documentId: string, operation: TextOperation) => {
    otClientsRef.current.get(documentId)?.applyLocal(operation)
  }, [])

  return { ready, applyLocalOperation }
}

export default useCodeSync
