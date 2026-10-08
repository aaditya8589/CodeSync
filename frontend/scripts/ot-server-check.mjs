// Drives the OT server with two simulated clients and checks the results.
// Edits solution.cpp in the given room, then restores it.
// Usage: node scripts/ot-server-check.mjs <email> <password> <roomId>
import { Client } from "@stomp/stompjs"

const API = "http://localhost:8080"
const [email, password, roomId] = process.argv.slice(2)

if (!email || !password || !roomId) {
  console.log("Usage: node scripts/ot-server-check.mjs <email> <password> <roomId>")
  process.exit(1)
}

let failures = 0

function check(name, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${ok || !detail ? "" : `\n      ${detail}`}`)
  if (!ok) failures++
}

async function waitFor(condition, timeoutMs = 5000) {
  const start = Date.now()
  while (!condition()) {
    if (Date.now() - start > timeoutMs) return false
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
  return true
}

const loginResponse = await fetch(`${API}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
})
if (!loginResponse.ok) {
  console.log(`Login failed: ${loginResponse.status} ${await loginResponse.text()}`)
  process.exit(1)
}
const { token } = await loginResponse.json()

async function getDocument() {
  const response = await fetch(`${API}/api/rooms/${roomId}/documents`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error(`GET documents failed: ${response.status}`)
  return (await response.json()).find((doc) => doc.fileName === "solution.cpp")
}

function connect() {
  return new Promise((resolve, reject) => {
    const state = { broadcasts: [], errors: [] }
    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      debug: () => {},
      onConnect: () => {
        client.subscribe(`/topic/rooms/${roomId}/code`, (m) => state.broadcasts.push(JSON.parse(m.body)))
        client.subscribe("/user/queue/errors", (m) => state.errors.push(JSON.parse(m.body)))
        state.client = client
        setTimeout(() => resolve(state), 300)
      },
      onStompError: (frame) => reject(new Error(frame.headers.message)),
      onWebSocketError: () => reject(new Error("Could not connect. Is the backend running?")),
    })
    client.activate()
  })
}

function send(state, clientId, documentId, baseRevision, operation) {
  state.client.publish({
    destination: `/app/rooms/${roomId}/code`,
    body: JSON.stringify({ documentId, baseRevision, operation, clientId }),
  })
}

const original = await getDocument()
const id = original.id
const text = original.content
const L = text.length
const R = original.revision
console.log(`solution.cpp: ${L} characters, revision ${R}\n`)

const a = await connect()
const b = await connect()

// 1. Two clients edit at the same time, both based on revision R
send(a, "A", id, R, ["A", L])
send(b, "B", id, R, [L, "B"])
await waitFor(() => a.broadcasts.length >= 2 && b.broadcasts.length >= 2)

let doc = await getDocument()
check("concurrent edits are both kept", doc.content === "A" + text + "B",
  `got ${JSON.stringify(doc.content.slice(0, 5))}...${JSON.stringify(doc.content.slice(-5))}`)
check("revision advanced by 2", doc.revision === R + 2, `revision ${doc.revision}`)
check("both clients saw revisions in order",
  JSON.stringify(a.broadcasts.map((m) => m.revision)) === JSON.stringify([R + 1, R + 2]) &&
  JSON.stringify(b.broadcasts.map((m) => m.revision)) === JSON.stringify([R + 1, R + 2]),
  `a=${a.broadcasts.map((m) => m.revision)} b=${b.broadcasts.map((m) => m.revision)}`)
check("each edit is acknowledged with its clientId",
  a.broadcasts.map((m) => m.clientId).sort().join() === "A,B")

// 2. A stale edit, two revisions behind, is rebased by the server
send(a, "C", id, R, [L, "C"])
await waitFor(() => a.broadcasts.length >= 3)
doc = await getDocument()
const ack = a.broadcasts[2]
check("stale edit is rebased, not rejected", doc.content === "A" + text + "BC",
  `got ...${JSON.stringify(doc.content.slice(-5))}`)
check("broadcast carries the rebased operation",
  JSON.stringify(ack?.operation) === JSON.stringify([L + 2, "C"]), `got ${JSON.stringify(ack?.operation)}`)

// 3. Invalid operations are rejected privately and change nothing
const before = doc
send(a, "E1", id, R + 3, [1])
send(a, "E2", id, R + 100, [L + 3])
send(a, "E3", id, R + 3, [L + 3, "\ud83d"])
await waitFor(() => a.errors.length >= 3)
const codes = Object.fromEntries(a.errors.map((e) => [e.clientId, e.code]))
check("wrong-length operation rejected", codes.E1 === "REJECTED", JSON.stringify(a.errors))
check("future baseRevision rejected", codes.E2 === "REJECTED", JSON.stringify(a.errors))
check("half an emoji rejected", codes.E3 === "REJECTED", JSON.stringify(a.errors))
doc = await getDocument()
check("rejected operations changed nothing",
  doc.content === before.content && doc.revision === before.revision)

// 4. Restore the original text
send(a, "RESTORE", id, R + 3, [-1, L, -2])
await waitFor(() => a.broadcasts.some((m) => m.clientId === "RESTORE"))
doc = await getDocument()
check("document restored", doc.content === text)

a.client.deactivate()
b.client.deactivate()
console.log(failures === 0 ? "\nAll checks passed." : `\n${failures} check(s) failed.`)
process.exit(failures === 0 ? 0 : 1)
