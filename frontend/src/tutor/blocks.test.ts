import { describe, expect, it } from "vitest"
import { blocksOf } from "./blocks"

describe("blocksOf", () => {
  it("groups paragraphs and lists", () => {
    expect(blocksOf("First idea.\nSame paragraph.\n\n1. Sort\n2. Sweep\n- note\n\n### Last")).toEqual([
      { kind: "p", lines: ["First idea.", "Same paragraph."] },
      { kind: "ol", items: ["Sort", "Sweep"] },
      { kind: "ul", items: ["note"] },
      { kind: "p", lines: ["Last"] },
    ])
  })

  it("handles a half-written answer", () => {
    expect(blocksOf("Think about")).toEqual([{ kind: "p", lines: ["Think about"] }])
    expect(blocksOf("")).toEqual([])
  })
})
