import { TextOperation, type OperationJson } from "./textOperation"

export interface ServerOperation {
  revision: number
  operation: OperationJson
  clientId: string
}

// One document's sync state in one browser tab. At most one operation is in flight;
// edits made while waiting are composed into a buffer and sent after the ack.
export class OtClient {
  revision: number
  private pending: TextOperation | null = null
  private buffer: TextOperation | null = null
  private readonly early = new Map<number, ServerOperation>()
  private readonly clientId: string
  private readonly send: (operation: TextOperation, baseRevision: number) => void
  private readonly applyRemote: (operation: TextOperation) => void

  constructor(
    revision: number,
    clientId: string,
    send: (operation: TextOperation, baseRevision: number) => void,
    applyRemote: (operation: TextOperation) => void
  ) {
    this.revision = revision
    this.clientId = clientId
    this.send = send
    this.applyRemote = applyRemote
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

  private process(message: ServerOperation): void {
    if (message.clientId === this.clientId) {
      this.revision = message.revision
      this.pending = this.buffer
      this.buffer = null
      if (this.pending) this.send(this.pending, this.revision)
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
