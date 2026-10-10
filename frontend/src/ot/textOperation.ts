// Mirror of the backend's com.codesync.backend.ot.TextOperation.
// Both sides must produce identical results, so change them together.
//
// Wire format, one entry per component:
//   positive number = retain n
//   string          = insert text
//   negative number = delete n
export type OperationJson = (number | string)[]

const isRetain = (c: number | string | undefined): c is number =>
  typeof c === "number" && c > 0
const isDelete = (c: number | string | undefined): c is number =>
  typeof c === "number" && c < 0
const isInsert = (c: number | string | undefined): c is string =>
  typeof c === "string"

export class TextOperation {
  readonly ops: OperationJson = []
  baseLength = 0
  targetLength = 0

  retain(count: number): this {
    if (count < 0) throw new Error("retain count must not be negative")
    if (count === 0) return this

    this.baseLength += count
    this.targetLength += count

    const last = this.ops[this.ops.length - 1]
    if (isRetain(last)) {
      this.ops[this.ops.length - 1] = last + count
    } else {
      this.ops.push(count)
    }
    return this
  }

  insert(text: string): this {
    if (text === "") return this

    this.targetLength += text.length

    const ops = this.ops
    const last = ops[ops.length - 1]
    if (isInsert(last)) {
      ops[ops.length - 1] = last + text
    } else if (isDelete(last)) {
      // Normal form: insert before delete at the same position
      const beforeDelete = ops[ops.length - 2]
      if (isInsert(beforeDelete)) {
        ops[ops.length - 2] = beforeDelete + text
      } else {
        ops[ops.length - 1] = text
        ops.push(last)
      }
    } else {
      ops.push(text)
    }
    return this
  }

  delete(count: number): this {
    if (count < 0) throw new Error("delete count must not be negative")
    if (count === 0) return this

    this.baseLength += count

    const last = this.ops[this.ops.length - 1]
    if (isDelete(last)) {
      this.ops[this.ops.length - 1] = last - count
    } else {
      this.ops.push(-count)
    }
    return this
  }

  isNoop(): boolean {
    return this.ops.length === 0 || (this.ops.length === 1 && isRetain(this.ops[0]))
  }

  apply(text: string): string {
    if (text.length !== this.baseLength) {
      throw new Error(
        `Operation expects text of length ${this.baseLength} but got ${text.length}`
      )
    }

    const parts: string[] = []
    let position = 0

    for (const op of this.ops) {
      if (isRetain(op)) {
        parts.push(text.slice(position, position + op))
        position += op
      } else if (isInsert(op)) {
        parts.push(op)
      } else {
        position -= op
      }
    }

    return parts.join("")
  }

  // Where a position in the old text ends up in the new text. An insert exactly at the
  // position pushes it right, so a remote cursor stays after text typed at its spot.
  transformIndex(index: number): number {
    let remaining = index
    let result = index

    for (const op of this.ops) {
      if (isRetain(op)) {
        remaining -= op
      } else if (isInsert(op)) {
        result += op.length
      } else {
        result -= Math.min(remaining, -op)
        remaining += op
      }
      if (remaining < 0) break
    }

    return result
  }

  toJSON(): OperationJson {
    return this.ops
  }

  static fromJSON(json: OperationJson): TextOperation {
    const op = new TextOperation()
    for (const c of json) {
      if (isRetain(c)) op.retain(c)
      else if (isDelete(c)) op.delete(-c)
      else if (isInsert(c)) op.insert(c)
      else throw new Error(`Invalid operation component: ${String(c)}`)
    }
    return op
  }

  static compose(first: TextOperation, second: TextOperation): TextOperation {
    if (first.targetLength !== second.baseLength) {
      throw new Error(
        `Cannot compose: first produces length ${first.targetLength} but second expects ${second.baseLength}`
      )
    }

    const result = new TextOperation()
    const a = new Cursor(first.ops)
    const b = new Cursor(second.ops)

    while (a.hasNext() || b.hasNext()) {
      const ca = a.peek()
      const cb = b.peek()

      if (isDelete(ca)) {
        result.delete(-ca)
        a.next()
        continue
      }
      if (isInsert(cb)) {
        result.insert(cb)
        b.next()
        continue
      }
      if (ca === undefined || cb === undefined) {
        throw new Error("Operations do not line up while composing")
      }

      if (isRetain(ca) && isRetain(cb)) {
        const n = Math.min(ca, cb)
        result.retain(n)
        a.consume(n)
        b.consume(n)
      } else if (isInsert(ca) && isDelete(cb)) {
        const n = Math.min(ca.length, -cb)
        a.consume(n)
        b.consume(n)
      } else if (isInsert(ca) && isRetain(cb)) {
        const n = Math.min(ca.length, cb)
        result.insert(ca.slice(0, n))
        a.consume(n)
        b.consume(n)
      } else if (isRetain(ca) && isDelete(cb)) {
        const n = Math.min(ca, -cb)
        result.delete(n)
        a.consume(n)
        b.consume(n)
      } else {
        throw new Error(`Unexpected components while composing: ${ca}, ${cb}`)
      }
    }

    return result
  }

  // Returns [a', b'] so that apply(apply(s, a), b') === apply(apply(s, b), a').
  // On inserts at the same position, a's text goes first. The server always
  // passes the operation it already applied as a.
  static transform(a: TextOperation, b: TextOperation): [TextOperation, TextOperation] {
    if (a.baseLength !== b.baseLength) {
      throw new Error(
        `Cannot transform: operations apply to different lengths ${a.baseLength} and ${b.baseLength}`
      )
    }

    const aPrime = new TextOperation()
    const bPrime = new TextOperation()
    const ca = new Cursor(a.ops)
    const cb = new Cursor(b.ops)

    while (ca.hasNext() || cb.hasNext()) {
      const x = ca.peek()
      const y = cb.peek()

      if (isInsert(x)) {
        aPrime.insert(x)
        bPrime.retain(x.length)
        ca.next()
        continue
      }
      if (isInsert(y)) {
        aPrime.retain(y.length)
        bPrime.insert(y)
        cb.next()
        continue
      }
      if (x === undefined || y === undefined) {
        throw new Error("Operations do not line up while transforming")
      }

      if (isRetain(x) && isRetain(y)) {
        const n = Math.min(x, y)
        aPrime.retain(n)
        bPrime.retain(n)
        ca.consume(n)
        cb.consume(n)
      } else if (isDelete(x) && isDelete(y)) {
        const n = Math.min(-x, -y)
        ca.consume(n)
        cb.consume(n)
      } else if (isDelete(x) && isRetain(y)) {
        const n = Math.min(-x, y)
        aPrime.delete(n)
        ca.consume(n)
        cb.consume(n)
      } else if (isRetain(x) && isDelete(y)) {
        const n = Math.min(x, -y)
        bPrime.delete(n)
        ca.consume(n)
        cb.consume(n)
      } else {
        throw new Error(`Unexpected components while transforming: ${x}, ${y}`)
      }
    }

    return [aPrime, bPrime]
  }
}

class Cursor {
  private readonly ops: OperationJson
  private index = 0
  private current: number | string | undefined

  constructor(ops: OperationJson) {
    this.ops = ops
    this.current = ops[0]
  }

  hasNext(): boolean {
    return this.current !== undefined
  }

  peek(): number | string | undefined {
    return this.current
  }

  next(): void {
    this.index++
    this.current = this.ops[this.index]
  }

  consume(n: number): void {
    const c = this.current
    if (c === undefined) throw new Error("Nothing to consume")

    const length = isInsert(c) ? c.length : Math.abs(c)
    if (n > length) throw new Error("Consuming more than the component holds")

    if (n === length) {
      this.next()
    } else if (isInsert(c)) {
      this.current = c.slice(n)
    } else if (isRetain(c)) {
      this.current = c - n
    } else {
      this.current = c + n
    }
  }
}