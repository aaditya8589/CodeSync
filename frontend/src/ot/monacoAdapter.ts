import { TextOperation } from "./textOperation"

// Monaco reports each edit as "replace rangeLength characters at rangeOffset with text".
// All changes in one event are relative to the text before the event.
export interface ContentChange {
  rangeOffset: number
  rangeLength: number
  text: string
}

export interface OffsetEdit {
  offset: number
  length: number
  text: string
}

export function operationFromChanges(
  changes: readonly ContentChange[],
  lengthAfter: number
): TextOperation {
  const sorted = [...changes].sort((a, b) => a.rangeOffset - b.rangeOffset)
  const lengthBefore = sorted.reduce(
    (length, change) => length + change.rangeLength - change.text.length,
    lengthAfter
  )

  const operation = new TextOperation()
  let position = 0

  for (const change of sorted) {
    operation.retain(change.rangeOffset - position)
    operation.delete(change.rangeLength)
    operation.insert(change.text)
    position = change.rangeOffset + change.rangeLength
  }

  operation.retain(lengthBefore - position)
  return operation
}

// The reverse: non-overlapping edits, all relative to the text before the operation,
// which is the form Monaco's pushEditOperations expects.
export function editsFromOperation(operation: TextOperation): OffsetEdit[] {
  const edits: OffsetEdit[] = []
  let position = 0

  const editEndingAt = (offset: number) => {
    const last = edits[edits.length - 1]
    return last && last.offset + last.length === offset ? last : undefined
  }

  for (const component of operation.ops) {
    if (typeof component === "string") {
      const last = editEndingAt(position)
      if (last) last.text += component
      else edits.push({ offset: position, length: 0, text: component })
    } else if (component > 0) {
      position += component
    } else {
      const last = editEndingAt(position)
      if (last) last.length -= component
      else edits.push({ offset: position, length: -component, text: "" })
      position -= component
    }
  }

  return edits
}
