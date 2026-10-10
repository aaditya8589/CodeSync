import { describe, expect, it } from "vitest"
import { OtClient, type ServerOperation } from "./otClient"
import { TextOperation } from "./textOperation"

function seededRandom(seed: number): (max: number) => number {
  let state = seed >>> 0
  return (max: number) => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return Math.floor((((t ^ (t >>> 14)) >>> 0) / 4294967296) * max)
  }
}

describe("transformIndex", () => {
  it("moves a position right when text is inserted before it", () => {
    expect(new TextOperation().insert("ab").retain(5).transformIndex(3)).toBe(5)
  })

  it("does not move a position when text is inserted after it", () => {
    expect(new TextOperation().retain(4).insert("ab").retain(1).transformIndex(3)).toBe(3)
  })

  it("moves a position right when text is inserted exactly at it", () => {
    expect(new TextOperation().retain(3).insert("ab").retain(2).transformIndex(3)).toBe(5)
  })

  it("moves a position left when text before it is deleted", () => {
    expect(new TextOperation().delete(2).retain(3).transformIndex(4)).toBe(2)
  })

  it("collapses a position inside deleted text to the start of the deletion", () => {
    expect(new TextOperation().retain(1).delete(3).retain(1).transformIndex(3)).toBe(1)
  })

  it("keeps the end of the document at the end", () => {
    const op = new TextOperation().retain(2).delete(1).insert("xyz").retain(2)
    expect(op.transformIndex(5)).toBe(op.targetLength)
  })
})

// Every character is unique, so "the cursor is just before character c" can be checked on
// any client: wherever c is now, a correctly transformed cursor must be right before it.
let nextChar = 0x4e00
const uniqueChar = () => String.fromCharCode(nextChar++)

function uniqueText(length: number): string {
  let text = ""
  for (let i = 0; i < length; i++) text += uniqueChar()
  return text
}

function randomOperation(next: (max: number) => number, text: string): TextOperation {
  const op = new TextOperation()
  let position = 0
  while (position < text.length) {
    const chunk = 1 + next(Math.min(text.length - position, 3))
    const kind = next(5)
    if (kind === 0) op.delete(chunk)
    else if (kind === 1) op.insert(uniqueChar())
    else op.retain(chunk)
    if (kind !== 1) position += chunk
  }
  if (next(2) === 0) op.insert(uniqueChar())
  return op
}

type ToServer =
  | { kind: "op"; operation: TextOperation; baseRevision: number; clientId: string }
  | { kind: "cursor"; index: number; revision: number; clientId: string; char: string | null }

type FromServer =
  | ({ kind: "op" } & ServerOperation)
  | { kind: "cursor"; index: number; revision: number; clientId: string; char: string | null }

describe("remote cursors", () => {
  it("land just before the same character on every client, with delays and reordering", () => {
    let checked = 0

    for (let run = 0; run < 300; run++) {
      const next = seededRandom(1000 + run)
      let serverText = uniqueText(12)
      let serverRevision = 7
      const log: TextOperation[] = []
      const startRevision = serverRevision

      const clients = ["a", "b", "c"].map((id) => {
        const peer = {
          id,
          text: serverText,
          toServer: [] as ToServer[],
          fromServer: [] as FromServer[],
          ot: null as unknown as OtClient,
        }
        peer.ot = new OtClient(
          serverRevision,
          id,
          (operation, baseRevision) => peer.toServer.push({ kind: "op", operation, baseRevision, clientId: id }),
          (operation) => { peer.text = operation.apply(peer.text) }
        )
        return peer
      })

      const serverReceive = (message: ToServer) => {
        if (message.kind === "op") {
          let operation = message.operation
          for (const applied of log.slice(message.baseRevision - startRevision)) {
            operation = TextOperation.transform(applied, operation)[1]
          }
          serverText = operation.apply(serverText)
          serverRevision++
          log.push(operation)
          for (const c of clients) {
            c.fromServer.push({ kind: "op", revision: serverRevision, operation: operation.toJSON(), clientId: message.clientId })
          }
        } else {
          for (const c of clients) if (c.id !== message.clientId) c.fromServer.push(message)
        }
      }

      const deliver = (client: (typeof clients)[number], message: FromServer) => {
        if (message.kind === "op") {
          client.ot.receive(message)
          return
        }
        const index = client.ot.transformRemoteIndex(message.index, message.revision)
        if (index === null) return
        expect(index).toBeGreaterThanOrEqual(0)
        expect(index).toBeLessThanOrEqual(client.text.length)
        // Only checkable while the character the cursor sat before still exists
        if (message.char !== null && client.text.includes(message.char)) {
          expect(index, `run ${run}`).toBe(client.text.indexOf(message.char))
          checked++
        }
      }

      for (let step = 0; step < 120; step++) {
        const client = clients[next(3)]
        const action = next(4)

        if (action === 0) {
          const operation = randomOperation(next, client.text)
          client.text = operation.apply(client.text)
          client.ot.applyLocal(operation)
        } else if (action === 1 && client.ot.isSynchronized) {
          // Cursors are only sent from a synchronized client, so the index is in server coordinates
          const index = next(client.text.length + 1)
          const char = index < client.text.length ? client.text[index] : null
          client.toServer.push({ kind: "cursor", index, revision: client.ot.revision, clientId: client.id, char })
        } else if (action === 2 && client.toServer.length > 0) {
          serverReceive(client.toServer.shift()!)
        } else if (client.fromServer.length > 0) {
          const pick = next(Math.min(client.fromServer.length, 3))
          deliver(client, client.fromServer.splice(pick, 1)[0])
        }
      }
    }

    // Make sure the property was actually exercised, not skipped every time
    expect(checked).toBeGreaterThan(1000)
  })

  it("returns null for a revision that is not reached yet", () => {
    const client = new OtClient(5, "me", () => {}, () => {})
    expect(client.transformRemoteIndex(3, 6)).toBeNull()
  })

  it("returns null for a revision older than the kept history", () => {
    const client = new OtClient(0, "me", () => {}, () => {})
    for (let revision = 1; revision <= 150; revision++) {
      const appendX = new TextOperation().retain(revision - 1).insert("x")
      client.receive({ revision, operation: appendX.toJSON(), clientId: "other" })
    }
    expect(client.transformRemoteIndex(0, 10)).toBeNull()
    expect(client.transformRemoteIndex(0, 140)).toBe(0)
  })

  it("calls onSynchronized when the last unconfirmed edit is acknowledged", () => {
    let synced = 0
    const client = new OtClient(0, "me", () => {}, () => {}, () => synced++)
    client.applyLocal(new TextOperation().insert("a"))
    expect(synced).toBe(0)
    client.receive({ revision: 1, operation: ["a"], clientId: "me" })
    expect(synced).toBe(1)
  })
})
