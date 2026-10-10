import { useCallback, useEffect, useRef, useState } from "react"
import { Client } from "@stomp/stompjs"
import { WEBSOCKET_URL } from "../config"
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

export interface PresenceMember {
  username: string
  clientIds: string[]
}

interface RemoteCursorMessage {
  clientId: string
  username: string
  documentId: string
  revision: number
  anchor: number
  head: number
}

export interface RemoteCursor {
  clientId: string
  username: string
  documentId: string
  anchor: number
  head: number
}

interface LocalCursor {
  documentId: string
  anchor: number
  head: number
}

interface UseCodeSyncProps {
  roomId: string
  // The snapshot the editor currently shows. A new array means a fresh snapshot.
  documents: RoomDocument[] | null
  enabled: boolean
  onRemoteOperation: (documentId: string, operation: TextOperation) => void
  onResyncNeeded: (reason: string) => void
  onRemoteCursor: (cursor: RemoteCursor) => void
}

const GAP_TIMEOUT_MS = 3000
// Cursor updates are sent at most this often while the caret is moving
const CURSOR_THROTTLE_MS = 50

function useCodeSync({
  roomId,
  documents,
  enabled,
  onRemoteOperation,
  onResyncNeeded,
  onRemoteCursor,
}: UseCodeSyncProps) {
  // One ID per tab: the same user can have the room open twice
  const [clientId] = useState(() => crypto.randomUUID())
  const [ready, setReady] = useState(false)
  const [members, setMembers] = useState<PresenceMember[]>([])

  const stompRef = useRef<Client | null>(null)
  const connectedRef = useRef(false)
  const otClientsRef = useRef(new Map<string, OtClient>())
  // While waiting for a fresh snapshot, broadcasts are queued instead of applied
  const syncingRef = useRef(false)
  const queuedRef = useRef<AppliedOperationMessage[]>([])
  const gapTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Our caret, and whether the latest position still has to be sent
  const localCursorRef = useRef<LocalCursor | null>(null)
  const cursorDirtyRef = useRef(false)
  const cursorTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Remote cursors from a revision this tab has not reached yet, retried after each update
  const earlyCursorsRef = useRef(new Map<string, RemoteCursorMessage>())

  const callbacksRef = useRef({ onRemoteOperation, onResyncNeeded, onRemoteCursor })
  useEffect(() => {
    callbacksRef.current = { onRemoteOperation, onResyncNeeded, onRemoteCursor }
  })

  // A cursor is only sent while its document has no unconfirmed edits. Then the editor shows
  // exactly server revision `revision`, so the offsets mean the same thing to every receiver.
  // Otherwise it is sent as soon as the edits are acknowledged.
  const flushCursor = useCallback(() => {
    const cursor = localCursorRef.current
    const stomp = stompRef.current
    if (!cursor || !cursorDirtyRef.current || syncingRef.current || !stomp?.connected) return

    const otClient = otClientsRef.current.get(cursor.documentId)
    if (!otClient || !otClient.isSynchronized) return

    cursorDirtyRef.current = false
    stomp.publish({
      destination: `/app/rooms/${roomId}/cursor`,
      body: JSON.stringify({ ...cursor, revision: otClient.revision }),
    })
  }, [roomId])

  const sendCursor = useCallback((documentId: string, anchor: number, head: number) => {
    localCursorRef.current = { documentId, anchor, head }
    cursorDirtyRef.current = true
    if (cursorTimerRef.current) return
    cursorTimerRef.current = setTimeout(() => {
      cursorTimerRef.current = null
      flushCursor()
    }, CURSOR_THROTTLE_MS)
  }, [flushCursor])

  // Returns false if the cursor is from a revision this tab has not received yet
  const showRemoteCursor = useCallback((message: RemoteCursorMessage): boolean => {
    const otClient = otClientsRef.current.get(message.documentId)
    if (!otClient) return true

    if (message.revision > otClient.revision) return false

    const anchor = otClient.transformRemoteIndex(message.anchor, message.revision)
    const head = otClient.transformRemoteIndex(message.head, message.revision)
    // Too old to transform; the next update from that user will place it correctly
    if (anchor === null || head === null) return true

    callbacksRef.current.onRemoteCursor({
      clientId: message.clientId,
      username: message.username,
      documentId: message.documentId,
      anchor,
      head,
    })
    return true
  }, [])

  const retryEarlyCursors = useCallback(() => {
    for (const [id, message] of earlyCursorsRef.current) {
      if (showRemoteCursor(message)) earlyCursorsRef.current.delete(id)
    }
  }, [showRemoteCursor])

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
        (operation) => {
          // A remote edit shifts our caret in the editor without a cursor event, so shift
          // the copy we send in the same way
          const cursor = localCursorRef.current
          if (cursor?.documentId === doc.id) {
            cursor.anchor = operation.transformIndex(cursor.anchor)
            cursor.head = operation.transformIndex(cursor.head)
          }
          callbacksRef.current.onRemoteOperation(doc.id, operation)
        },
        flushCursor
      ))
    }
    otClientsRef.current = clients
    syncingRef.current = false

    const backlog = queuedRef.current
    queuedRef.current = []
    for (const message of backlog) {
      clients.get(message.documentId)?.receive(message)
    }
    retryEarlyCursors()

    const stomp = stompRef.current
    if (connectedRef.current && stomp?.connected) {
      // Joining makes the server send the member list to everyone, and every tab answers it
      // with its cursor, so after a (re)load we see all cursors without waiting for them to move
      stomp.publish({
        destination: `/app/rooms/${roomId}/presence`,
        body: JSON.stringify({ clientId }),
      })
    }

    setReady(connectedRef.current)
  }, [documents, roomId, clientId, requestResync, flushCursor, retryEarlyCursors])

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
      retryEarlyCursors()

      // A missing revision normally arrives within milliseconds; if not, reload
      if (otClient.hasGap && !gapTimerRef.current) {
        gapTimerRef.current = setTimeout(() => {
          gapTimerRef.current = null
          if (otClient.hasGap) requestResync("missed an update")
        }, GAP_TIMEOUT_MS)
      }
    }

    const client = new Client({
      brokerURL: WEBSOCKET_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,

      onConnect: () => {
        client.subscribe(`/topic/rooms/${roomId}/code`, (frame) => {
          handleBroadcast(JSON.parse(frame.body))
        })
        client.subscribe(`/topic/rooms/${roomId}/presence`, (frame) => {
          setMembers(JSON.parse(frame.body).members)
          // Someone joined or left: send our cursor again so a newcomer sees it
          cursorDirtyRef.current = true
          flushCursor()
        })
        client.subscribe(`/topic/rooms/${roomId}/cursors`, (frame) => {
          const message: RemoteCursorMessage = JSON.parse(frame.body)
          if (message.clientId === clientId) return
          earlyCursorsRef.current.delete(message.clientId)
          if (syncingRef.current || !showRemoteCursor(message)) {
            earlyCursorsRef.current.set(message.clientId, message)
          }
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
        setMembers([])
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
      if (cursorTimerRef.current) clearTimeout(cursorTimerRef.current)
      cursorTimerRef.current = null
      connectedRef.current = false
      stompRef.current = null
      client.deactivate()
    }
  }, [enabled, roomId, clientId, requestResync, flushCursor, showRemoteCursor, retryEarlyCursors])

  const applyLocalOperation = useCallback((documentId: string, operation: TextOperation) => {
    otClientsRef.current.get(documentId)?.applyLocal(operation)
  }, [])

  return { ready, members, applyLocalOperation, sendCursor }
}

export default useCodeSync
