// Splits the tutor's Markdown-ish text into paragraphs and lists
export type Block = { kind: "p"; lines: string[] } | { kind: "ul" | "ol"; items: string[] }

export function blocksOf(text: string): Block[] {
  const blocks: Block[] = []
  for (const raw of text.split("\n")) {
    const line = raw.trimEnd()
    const bullet = line.match(/^\s*[-*+]\s+(.*)$/)
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/)
    const last = blocks[blocks.length - 1]
    if (bullet || numbered) {
      const kind = bullet ? "ul" : "ol"
      const item = (bullet ?? numbered)![1].replace(/^#+\s*/, "")
      if (last && last.kind === kind) last.items.push(item)
      else blocks.push({ kind, items: [item] })
    } else if (!line.trim()) {
      blocks.push({ kind: "p", lines: [] })
    } else if (last && last.kind === "p") {
      last.lines.push(line.replace(/^#+\s*/, ""))
    } else {
      blocks.push({ kind: "p", lines: [line.replace(/^#+\s*/, "")] })
    }
  }
  return blocks.filter((block) => block.kind !== "p" || block.lines.length > 0)
}

