import { useEffect, useRef, useState } from "react"
import { askTutor, getTutorStatus, type TutorLevel, type TutorStatus, type TutorTurn } from "../services/tutorService"
import TutorText from "./TutorText"

interface TutorPanelProps {
  roomId: string
  // The open file: hints are about this code
  documentId: string | null
  fileName: string | null
}

interface Shown extends TutorTurn {
  // What the student pressed or typed, as shown in the conversation
  label?: string
  codeRemoved?: boolean
}

const LEVELS: { level: TutorLevel; label: string; title: string }[] = [
  { level: "NUDGE", label: "Nudge", title: "A small push in the right direction" },
  { level: "APPROACH", label: "Approach", title: "Which idea fits, and why" },
  { level: "STEPS", label: "Steps", title: "A plan in plain words" },
  { level: "REVIEW", label: "Review my code", title: "What is wrong or slow in your file, and where" },
]

const message = (reason: unknown, fallback: string) => (reason instanceof Error && reason.message) || fallback

// Hints for the room's problem; the tutor explains, the student writes every line
function TutorPanel({ roomId, documentId, fileName }: TutorPanelProps) {
  const [status, setStatus] = useState<TutorStatus | null>(null)
  const [turns, setTurns] = useState<Shown[]>([])
  const [question, setQuestion] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const abortRef = useRef<AbortController | null>(null)
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    getTutorStatus().then(setStatus).catch(() => setStatus(null))
    return () => abortRef.current?.abort()
  }, [])

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: "end" })
  }, [turns])

  const ask = async (level: TutorLevel, label: string, text?: string) => {
    if (!documentId || busy) return
    setError("")
    setBusy(true)
    const history: TutorTurn[] = turns.map(({ role, text }) => ({ role, text }))
    const studentText = text ? `${label}: ${text}` : label
    setTurns((current) => [...current, { role: "STUDENT", text: studentText, label }, { role: "TUTOR", text: "" }])

    const controller = new AbortController()
    abortRef.current = controller
    try {
      const end = await askTutor(roomId, { documentId, level, question: text, history }, {
        onText: (piece) => setTurns((current) => {
          const next = [...current]
          const last = next[next.length - 1]
          next[next.length - 1] = { ...last, text: last.text + piece }
          return next
        }),
      }, controller.signal)
      setTurns((current) => {
        const next = [...current]
        next[next.length - 1] = { ...next[next.length - 1], codeRemoved: end.codeRemoved }
        return next
      })
      if (level === "ASK") setQuestion("")
    } catch (reason) {
      const stopped = controller.signal.aborted
      // Drop the unanswered question so the conversation sent next time stays consistent
      setTurns((current) => {
        const last = current[current.length - 1]
        return stopped && last?.text ? current : current.slice(0, -2)
      })
      if (!stopped) setError(message(reason, "The tutor could not answer"))
    } finally {
      setBusy(false)
      abortRef.current = null
      getTutorStatus().then(setStatus).catch(() => {})
    }
  }

  const submitQuestion = (event: { preventDefault: () => void }) => {
    event.preventDefault()
    const text = question.trim()
    if (text) void ask("ASK", "Question", text)
  }

  if (status && !status.enabled) {
    return (
      <section className="tutor" aria-label="Tutor">
        <p className="tutor__off">
          The tutor is switched off on this server. Whoever runs it can turn it on by setting
          {" "}<code>GEMINI_API_KEY</code>; a free key comes from Google AI Studio.
        </p>
      </section>
    )
  }

  const left = status ? `${status.leftToday} hint${status.leftToday === 1 ? "" : "s"} left today` : ""

  return (
    <section className="tutor" aria-label="Tutor">
      <p className="tutor__intro">
        Stuck? Ask for as little help as you need. The tutor reads {fileName ? <code>{fileName}</code> : "your open file"} and
        your last submission, explains ideas, and never writes code: you type every line.
      </p>

      {turns.length > 0 && (
        <ol className="tutor__turns" aria-live="polite">
          {turns.map((turn, index) => (
            <li key={index} className={`tutor__turn tutor__turn--${turn.role.toLowerCase()}`}>
              {turn.role === "STUDENT" ? (
                <span className="tutor__asked">{turn.text}</span>
              ) : turn.text ? (
                <>
                  <TutorText text={turn.text} />
                  {turn.codeRemoved && (
                    <p className="tutor__guard">Some code was taken out of this answer, so the solution stays yours to write.</p>
                  )}
                </>
              ) : (
                <span className="tutor__thinking">Thinking...</span>
              )}
            </li>
          ))}
        </ol>
      )}
      <div ref={endRef} />

      {error && <p className="message message--error" role="alert">{error}</p>}

      <div className="tutor__levels" role="group" aria-label="Ask for">
        {LEVELS.map(({ level, label, title }) => (
          <button key={level} type="button" className="button" title={title}
                  disabled={busy || !documentId} onClick={() => void ask(level, label)}>
            {label}
          </button>
        ))}
      </div>

      <form className="tutor__ask" onSubmit={submitQuestion}>
        <textarea
          className="input"
          aria-label="Question for the tutor"
          placeholder="Ask about your approach, e.g. why is my loop too slow?"
          rows={2}
          maxLength={600}
          value={question}
          disabled={busy || !documentId}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault()
              submitQuestion(event)
            }
          }}
        />
        <div className="tutor__ask-row">
          <span className="tutor__left">{!documentId ? "Open a file to ask about your code." : left}</span>
          {busy ? (
            <button type="button" className="button button--quiet" onClick={() => abortRef.current?.abort()}>Stop</button>
          ) : (
            <button type="submit" className="button button--primary" disabled={!question.trim() || !documentId}>Ask</button>
          )}
        </div>
      </form>
    </section>
  )
}

export default TutorPanel
