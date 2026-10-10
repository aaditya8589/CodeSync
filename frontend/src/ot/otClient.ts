import { TextOperation, type OperationJson } from "./textOperation"

export interface ServerOperation {
  revision: number
  operation: OperationJson
  clientId: string
}

// How many recent server operations are kept for transforming remote cursors that were
// sent a few revisions ago
const HISTORY_SIZE = 100

// One document's sync state in one browser tab. At most one operation is in flight;
// edits made while waiting are composed into a buffer and sent after the ack.
export class OtClient {
  revision: number
  private pending: TextOperation | null = null
  private buffer: TextOperation | null = null
  private readonly early = new Map<number, ServerOperation>()
  // Server operations in revision order; history[i] produced revision historyStart + i + 1
  private history: TextOperation[] = []
  private historyStart: number
  private readonly clientId: string
  private readonly send: (operation: TextOperation, baseRevision: number) => void
  private readonly applyRemote: (operation: TextOperation) => void
  private readonly onSynchronized: () => void

  constructor(
    revision: number,
    clientId: string,
    send: (operation: TextOperation, baseRevision: number) => void,
    applyRemote: (operation: TextOperation) => void,
    onSynchronized: () => void = () => {}
  ) {
    this.revision = revision
    this.historyStart = revision
    this.clientId = clientId
    this.send = send
    this.applyRemote = applyRemote
    this.onSynchronized = onSynchronized
  }

  get isSynchronized(): boolean {
    return this.pending === null
  }

  // True while a later revision has arrived but an earlier one is still missing
  get hasGap(): boolean {
    return this.early.size > 0
  }

  applyLocal(operation: TextOperation): void {
    if (operation.isNoop()) return

    if (this.pending === null) {
      this.pending = operation
      this.send(operation, this.revision)
    } else {
      this.buffer = this.buffer ? TextOperation.compose(this.buffer, operation) : operation
    }
  }

  // Maps a position that was valid at server revision `revision` to the text currently in
  // this editor: through the server operations since then, then through our own unconfirmed
  // edits. Returns null if that revision is not available (too old, or not reached yet).
  transformRemoteIndex(index: number, revision: number): number | null {
    if (revision > this.revision || revision < this.historyStart) return null

    let result = index
    for (const operation of this.history.slice(revision - this.historyStart)) {
      result = operation.transformIndex(result)
    }
    if (this.pending) result = this.pending.transformIndex(result)
    if (this.buffer) result = this.buffer.transformIndex(result)
    return result
  }

  receive(message: ServerOperation): void {
    if (message.revision <= this.revision) return

    this.early.set(message.revision, message)

    let next = this.early.get(this.revision + 1)
    while (next) {
      this.early.delete(next.revision)
      this.process(next)
      next = this.early.get(this.revision + 1)
    }
  }

  private remember(operation: TextOperation): void {
    this.history.push(operation)
    if (this.history.length > HISTORY_SIZE) {
      this.history.shift()
      this.historyStart++
    }
  }

  private process(message: ServerOperation): void {
    this.remember(TextOperation.fromJSON(message.operation))

    if (message.clientId === this.clientId) {
      this.revision = message.revision
      this.pending = this.buffer
      this.buffer = null
      if (this.pending) this.send(this.pending, this.revision)
      else this.onSynchronized()
      return
    }

    // The server applied this before our pending edit, so it goes first in transform,
    // exactly as on the server. Swapping the arguments breaks ties differently and diverges.
    let remote = TextOperation.fromJSON(message.operation)

    if (this.pending) {
      const [remotePrime, pendingPrime] = TextOperation.transform(remote, this.pending)
      remote = remotePrime
      this.pending = pendingPrime
    }
    if (this.buffer) {
      const [remotePrime, bufferPrime] = TextOperation.transform(remote, this.buffer)
      remote = remotePrime
      this.buffer = bufferPrime
    }

    this.revision = message.revision
    this.applyRemote(remote)
  }
}
