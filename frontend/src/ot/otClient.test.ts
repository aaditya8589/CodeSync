import { describe, expect, it } from "vitest"
import { OtClient, type ServerOperation } from "./otClient"
import { TextOperation } from "./textOperation"

const SEED = 7
const SIMULATIONS = 500

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

function randomOperation(next: (max: number) => number, text: string): TextOperation {
  const op = new TextOperation()
  let position = 0
  while (position < text.length) {
    const chunk = 1 + next(Math.min(text.length - position, 4))
    const kind = next(4)
    if (kind === 0) op.delete(chunk)
    else if (kind === 1) op.insert(String.fromCharCode(97 + next(26)))
    else op.retain(chunk)
    if (kind !== 1) position += chunk
  }
  if (next(2) === 0) op.insert(String.fromCharCode(65 + next(26)))
  return op
}

interface ClientMessage {
  baseRevision: number
  operation: TextOperation
  clientId: string
}

// Same algorithm as the Java DocumentService: rebase past missed operations, apply, broadcast.
class FakeServer {
  text: string
  revision: number
  private readonly startRevision: number
  private readonly log: TextOperation[] = []
  readonly outboxes: ServerOperation[][] = []

  constructor(text: string, revision: number) {
    this.text = text
    this.revision = revision
    this.startRevision = revision
  }

  receive(message: ClientMessage): void {
    let operation = message.operation
    for (const applied of this.log.slice(message.baseRevision - this.startRevision)) {
      operation = TextOperation.transform(applied, operation)[1]
    }
    this.text = operation.apply(this.text)
    this.revision++
    this.log.push(operation)
    for (const outbox of this.outboxes) {
      outbox.push({ revision: this.revision, operation: operation.toJSON(), clientId: message.clientId })
    }
  }
}

function simulate(seed: number, clientCount: number, steps: number) {
  const next = seededRandom(seed)
  const server = new FakeServer("hello world", 5)

  const clients = Array.from({ length: clientCount }, (_, index) => {
    const outbox: ServerOperation[] = []
    server.outboxes.push(outbox)
    const toServer: ClientMessage[] = []
    const id = `client-${index}`
    const peer = {
      id,
      text: server.text,
      toServer,
      fromServer: outbox,
      ot: new OtClient(
        server.revision,
        id,
        (operation, baseRevision) => toServer.push({ operation, baseRevision, clientId: id }),
        (operation) => { peer.text = operation.apply(peer.text) }
      ),
    }
    return peer
  })

  for (let step = 0; step < steps; step++) {
    const client = clients[next(clientCount)]
    const action = next(3)

    if (action === 0) {
      const operation = randomOperation(next, client.text)
      client.text = operation.apply(client.text)
      client.ot.applyLocal(operation)
    } else if (action === 1 && client.toServer.length > 0) {
      server.receive(client.toServer.shift()!)
    } else if (client.fromServer.length > 0) {
      // Deliver a random one of the first few: simulates out-of-order arrival
      const index = next(Math.min(client.fromServer.length, 3))
      client.ot.receive(client.fromServer.splice(index, 1)[0])
    }
  }

  // Drain the network until everything is delivered
  let moved = true
  while (moved) {
    moved = false
    for (const client of clients) {
      while (client.toServer.length > 0) {
        server.receive(client.toServer.shift()!)
        moved = true
      }
      while (client.fromServer.length > 0) {
        client.ot.receive(client.fromServer.shift()!)
        moved = true
      }
    }
  }

  return { server, clients }
}

describe("OtClient", () => {
  it("sends only one operation at a time and composes the rest", () => {
    const sent: [TextOperation, number][] = []
    const client = new OtClient(10, "me", (op, base) => sent.push([op, base]), () => {})

    client.applyLocal(new TextOperation().retain(3).insert("a"))
    client.applyLocal(new TextOperation().retain(4).insert("b"))
    client.applyLocal(new TextOperation().retain(5).insert("c"))

    expect(sent).toHaveLength(1)
    expect(client.isSynchronized).toBe(false)

    client.receive({ revision: 11, operation: [3, "a"], clientId: "me" })

    expect(sent).toHaveLength(2)
    expect(sent[1][0].toJSON()).toEqual([4, "bc"])
    expect(sent[1][1]).toBe(11)
  })

  it("ignores no-op edits", () => {
    const sent: unknown[] = []
    const client = new OtClient(0, "me", () => sent.push(1), () => {})
    client.applyLocal(new TextOperation().retain(5))
    expect(sent).toHaveLength(0)
  })

  it("holds an early message until the missing revision arrives", () => {
    const applied: string[] = []
    let text = "ab"
    const client = new OtClient(0, "me", () => {}, (op) => {
      text = op.apply(text)
      applied.push(text)
    })

    client.receive({ revision: 2, operation: [3, "Z"], clientId: "other" })
    expect(applied).toEqual([])
    expect(client.hasGap).toBe(true)

    client.receive({ revision: 1, operation: [2, "Y"], clientId: "other" })
    expect(applied).toEqual(["abY", "abYZ"])
    expect(client.hasGap).toBe(false)
  })

  it("ignores duplicate messages", () => {
    let count = 0
    const client = new OtClient(0, "me", () => {}, () => count++)
    const message = { revision: 1, operation: [0 + 1, "x"], clientId: "other" }
    client.receive({ ...message, operation: ["x"] })
    client.receive({ ...message, operation: ["x"] })
    expect(count).toBe(1)
  })

  it("breaks same-position ties the same way as the server", () => {
    const { server, clients } = (() => {
      const server = new FakeServer("ab", 0)
      const outA: ServerOperation[] = []
      const outB: ServerOperation[] = []
      server.outboxes.push(outA, outB)
      const a = { text: "ab", out: outA, ot: null as unknown as OtClient }
      const b = { text: "ab", out: outB, ot: null as unknown as OtClient }
      const toServer: ClientMessage[] = []
      a.ot = new OtClient(0, "a", (operation, baseRevision) => toServer.push({ operation, baseRevision, clientId: "a" }), (op) => { a.text = op.apply(a.text) })
      b.ot = new OtClient(0, "b", (operation, baseRevision) => toServer.push({ operation, baseRevision, clientId: "b" }), (op) => { b.text = op.apply(b.text) })

      const opA = new TextOperation().retain(1).insert("A").retain(1)
      const opB = new TextOperation().retain(1).insert("B").retain(1)
      a.text = opA.apply(a.text)
      a.ot.applyLocal(opA)
      b.text = opB.apply(b.text)
      b.ot.applyLocal(opB)

      for (const message of toServer) server.receive(message)
      for (const message of outA) a.ot.receive(message)
      for (const message of outB) b.ot.receive(message)
      return { server, clients: [a, b] }
    })()

    expect(server.text).toBe("aABb")
    expect(clients.map((c) => c.text)).toEqual(["aABb", "aABb"])
  })

  it(`converges in ${SIMULATIONS} random simulations with 3 clients, delays and reordering`, () => {
    for (let run = 0; run < SIMULATIONS; run++) {
      const { server, clients } = simulate(SEED * 1000 + run, 3, 80)

      for (const client of clients) {
        expect(client.text, `run ${run}, ${client.id}`).toBe(server.text)
        expect(client.ot.revision, `run ${run}, ${client.id}`).toBe(server.revision)
        expect(client.ot.isSynchronized).toBe(true)
        expect(client.ot.hasGap).toBe(false)
      }
    }
  })
})
