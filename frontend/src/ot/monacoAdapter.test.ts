import { describe, expect, it } from "vitest"
import { editsFromOperation, operationFromChanges, type ContentChange } from "./monacoAdapter"
import { TextOperation } from "./textOperation"

const RUNS = 5_000

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

function randomText(next: (max: number) => number, length: number): string {
  let text = ""
  for (let i = 0; i < length; i++) text += String.fromCharCode(97 + next(26))
  return text
}

// Applies edits that are relative to the original text, last one first
function applyEdits(text: string, edits: { offset: number; length: number; text: string }[]): string {
  return [...edits]
    .sort((a, b) => b.offset - a.offset)
    .reduce((t, e) => t.slice(0, e.offset) + e.text + t.slice(e.offset + e.length), text)
}

describe("operationFromChanges", () => {
  it("converts a single keystroke", () => {
    const op = operationFromChanges([{ rangeOffset: 5, rangeLength: 0, text: "x" }], 11)
    expect(op.toJSON()).toEqual([5, "x", 5])
  })

  it("converts a selection replaced by typing", () => {
    const op = operationFromChanges([{ rangeOffset: 2, rangeLength: 3, text: "Z" }], 8)
    expect(op.apply("abcdefghij")).toBe("abZfghij")
  })

  it("converts multi-cursor edits given in any order", () => {
    const changes: ContentChange[] = [
      { rangeOffset: 8, rangeLength: 0, text: "!" },
      { rangeOffset: 0, rangeLength: 0, text: "!" },
    ]
    expect(operationFromChanges(changes, 12).apply("abcdefghij")).toBe("!abcdefgh!ij")
  })

  it("matches Monaco for random non-overlapping changes", () => {
    const next = seededRandom(11)
    for (let run = 0; run < RUNS; run++) {
      const before = randomText(next, next(30))
      const changes: ContentChange[] = []
      let position = 0
      while (position <= before.length && next(3) !== 0) {
        const offset = position + next(before.length - position + 1)
        const rangeLength = next(Math.min(4, before.length - offset) + 1)
        changes.push({ rangeOffset: offset, rangeLength, text: next(2) ? randomText(next, next(4)) : "" })
        position = offset + rangeLength + 1
      }
      const after = applyEdits(before, changes.map((c) => ({ offset: c.rangeOffset, length: c.rangeLength, text: c.text })))
      expect(operationFromChanges(changes.reverse(), after.length).apply(before)).toBe(after)
    }
  })
})

describe("editsFromOperation", () => {
  it("turns an insert into a zero-length edit", () => {
    expect(editsFromOperation(new TextOperation().retain(3).insert("x").retain(2)))
      .toEqual([{ offset: 3, length: 0, text: "x" }])
  })

  it("merges an adjacent insert and delete into one replacement", () => {
    expect(editsFromOperation(new TextOperation().retain(1).insert("Z").delete(3).retain(1)))
      .toEqual([{ offset: 1, length: 3, text: "Z" }])
  })

  it("produces edits equivalent to the operation for random operations", () => {
    const next = seededRandom(12)
    for (let run = 0; run < RUNS; run++) {
      const text = randomText(next, next(30))
      const op = new TextOperation()
      let remaining = text.length
      while (remaining > 0) {
        const chunk = 1 + next(Math.min(remaining, 4))
        const kind = next(3)
        if (kind === 0) op.retain(chunk)
        else if (kind === 1) op.delete(chunk)
        else op.insert(randomText(next, 1 + next(3)))
        remaining = text.length - op.baseLength
      }
      if (next(2)) op.insert("Q")

      expect(applyEdits(text, editsFromOperation(op))).toBe(op.apply(text))
    }
  })

  it("round-trips through operationFromChanges", () => {
    const op = new TextOperation().retain(2).insert("ab").delete(1).retain(3).delete(2).insert("c")
    const changes = editsFromOperation(op).map((e) => ({ rangeOffset: e.offset, rangeLength: e.length, text: e.text }))
    expect(operationFromChanges(changes, op.targetLength).toJSON()).toEqual(op.toJSON())
  })
})
