import { useEffect, useState, type FormEvent } from "react"
import { getProblem, listProblems, type ProblemDetail, type ProblemSummary } from "../services/arenaService"
import type { Match, StandingCell } from "../services/matchService"
import DifficultyTag from "../arena/DifficultyTag"
import ProblemStatement from "../arena/ProblemStatement"
import { formatClock } from "./clock"
import { useCountdown } from "./useCountdown"

const DURATIONS = [15, 30, 45, 60, 90, 120]
const MAX_PROBLEMS = 3

interface MatchPanelProps {
  match: Match | null
  offset: number
  me: string | null
  onStart: (slugs: string[], minutes: number) => Promise<void>
  onJoin: () => Promise<void>
  onEnd: () => Promise<void>
  onClose: () => void
}

const message = (reason: unknown, fallback: string) => (reason instanceof Error && reason.message) || fallback


function Cell({ cell }: { cell: StandingCell | undefined }) {
  if (!cell || (!cell.solved && cell.rejected === 0)) return <td className="standings__cell" />
  if (cell.solved) {
    return (
      <td className="standings__cell standings__cell--solved" title={`Solved at minute ${cell.solvedAtMinute} after ${cell.rejected} rejected`}>
        {cell.solvedAtMinute}
        {cell.rejected > 0 && <small> +{cell.rejected}</small>}
      </td>
    )
  }
  return <td className="standings__cell standings__cell--tried" title={`${cell.rejected} rejected`}>−{cell.rejected}</td>
}

function Standings({ match, me }: { match: Match; me: string | null }) {
  if (match.standings.length === 0) return null
  return (
    <table className="standings">
      <caption>{match.running ? "Standings" : "Final standings"}</caption>
      <thead>
        <tr>
          <th scope="col">#</th>
          <th scope="col">Player</th>
          {match.problems.map((problem) => <th scope="col" key={problem.slug} title={problem.title}>{problem.letter}</th>)}
          <th scope="col" title="Problems solved">Solved</th>
          <th scope="col" title="Minutes to each accepted solution, plus 5 per rejected try before it">Penalty</th>
        </tr>
      </thead>
      <tbody>
        {match.standings.map((row) => (
          <tr key={row.username} aria-current={row.username === me ? "true" : undefined}>
            <td>{row.rank}</td>
            <th scope="row">{row.username}</th>
            {match.problems.map((problem) => (
              <Cell key={problem.slug} cell={row.cells.find((cell) => cell.problemSlug === problem.slug)} />
            ))}
            <td>{row.solved}</td>
            <td>{row.penalty}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function StartForm({ onStart }: { onStart: MatchPanelProps["onStart"] }) {
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  const [chosen, setChosen] = useState<string[]>([])
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
      await onStart(chosen, minutes)
    } catch (reason) {
      setError(message(reason, "Could not start the match"))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="match-start" onSubmit={(event) => void submit(event)}>
      <h3>Start a match</h3>
      <p className="match__note">
        Everyone in the room can join. Each player gets a private file per problem; nobody sees anyone else's code until
        the time is up. More problems solved wins; ties go to less time, with 5 minutes added per rejected try.
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
        {busy ? "Starting..." : "Start match"}
      </button>
      {error && <p className="message message--error" role="alert">{error}</p>}
    </form>
  )
}

// A running or finished match beside the code, or the form to start one
function MatchPanel({ match, offset, me, onStart, onJoin, onEnd, onClose }: MatchPanelProps) {
  const left = useCountdown(match, offset)
  const [openSlug, setOpenSlug] = useState<string | null>(null)
  const [statement, setStatement] = useState<ProblemDetail | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    if (!openSlug) return
    let cancelled = false
    getProblem(openSlug).then((detail) => { if (!cancelled) setStatement(detail) }).catch(() => {})
    return () => { cancelled = true }
  }, [openSlug])

  const act = async (action: () => Promise<void>, fallback: string) => {
    setBusy(true)
    setError("")
    try {
      await action()
    } catch (reason) {
      setError(message(reason, fallback))
    } finally {
      setBusy(false)
      setConfirmEnd(false)
    }
  }

  const running = match?.running && left > 0

  return (
    <aside className="problem-panel match-panel" aria-label="Match">
      <div className="problem-panel__head">
        <strong className="match-panel__title">Match</strong>
        {running && <span className="match-clock" role="timer" aria-label="Time left">{formatClock(left)}</span>}
        <button type="button" className="button button--quiet" onClick={onClose}>Hide</button>
      </div>

      <div className="problem-panel__body">
        {match && running ? (
          <>
            {!match.joined && (
              <div className="match__join">
                <p>A match started by <strong>{match.host}</strong> is running. You get your own private file for each problem.
                  Time counts from the start, so join soon.</p>
                <button type="button" className="button button--primary" disabled={busy}
                  onClick={() => void act(onJoin, "Could not join")}>
                  {busy ? "Joining..." : "Join match"}
                </button>
              </div>
            )}

            <ol className="match__problems">
              {match.problems.map((problem) => (
                <li key={problem.slug}>
                  <button type="button" className="match__problem" aria-expanded={openSlug === problem.slug}
                    onClick={() => setOpenSlug((current) => current === problem.slug ? null : problem.slug)}>
                    <span className="match-start__letter">{problem.letter}</span>
                    <span>{problem.title}</span>
                    <DifficultyTag difficulty={problem.difficulty} />
                  </button>
                  {openSlug === problem.slug && statement?.slug === problem.slug && (
                    <div className="match__statement"><ProblemStatement problem={statement} /></div>
                  )}
                </li>
              ))}
            </ol>

            <Standings match={match} me={me} />

            {match.host === me && (
              confirmEnd ? (
                <div className="match__end">
                  <span>End it for everyone now? All files become visible.</span>
                  <button type="button" className="button button--danger" disabled={busy}
                    onClick={() => void act(onEnd, "Could not end the match")}>End now</button>
                  <button type="button" className="button button--quiet" onClick={() => setConfirmEnd(false)}>Cancel</button>
                </div>
              ) : (
                <button type="button" className="button button--quiet match__end-button" onClick={() => setConfirmEnd(true)}>
                  End match early
                </button>
              )
            )}
          </>
        ) : (
          <>
            {match && (
              <section className="match__result">
                <Standings match={match} me={me} />
                <p className="match__note">The match is over: everyone's files are visible in the file list now.</p>
              </section>
            )}
            <StartForm onStart={onStart} />
          </>
        )}
        {error && <p className="message message--error" role="alert">{error}</p>}
      </div>
    </aside>
  )
}

export default MatchPanel
