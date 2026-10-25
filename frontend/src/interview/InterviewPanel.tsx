import { useEffect, useState, type FormEvent } from "react"
import { Link } from "react-router-dom"
import DifficultyTag from "../arena/DifficultyTag"
import ProblemStatement from "../arena/ProblemStatement"
import { formatClock, remainingMs } from "../match/clock"
import { getProblem, VERDICT_TEXT, verdictTone, type ProblemDetail, type Verdict } from "../services/arenaService"
import {
  addNote,
  approveCandidate,
  declineCandidate,
  endInterview,
  giveHint,
  interviewLink,
  revealProblem,
  saveScorecard,
  startInterview,
  swapRoles,
  type Interview,
  type InterviewEntryNote,
} from "../services/interviewService"
import TutorText from "../tutor/TutorText"
import ScorecardForm from "./ScorecardForm"
import ScorecardView from "./ScorecardView"

interface InterviewPanelProps {
  roomId: string
  interview: Interview | null
  // How far the server's clock is ahead of this browser's
  offset: number
  onChange: (interview: Interview) => void
  onSwapped: (roomId: string) => void
  onOpenHistory: () => void
  onClose: () => void
}

const message = (reason: unknown, fallback: string) => (reason instanceof Error && reason.message) || fallback

function useRemaining(endsAt: string | null, running: boolean, offset: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!running) return
    const timer = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [running, endsAt])
  return endsAt ? remainingMs(endsAt, offset, now) : 0
}

function stamp(entry: InterviewEntryNote) {
  return entry.minute == null ? "before start" : `${entry.minute}'`
}

function CopyLink({ roomId }: { roomId: string }) {
  const [copied, setCopied] = useState(false)
  const link = interviewLink(roomId)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      window.prompt("Interview link", link)
    }
  }
  return (
    <div className="match__share">
      <label htmlFor="interview-link">Send this link to the candidate</label>
      <div className="side-form__row">
        <input id="interview-link" className="input" value={link} readOnly onFocus={(event) => event.target.select()} />
        <button type="button" className="button" onClick={() => void copy()}>{copied ? "Copied" : "Copy"}</button>
      </div>
    </div>
  )
}

function LastSubmission({ interview }: { interview: Interview }) {
  const last = interview.lastSubmission
  if (!last) return null
  const verdict = last.verdict as Verdict
  return (
    <p className={`interview__submission interview__submission--${verdictTone(verdict)}`}>
      Last submission: <strong>{VERDICT_TEXT[verdict] ?? last.verdict}</strong> ({last.passed}/{last.total})
      {last.growthSummary && <><br /><small>{last.growthSummary}</small></>}
    </p>
  )
}

// A mock interview in its room: the interviewer runs it, the candidate solves
function InterviewPanel({ roomId, interview, offset, onChange, onSwapped, onOpenHistory, onClose }: InterviewPanelProps) {
  const running = interview?.state === "RUNNING"
  const left = useRemaining(interview?.endsAt ?? null, running, offset)
  const [statement, setStatement] = useState<ProblemDetail | null>(null)
  const [showStatement, setShowStatement] = useState(false)
  const [note, setNote] = useState("")
  const [busy, setBusy] = useState(false)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [error, setError] = useState("")

  const slug = interview?.problem?.slug ?? null
  useEffect(() => {
    if (!slug) return
    let cancelled = false
    getProblem(slug).then((detail) => { if (!cancelled) setStatement(detail) }).catch(() => {})
    return () => { cancelled = true }
  }, [slug])

  const act = async (action: () => Promise<Interview>, fallback: string) => {
    setBusy(true)
    setError("")
    try {
      onChange(await action())
    } catch (reason) {
      setError(message(reason, fallback))
    } finally {
      setBusy(false)
      setConfirmEnd(false)
    }
  }

  const submitNote = (event: FormEvent) => {
    event.preventDefault()
    const text = note.trim()
    if (!text) return
    void act(async () => {
      const updated = await addNote(roomId, text)
      setNote("")
      return updated
    }, "Could not save the note")
  }

  const swap = async () => {
    setBusy(true)
    setError("")
    try {
      onSwapped((await swapRoles(roomId)).roomId)
    } catch (reason) {
      setError(message(reason, "Could not swap roles"))
    } finally {
      setBusy(false)
    }
  }

  if (!interview) {
    return (
      <aside className="problem-panel interview-panel" aria-label="Interview">
        <div className="problem-panel__head"><strong>Interview</strong></div>
        <div className="problem-panel__body"><p className="match__note">Loading the interview...</p></div>
      </aside>
    )
  }

  const interviewer = interview.role === "INTERVIEWER"
  const lobby = interview.state === "LOBBY"
  const ended = interview.state === "ENDED"
  const other = interviewer ? interview.candidate : interview.interviewer

  return (
    <aside className="problem-panel interview-panel" aria-label="Interview">
      <div className="problem-panel__head">
        <strong className="match-panel__title">
          {lobby ? "Interview lobby" : running ? (interviewer ? "Interviewing" : "Interview") : "Interview over"}
        </strong>
        <span className="interview__role">{interviewer ? "You interview" : "You're the candidate"}</span>
        {running && <span className="match-clock" role="timer" aria-label="Time left">{formatClock(left)}</span>}
        {running && interviewer && !confirmEnd && (
          <button type="button" className="button button--danger match__end-head" onClick={() => setConfirmEnd(true)}>End</button>
        )}
        <button type="button" className="button button--quiet" onClick={onClose}>Hide</button>
      </div>

      <div className="problem-panel__body interview">
        {confirmEnd && (
          <div className="match__end" role="alert">
            <span>End the interview now?</span>
            <button type="button" className="button button--danger" disabled={busy}
                    onClick={() => void act(() => endInterview(roomId), "Could not end the interview")}>End now</button>
            <button type="button" className="button button--quiet" onClick={() => setConfirmEnd(false)}>Cancel</button>
          </div>
        )}

        {interview.meetingLink && !ended && (
          <p className="interview__call">
            Voice call: <a href={interview.meetingLink} target="_blank" rel="noopener noreferrer">{interview.meetingLink}</a>
          </p>
        )}

        {/* ---------- Lobby ---------- */}
        {lobby && interviewer && (
          <section className="interview__lobby">
            <CopyLink roomId={roomId} />
            {interview.pendingCandidate ? (
              <div className="interview__request" role="group" aria-label="Asking to join">
                <span><strong>{interview.pendingCandidate}</strong> asks to be the candidate</span>
                <button type="button" className="button button--primary" disabled={busy}
                        onClick={() => void act(() => approveCandidate(roomId, interview.pendingCandidate!), "Could not let them in")}>Let in</button>
                <button type="button" className="button button--quiet" disabled={busy}
                        onClick={() => void act(() => declineCandidate(roomId, interview.pendingCandidate!), "Could not decline")}>Decline</button>
              </div>
            ) : interview.candidate ? (
              <p className="match__note"><strong>{interview.candidate}</strong> is in.</p>
            ) : (
              <p className="match__note">Waiting for the candidate to open the link.</p>
            )}
            {interview.problem && (
              <p className="match__note">
                Problem: <strong>{interview.problem.title}</strong> <DifficultyTag difficulty={interview.problem.difficulty} />.
                {" "}Hidden from the candidate until you reveal it.
              </p>
            )}
            <button type="button" className="button button--primary" disabled={busy || !interview.candidate}
                    onClick={() => void act(() => startInterview(roomId), "Could not start")}>
              {busy ? "Starting..." : `Start the ${interview.durationMinutes}-minute interview`}
            </button>
          </section>
        )}
        {lobby && !interviewer && (
          <p className="match__note">
            Waiting for <strong>{interview.interviewer}</strong> to start the {interview.durationMinutes}-minute interview.
            {" "}Think out loud once it starts: how you reason matters as much as the code.
          </p>
        )}

        {/* ---------- Interviewer during and after ---------- */}
        {!lobby && interviewer && interview.problem && (
          <>
            <section className="interview__section">
              <div className="interview__problem">
                <strong>{interview.problem.title}</strong>
                <DifficultyTag difficulty={interview.problem.difficulty} />
                {interview.revealed ? <span className="interview__badge">Revealed</span> : running && (
                  <button type="button" className="button button--primary" disabled={busy}
                          onClick={() => void act(() => revealProblem(roomId), "Could not reveal")}>Reveal problem</button>
                )}
              </div>
              <button type="button" className="link-button" aria-expanded={showStatement}
                      onClick={() => setShowStatement((open) => !open)}>
                {showStatement ? "Hide statement" : "Show statement"}
              </button>
              {showStatement && statement && <div className="match__statement"><ProblemStatement problem={statement} /></div>}
            </section>

            {interview.guide && (
              <details className="interview__guide" open={running}>
                <summary>Interviewer guide (only you see this)</summary>
                <TutorText text={interview.guide} />
              </details>
            )}

            <section className="interview__section" aria-label="Hints">
              <h3>Hints <small>{interview.hintsGiven.length} given{interview.hintsLeft != null ? `, ${interview.hintsLeft} left` : ""}</small></h3>
              {interview.hintsGiven.length > 0 && (
                <ol className="interview__hints">
                  {interview.hintsGiven.map((hint, index) => <li key={index}><span className="interview__time">{stamp(hint)}</span> {hint.text}</li>)}
                </ol>
              )}
              {running && interview.nextHint && (
                <div className="interview__next-hint">
                  <p><span className="interview__label">Next:</span> {interview.nextHint}</p>
                  <button type="button" className="button" disabled={busy || !interview.revealed}
                          title={interview.revealed ? "Show this hint to the candidate" : "Reveal the problem first"}
                          onClick={() => void act(() => giveHint(roomId), "Could not give the hint")}>Give hint</button>
                </div>
              )}
            </section>

            <LastSubmission interview={interview} />

            <section className="interview__section" aria-label="Notes">
              <h3>Notes <small>only you see these</small></h3>
              {interview.notes && interview.notes.length > 0 && (
                <ul className="interview__notes">
                  {interview.notes.map((entry, index) => <li key={index}><span className="interview__time">{stamp(entry)}</span> {entry.text}</li>)}
                </ul>
              )}
              <form className="interview__note-form" onSubmit={submitNote}>
                <textarea className="input" rows={2} maxLength={2000} value={note} aria-label="New note"
                          placeholder="e.g. went for brute force first, stated the complexity"
                          onChange={(event) => setNote(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter" && !event.shiftKey) {
                              event.preventDefault()
                              submitNote(event)
                            }
                          }} />
                <button type="submit" className="button" disabled={busy || !note.trim()}>Add note</button>
              </form>
            </section>

            {ended && (
              <section className="interview__section" aria-label="Scorecard">
                <h3>Scorecard for {interview.candidate}</h3>
                <ScorecardForm initial={interview.scorecard}
                               onSave={async (card) => onChange(await saveScorecard(roomId, card))} />
              </section>
            )}
          </>
        )}

        {/* ---------- Candidate during and after ---------- */}
        {!lobby && !interviewer && (
          <>
            {/* After the end the feedback comes first; the statement stays one click away */}
            {ended && (interview.scorecard
              ? <ScorecardView card={interview.scorecard} />
              : <p className="match__note" role="status">Waiting for {interview.interviewer}'s feedback. It appears here.</p>)}
            {ended && <LastSubmission interview={interview} />}
            {!interview.revealed ? (
              !ended && <p className="match__note">{interview.interviewer} will share the problem with you. Ask questions out loud.</p>
            ) : statement && (ended ? (
              <details className="interview__guide">
                <summary>Problem: {statement.title}</summary>
                <ProblemStatement problem={statement} />
              </details>
            ) : (
              <div className="interview__statement"><ProblemStatement problem={statement} /></div>
            ))}
            {interview.hintsGiven.length > 0 && (
              <section className="interview__section" aria-label="Hints from your interviewer">
                <h3>Hints from {interview.interviewer}</h3>
                <ol className="interview__hints">
                  {interview.hintsGiven.map((hint, index) => <li key={index}>{hint.text}</li>)}
                </ol>
              </section>
            )}
          </>
        )}

        {ended && (
          <section className="interview__after">
            <button type="button" className="button" onClick={onOpenHistory}>Replay the code</button>
            {other && (interview.nextRoomId ? (
              <Link to={`/rooms/${interview.nextRoomId}`} className="button button--primary">Open the next interview</Link>
            ) : (
              <button type="button" className="button button--primary" disabled={busy} onClick={() => void swap()}>
                Swap roles: you {interviewer ? "solve" : "interview"} next
              </button>
            ))}
          </section>
        )}

        {error && <p className="message message--error" role="alert">{error}</p>}
      </div>
    </aside>
  )
}

export default InterviewPanel
