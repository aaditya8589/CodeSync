import { describe, expect, it } from "vitest"
import { historyEntries } from "./entries"

const version = (fromRevision: number, toRevision: number, authors: string[], edits: number) => ({
  fromRevision,
  toRevision,
  authors,
  startedAt: "2026-10-10T09:00:00Z",
  endedAt: "2026-10-10T09:05:00Z",
  edits,
})

describe("historyEntries", () => {
  it("is empty before the first edit", () => {
    expect(historyEntries({
      documentId: "d", fileName: "main.cpp", currentRevision: 7, historyStartRevision: null, versions: [],
    })).toEqual([])
  })

  it("lists versions newest first, then the start of history", () => {
    const entries = historyEntries({
      documentId: "d",
      fileName: "main.cpp",
      currentRevision: 30,
      historyStartRevision: 4,
      versions: [version(12, 30, ["bob", "alice"], 18), version(4, 12, ["alice"], 1)],
    })

    expect(entries.map((entry) => entry.revision)).toEqual([30, 12, 4])
    expect(entries[0].title).toMatch(/\(latest\)$/)
    expect(entries[0].detail).toBe("bob, alice · 18 edits")
    expect(entries[1].detail).toBe("alice · 1 edit")
    expect(entries[2].title).toBe("Start of history")
  })
})
