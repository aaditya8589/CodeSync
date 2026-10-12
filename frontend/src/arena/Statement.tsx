import { Fragment } from "react"

// Problem text uses `backticks` for code and blank lines between paragraphs; nothing else
function InlineCode({ text }: { text: string }) {
  const parts = text.split("`")
  return (
    <>
      {parts.map((part, index) =>
        index % 2 === 1 ? <code key={index}>{part}</code> : <Fragment key={index}>{part}</Fragment>
      )}
    </>
  )
}

export function Paragraphs({ text }: { text: string }) {
  return (
    <>
      {text.split(/\n\s*\n/).map((paragraph, index) => (
        <p key={index}>
          <InlineCode text={paragraph} />
        </p>
      ))}
    </>
  )
}

export function ConstraintList({ text }: { text: string }) {
  if (!text.trim()) return null
  return (
    <ul className="constraints">
      {text.split("\n").filter((line) => line.trim()).map((line) => (
        <li key={line}>
          <InlineCode text={line} />
        </li>
      ))}
    </ul>
  )
}
