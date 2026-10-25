import { useEffect, useState, type FormEvent } from "react"
import { useNavigate } from "react-router-dom"
import TopBar from "../components/TopBar"
import { listProblems, type ProblemSummary } from "../services/arenaService"
import { createInterview } from "../services/interviewService"

const DURATIONS = [30, 45, 60]
const DIFFICULTIES = ["Easy", "Medium", "Hard"]

// Setting up a mock interview: the problem (or a surprise), how long, and a call link
function NewInterview() {
  const navigate = useNavigate()
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  // "surprise:Medium" or a problem's slug
  const [choice, setChoice] = useState("surprise:Medium")
  const [minutes, setMinutes] = useState(45)
  const [meetingLink, setMeetingLink] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    listProblems().then(setProblems).catch(() => setProblems([]))
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError("")
    try {
      const surprise = choice.startsWith("surprise:")
      const created = await createInterview({
        problemSlug: surprise ? undefined : choice,
        difficulty: surprise ? choice.slice("surprise:".length) : undefined,
        durationMinutes: minutes,
        meetingLink: meetingLink.trim() || undefined,
      })
      navigate(`/rooms/${created.roomId}`)
    } catch (reason) {
      setError((reason instanceof Error && reason.message) || "Could not create the interview")
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title="Mock interview" />
      <div className="narrow-page">
        <form className="side-form new-interview" onSubmit={(event) => void submit(event)}>
          <h1>Mock interview</h1>
          <p className="side-form__hint">
            You interview, a friend solves. They join through a link you share, and see the problem
            only when you reveal it. You get a guide with the expected approach, hints to give one
            at a time and private notes; at the end you send them a scorecard.
          </p>

          <label className="side-form__field">
            <span>Problem</span>
            <select className="input" value={choice} onChange={(event) => setChoice(event.target.value)}>
              <optgroup label="Surprise me">
                {DIFFICULTIES.map((difficulty) => (
                  <option key={difficulty} value={`surprise:${difficulty}`}>A random {difficulty} problem</option>
                ))}
              </optgroup>
              <optgroup label="Pick one">
                {problems.map((problem) => (
                  <option key={problem.slug} value={problem.slug}>{problem.title} ({problem.difficulty})</option>
                ))}
              </optgroup>
            </select>
          </label>

          <fieldset className="side-form__field">
            <legend>Length</legend>
            <div className="duration-choices">
              {DURATIONS.map((value) => (
                <label key={value} className={`score-chip${minutes === value ? " score-chip--on" : ""}`}>
                  <input type="radio" name="minutes" value={value} checked={minutes === value}
                         onChange={() => setMinutes(value)} />
                  {value} min
                </label>
              ))}
            </div>
          </fieldset>

          <label className="side-form__field">
            <span>Voice call link (optional)</span>
            <input className="input" type="url" placeholder="https://meet.google.com/..." value={meetingLink}
                   maxLength={300} onChange={(event) => setMeetingLink(event.target.value)} />
            <small className="side-form__hint">Interviews work best when you can talk: paste a Meet, Zoom or Discord link.</small>
          </label>

          {error && <p className="message message--error" role="alert">{error}</p>}
          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? "Creating..." : "Create interview room"}
          </button>
        </form>
      </div>
    </>
  )
}

export default NewInterview
