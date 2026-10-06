import { describe, expect, it } from "vitest"
import { TextOperation } from "./textOperation"

// Fixed seed so a failing random case fails the same way every run
const SEED = 42
const RANDOM_RUNS = 10_000

function seededRandom(seed: number): (max: number) => number {
  let state = seed >>> 0
  return (max: number) => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    const value = ((t ^ (t >>> 14)) >>> 0) / 4294967296
    return Math.floor(value * max)
  }
}

function randomText(next: (max: number) => number, length: number): string {
  let text = ""
  for (let i = 0; i < length; i++) {
    text += String.fromCharCode(97 + next(26))
  }
  return text
}

function randomOperation(next: (max: number) => number, text: string): TextOperation {
  const op = new TextOperation()
  let remaining = text.length

  while (remaining > 0) {
    const chunk = 1 + next(Math.min(remaining, 5))
    const kind = next(3)
    if (kind === 0) op.retain(chunk)
    else if (kind === 1) op.delete(chunk)
    else op.insert(randomText(next, 1 + next(3)))
    remaining = text.length - op.baseLength
  }

  if (next(2) === 0) {
    op.insert(randomText(next, 1 + next(3)))
  }
  return op
}

describe("apply", () => {
  it("inserts in the middle", () => {
    const op = new TextOperation().retain(6).insert("big ").retain(5)
    expect(op.apply("hello world")).toBe("hello big world")
  })

  it("deletes", () => {
    expect(new TextOperation().retain(5).delete(6).apply("hello world")).toBe("hello")
  })

  it("replaces", () => {
    const op = new TextOperation().retain(6).delete(5).insert("there")
    expect(op.apply("hello world")).toBe("hello there")
  })

  it("rejects text of the wrong length", () => {
    expect(() => new TextOperation().retain(3).apply("too long")).toThrow()
  })

  it("tracks lengths", () => {
    const op = new TextOperation().retain(2).insert("abc").delete(4)
    expect(op.baseLength).toBe(6)
    expect(op.targetLength).toBe(5)
  })
})

describe("normal form", () => {
  it("merges adjacent components of the same kind", () => {
    const op = new TextOperation().retain(1).retain(2).insert("a").insert("b").delete(1).delete(1)
    expect(op.toJSON()).toEqual([3, "ab", -2])
  })

  it("places insert before delete at the same position", () => {
    expect(new TextOperation().delete(2).insert("x").toJSON()).toEqual(["x", -2])
  })
})

describe("JSON", () => {
  it("round-trips through the wire format", () => {
    const op = new TextOperation().retain(3).insert("hi").delete(2).retain(1)
    const copy = TextOperation.fromJSON(JSON.parse(JSON.stringify(op)))
    expect(copy.toJSON()).toEqual(op.toJSON())
    expect(copy.baseLength).toBe(op.baseLength)
    expect(copy.targetLength).toBe(op.targetLength)
  })

  it("rejects invalid components", () => {
    expect(() => TextOperation.fromJSON([0])).toThrow()
  })
})

describe("transform", () => {
  it("handles concurrent inserts at different positions", () => {
    const a = new TextOperation().insert("X").retain(3)
    const b = new TextOperation().retain(3).insert("Y")
    const [aPrime, bPrime] = TextOperation.transform(a, b)
    expect(bPrime.apply(a.apply("abc"))).toBe("XabcY")
    expect(aPrime.apply(b.apply("abc"))).toBe("XabcY")
  })

  it("puts the first operation's insert first on a tie", () => {
    const a = new TextOperation().retain(1).insert("A").retain(1)
    const b = new TextOperation().retain(1).insert("B").retain(1)
    const [aPrime, bPrime] = TextOperation.transform(a, b)
    expect(bPrime.apply(a.apply("ab"))).toBe("aABb")
    expect(aPrime.apply(b.apply("ab"))).toBe("aABb")
  })

  it("keeps an insert made inside text the other deleted", () => {
    const text = "hello world"
    const a = new TextOperation().retain(5).delete(6)
    const b = new TextOperation().retain(8).insert("!!").retain(3)
    const [aPrime, bPrime] = TextOperation.transform(a, b)
    expect(bPrime.apply(a.apply(text))).toBe("hello!!")
    expect(aPrime.apply(b.apply(text))).toBe("hello!!")
  })

  it("handles overlapping deletes", () => {
    const a = new TextOperation().retain(1).delete(3).retain(2)
    const b = new TextOperation().retain(2).delete(3).retain(1)
    const [aPrime, bPrime] = TextOperation.transform(a, b)
    expect(bPrime.apply(a.apply("abcdef"))).toBe("af")
    expect(aPrime.apply(b.apply("abcdef"))).toBe("af")
  })

  it("rejects operations on different lengths", () => {
    expect(() =>
      TextOperation.transform(new TextOperation().retain(3), new TextOperation().retain(4))
    ).toThrow()
  })
})

describe("randomized properties", () => {
  it("converges for random concurrent edits", () => {
    const next = seededRandom(SEED)
    for (let run = 0; run < RANDOM_RUNS; run++) {
      const text = randomText(next, next(20))
      const a = randomOperation(next, text)
      const b = randomOperation(next, text)
      const [aPrime, bPrime] = TextOperation.transform(a, b)
      expect(bPrime.apply(a.apply(text)), `text="${text}" a=${JSON.stringify(a)} b=${JSON.stringify(b)}`)
        .toBe(aPrime.apply(b.apply(text)))
    }
  })

  it("compose equals applying in sequence", () => {
    const next = seededRandom(SEED)
    for (let run = 0; run < RANDOM_RUNS; run++) {
      const text = randomText(next, next(20))
      const first = randomOperation(next, text)
      const middle = first.apply(text)
      const second = randomOperation(next, middle)
      expect(TextOperation.compose(first, second).apply(text)).toBe(second.apply(middle))
    }
  })
})