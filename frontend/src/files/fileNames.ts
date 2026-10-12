import type { RoomDocument } from "../services/documentService"

// A rename arrives as a message and is shown at once, without reloading the documents (a reload
// would replace the editor's text and the sync state). Each rename is numbered. A reload that
// started after rename n already contains it, so its result replaces renames up to n; renames
// that arrive while it is in flight may be missing from it and are kept on top.
export interface NameOverride {
  name: string
  seq: number
}

export type NameOverrides = ReadonlyMap<string, NameOverride>

export function withRename(overrides: NameOverrides, documentId: string, name: string, seq: number): NameOverrides {
  const next = new Map(overrides)
  next.set(documentId, { name, seq })
  return next
}

/** What is left after applying a snapshot whose request started when `startedAtSeq` renames had arrived. */
export function afterSnapshot(overrides: NameOverrides, startedAtSeq: number): NameOverrides {
  const next = new Map<string, NameOverride>()
  for (const [id, override] of overrides) {
    if (override.seq > startedAtSeq) next.set(id, override)
  }
  return next
}

const byName = (a: RoomDocument, b: RoomDocument) =>
  a.fileName.localeCompare(b.fileName, undefined, { sensitivity: "base" }) || a.fileName.localeCompare(b.fileName)

/** The documents with their current names, sorted by name. Returns the same objects when nothing was renamed. */
export function withCurrentNames(documents: RoomDocument[], overrides: NameOverrides): RoomDocument[] {
  return documents
    .map((doc) => {
      const override = overrides.get(doc.id)
      return override && override.name !== doc.fileName ? { ...doc, fileName: override.name } : doc
    })
    .sort(byName)
}
