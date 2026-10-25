import { Fragment, type ReactNode } from "react"
import { blocksOf } from "./blocks"

// The little Markdown the tutor writes: paragraphs, lists, **bold**, _italic_ and `names`
function Inline({ text }: { text: string }) {
  const parts: ReactNode[] = []
  const pattern = /(`[^`]+`|\*\*[^*]+\*\*|_[^_]+_)/g
  let last = 0
  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > last) parts.push(text.slice(last, index))
    const token = match[0]
    if (token.startsWith("`")) parts.push(<code key={index}>{token.slice(1, -1)}</code>)
    else if (token.startsWith("**")) parts.push(<strong key={index}>{token.slice(2, -2)}</strong>)
    else parts.push(<em key={index}>{token.slice(1, -1)}</em>)
    last = index + token.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return <>{parts.map((part, i) => <Fragment key={i}>{part}</Fragment>)}</>
}

function TutorText({ text }: { text: string }) {
  return (
    <>
      {blocksOf(text).map((block, index) => {
        if (block.kind === "p") {
          return (
            <p key={index}>
              {block.lines.map((line, i) => (
                <Fragment key={i}>{i > 0 && <br />}<Inline text={line} /></Fragment>
              ))}
            </p>
          )
        }
        const List = block.kind
        return (
          <List key={index}>
            {block.items.map((item, i) => <li key={i}><Inline text={item} /></li>)}
          </List>
        )
      })}
    </>
  )
}

export default TutorText
