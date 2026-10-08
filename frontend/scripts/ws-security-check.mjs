// Tries to read and write a room's WebSocket traffic as a NON-member.
// Usage: node scripts/ws-security-check.mjs <outsider JWT> <roomId>
import { Client } from "@stomp/stompjs"

const [token, roomId] = process.argv.slice(2)

if (!token || !roomId) {
  console.log("Usage: node scripts/ws-security-check.mjs <outsider JWT> <roomId>")
  process.exit(1)
}

function attempt(name, waitMs, action) {
  return new Promise((resolve) => {
    let connected = false
    let settled = false

    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      debug: () => {},
      onConnect: () => {
        connected = true
        action(client)
        setTimeout(() => finish("ALLOWED"), waitMs)
      },
      onStompError: (frame) => finish(`REJECTED (${frame.headers.message})`),
      onWebSocketClose: () =>
        finish(connected ? "REJECTED (connection closed)" : "COULD NOT CONNECT (is the backend running?)"),
      onWebSocketError: () => {
        if (!connected) finish("COULD NOT CONNECT (is the backend running?)")
      },
    })

    setTimeout(() => {
      if (!connected) finish("COULD NOT CONNECT (timed out)")
    }, 5000)

    function finish(result) {
      if (settled) return
      settled = true
      console.log(`${name}: ${result}`)
      client.deactivate()
      resolve()
    }

    client.activate()
  })
}

await attempt("1. Subscribe to the room directly (control)", 1500, (client) => {
  client.subscribe(`/topic/rooms/${roomId}/code`, () => {})
})

await attempt("2. Subscribe with wildcard /topic/rooms/** (listening 10s, type in the room now)", 10000, (client) => {
  client.subscribe("/topic/rooms/**", (message) => {
    console.log(`   LEAKED: ${message.body.slice(0, 120)}`)
  })
})

await attempt("3. Send a fake edit straight to the broadcast topic", 1500, (client) => {
  client.publish({
    destination: `/topic/rooms/${roomId}/code`,
    body: JSON.stringify({
      roomId,
      documentId: "00000000-0000-0000-0000-000000000000",
      fileName: "main.cpp",
      content: "// INJECTED by a non-member, bypassing the server\n",
      revision: 0,
    }),
  })
})
