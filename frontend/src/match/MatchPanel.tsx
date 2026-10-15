import { useEffect, useState } from "react"
import { getProblem, type ProblemDetail } from "../services/arenaService"
import { matchLink, type Match, type StandingCell } from "../services/matchService"
import DifficultyTag from "../arena/DifficultyTag"
import ProblemStatement from "../arena/ProblemStatement"
import { formatClock } from "./clock"
import { useCountdown } from "./useCountdown"

interface MatchPanelProps {
  roomId: string
  match: Match | null
  offset: number
  me: string | null
  onApprove: (username: string) => Promise<void>
  onDecline: (username: string) => Promise<void>
  onStart: () => Promise<void>
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
      <caption>{match.state === "ENDED" ? "Final standings" : "Standings"}</caption>
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

function ShareLink({ roomId }: { roomId: string }) {
  const [copied, setCopied] = useState(false)
  const link = matchLink(roomId)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      window.prompt("Match link", link)
    }
  }
  return (
    <div className="match__share">
      <label htmlFor="match-link">Invite with this link</label>
      <div className="side-form__row">
        <input id="match-link" className="input" value={link} readOnly onFocus={(event) => event.target.select()} />
        <button type="button" className="button" onClick={() => void copy()}>{copied ? "Copied" : "Copy"}</button>
      </div>
    </div>
  )
}

// The match room's contest: lobby, running match or final standings
function MatchPanel({ roomId, match, offset, me, onApprove, onDecline, onStart, onEnd, onClose }: MatchPanelProps) {
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

  const isHost = match?.host === me
  const running = Boolean(match?.running && left > 0)
  const lobby = match?.state === "LOBBY"

  return (
    <aside className="problem-panel match-panel" aria-label="Match">
      <div className="problem-panel__head">
        <strong className="match-panel__title">
          {lobby ? "Lobby" : running ? "Match" : "Match over"}
        </strong>
        {running && <span className="match-clock" role="timer" aria-label="Time left">{formatClock(left)}</span>}
        {running && isHost && !confirmEnd && (
          <button type="button" className="button button--danger match__end-head" onClick={() => setConfirmEnd(true)}>
            End
          </button>
        )}
        <button type="button" className="button button--quiet" onClick={onClose}>Hide</button>
      </div>

      <div className="problem-panel__body">
        {!match && <p className="match__note">Loading the match...</p>}

        {match && confirmEnd && (
          <div className="match__end" role="alert">
            <span>End the match for everyone now? All files become visible.</span>
            <button type="button" className="button button--danger" disabled={busy}
              onClick={() => void act(onEnd, "Could not end the match")}>End now</button>
            <button type="button" className="button button--quiet" onClick={() => setConfirmEnd(false)}>Cancel</button>
          </div>
        )}

        {match && match.state !== "ENDED" && <ShareLink roomId={roomId} />}

        {match && isHost && match.requests.length > 0 && match.state !== "ENDED" && (
          <section className="match__requests" aria-label="Asking to join">
            <h3>Asking to join ({match.requests.length})</h3>
            <ul>
              {match.requests.map((username) => (
                <li key={username}>
                  <span>{username}</span>
                  <button type="button" className="button button--primary" disabled={busy}
                    onClick={() => void act(() => onApprove(username), "Could not let them in")}>Let in</button>
                  <button type="button" className="button button--quiet" disabled={busy}
                    onClick={() => void act(() => onDecline(username), "Could not decline")}>Decline</button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {match && lobby && (
          <section className="match__lobby">
            <p className="match__note">
              {match.problems.length} problem{match.problems.length > 1 ? "s" : ""}, {match.durationMinutes} minutes.
              {" "}{isHost
                ? "Share the link, let players in, and start when everyone is here. The clock starts for everyone at once."
                : `Waiting for ${match.host} to start. The clock starts for everyone at once.`}
            </p>
            <h3>Players ({match.standings.length})</h3>
            <ul className="match__players">
              {match.standings.map((row) => (
                <li key={row.username}>{row.username}{row.username === match.host ? " (host)" : ""}{row.username === me ? " (you)" : ""}</li>
              ))}
            </ul>
            {isHost && (
              <button type="button" className="button button--primary" disabled={busy}
                onClick={() => void act(onStart, "Could not start the match")}>
                {busy ? "Starting..." : `Start the ${match.durationMinutes}-minute match`}
              </button>
            )}
          </section>
        )}

        {match && !lobby && (
          <>
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
            {!running && <p className="match__note">The match is over: everyone's files are visible in the file list now.</p>}
          </>
        )}

        {error && <p className="message message--error" role="alert">{error}</p>}
      </div>
    </aside>
  )
}

export default MatchPanel
