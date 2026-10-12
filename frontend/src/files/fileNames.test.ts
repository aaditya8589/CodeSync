import { describe, expect, it } from "vitest"
import type { RoomDocument } from "../services/documentService"
import { afterSnapshot, withCurrentNames, withRename, type NameOverrides } from "./fileNames"

const doc = (id: string, fileName: string): RoomDocument => ({ id, fileName, content: "", updatedAt: "", revision: 0 })
const none: NameOverrides = new Map()

describe("file names", () => {
  it("shows a rename without a reload, keeping the same document and sorting by name", () => {
    const documents = [doc("a", "main.cpp"), doc("b", "solution.cpp")]
    const shown = withCurrentNames(documents, withRename(none, "b", "a.py", 1))
    expect(shown.map((d) => [d.id, d.fileName])).toEqual([["b", "a.py"], ["a", "main.cpp"]])
    expect(shown[1]).toBe(documents[0])
  })

  it("a later rename of the same file wins", () => {
    const overrides = withRename(withRename(none, "a", "x.cpp", 1), "a", "y.cpp", 2)
    expect(withCurrentNames([doc("a", "main.cpp")], overrides)[0].fileName).toBe("y.cpp")
  })

  it("a reload that started after a rename replaces it", () => {
    const overrides = withRename(none, "a", "x.cpp", 1)
    expect(afterSnapshot(overrides, 1).size).toBe(0)
  })

  it("a rename that arrives while a reload is in flight is kept", () => {
    // The reload started after 1 rename; rename 2 may or may not be in its result
    const overrides = withRename(withRename(none, "a", "x.cpp", 1), "b", "z.py", 2)
    const kept = afterSnapshot(overrides, 1)
    expect([...kept.keys()]).toEqual(["b"])
    // Applied on top of an older snapshot that still has the old name
    expect(withCurrentNames([doc("a", "x.cpp"), doc("b", "old.py")], kept).map((d) => d.fileName)).toEqual(["x.cpp", "z.py"])
  })

  it("renames of files the snapshot no longer has are ignored", () => {
    expect(withCurrentNames([doc("a", "main.cpp")], withRename(none, "gone", "x.py", 3)).map((d) => d.id)).toEqual(["a"])
  })

  it("sorts without regard to case, like the file list a person expects", () => {
    expect(withCurrentNames([doc("1", "b.py"), doc("2", "A.java"), doc("3", "a.cpp")], none).map((d) => d.fileName))
      .toEqual(["a.cpp", "A.java", "b.py"])
  })
})
