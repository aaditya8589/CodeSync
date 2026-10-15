import { useEffect, useState, type FormEvent } from "react"
import { listProblems, type ProblemSummary } from "../services/arenaService"
import DifficultyTag from "../arena/DifficultyTag"

const DURATIONS = [5, 10, 15, 30, 45, 60, 90, 120]
const MAX_PROBLEMS = 3

interface NewMatchFormProps {
  onCreate: (slugs: string[], minutes: number) => Promise<void>
  // A problem to tick from the start (the Problem page's "Compete on it")
  initialSlug?: string
  heading?: string
}

// Choosing a match's problems (in order: A, B, C) and length
function NewMatchForm({ onCreate, initialSlug, heading = "New match" }: NewMatchFormProps) {
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  const [chosen, setChosen] = useState<string[]>(initialSlug ? [initialSlug] : [])
  const [minutes, setMinutes] = useState(30)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    listProblems().then(setProblems).catch(() => setProblems([]))
  }, [])

  const toggle = (slug: string) =>
    setChosen((current) => current.includes(slug) ? current.filter((s) => s !== slug) : [...current, slug])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError("")
    try {
      await onCreate(chosen, minutes)
    } catch (reason) {
      setError((reason instanceof Error && reason.message) || "Could not create the match")
      setBusy(false)
    }
  }

  return (
    <form className="match-start" onSubmit={(event) => void submit(event)}>
      <h3>{heading}</h3>
      <p className="match__note">
        You get a match room and a link to share. Players ask to join through the link and you let them in. The
        clock starts for everyone when you press Start, and nobody sees anyone else's code until the time is up.
      </p>
      <fieldset className="match-start__problems">
        <legend>Problems (up to {MAX_PROBLEMS}, in order A, B, C)</legend>
        {problems.map((problem) => {
          const index = chosen.indexOf(problem.slug)
          return (
            <label key={problem.slug} className="match-start__problem">
              <input
                type="checkbox"
                checked={index >= 0}
                disabled={index < 0 && chosen.length >= MAX_PROBLEMS}
                onChange={() => toggle(problem.slug)}
              />
              <span className="match-start__letter">{index >= 0 ? String.fromCharCode(65 + index) : ""}</span>
              <span>{problem.title}</span>
              <DifficultyTag difficulty={problem.difficulty} />
            </label>
          )
        })}
      </fieldset>
      <label className="field">
        <span>Duration</span>
        <select className="input" value={minutes} onChange={(event) => setMinutes(Number(event.target.value))}>
          {DURATIONS.map((value) => <option key={value} value={value}>{value} minutes</option>)}
        </select>
      </label>
      <button type="submit" className="button button--primary" disabled={busy || chosen.length === 0}>
        {busy ? "Creating..." : "Create match room"}
      </button>
      {error && <p className="message message--error" role="alert">{error}</p>}
    </form>
  )
}

export default NewMatchForm
