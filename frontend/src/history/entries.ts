import type { DocumentHistory } from "../services/historyService"

export interface HistoryEntry {
  revision: number
  title: string
  detail: string
}

const timeFormat: Intl.DateTimeFormatOptions = {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
}

// Each version is shown as the state after its last edit; the oldest entry is the text
// the history started from
export function historyEntries(history: DocumentHistory): HistoryEntry[] {
  const entries: HistoryEntry[] = history.versions.map((version, index) => ({
    revision: version.toRevision,
    title: new Date(version.endedAt).toLocaleString([], timeFormat) + (index === 0 ? " (latest)" : ""),
    detail: `${version.authors.join(", ")} · ${version.edits} edit${version.edits === 1 ? "" : "s"}`,
  }))

  if (history.historyStartRevision !== null) {
    entries.push({
      revision: history.historyStartRevision,
      title: "Start of history",
      detail: `revision ${history.historyStartRevision}`,
    })
  }
  return entries
}
