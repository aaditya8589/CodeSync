import { useCallback, useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import TopBar from "../components/TopBar"
import { askToJoinInterview, getInterviewEntry, type InterviewEntry as Entry } from "../services/interviewService"

// While waiting to be let in, ask again this often (the room's live channel is members-only)
const POLL_MS = 2000

// Where a shared interview link lands: ask to be the candidate, wait, then go in
function InterviewEntry() {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const [entry, setEntry] = useState<Entry | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)

  const show = useCallback((found: Entry) => {
    if (found.myStatus === "CANDIDATE" || found.myStatus === "INTERVIEWER") {
      navigate(`/rooms/${found.roomId}`, { replace: true })
      return
    }
    setEntry(found)
  }, [navigate])

  useEffect(() => {
    if (!roomId) return
    getInterviewEntry(roomId).then(show).catch(() => setError("This link doesn't lead to a mock interview."))
  }, [roomId, show])

  useEffect(() => {
    if (!roomId || entry?.myStatus !== "PENDING") return
    const timer = setInterval(() => { getInterviewEntry(roomId).then(show).catch(() => {}) }, POLL_MS)
    return () => clearInterval(timer)
  }, [roomId, entry?.myStatus, show])

  const ask = async () => {
    if (!roomId) return
    setBusy(true)
    setError("")
    try {
      show(await askToJoinInterview(roomId))
    } catch (reason) {
      setError((reason instanceof Error && reason.message) || "Could not ask to join")
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title="Mock interview" />
      <div className="narrow-page match-entry">
        {error && <p className="message message--error" role="alert">{error}</p>}
        {!entry && !error && <p className="empty">Opening the interview...</p>}
        {entry && (
          <section className="side-form">
            <h1>Mock interview with {entry.interviewer}</h1>
            <p className="match__note">
              {entry.durationMinutes} minutes. You'll solve one problem while {entry.interviewer} watches your code live,
              {" "}gives hints if you need them, and sends you a scorecard at the end.
            </p>
            {entry.state === "ENDED" ? (
              <p>This interview is over.</p>
            ) : entry.myStatus === "NONE" ? (
              <button type="button" className="button button--primary" disabled={busy} onClick={() => void ask()}>
                {busy ? "Asking..." : "Ask to join as the candidate"}
              </button>
            ) : entry.myStatus === "PENDING" ? (
              <p role="status" className="match-entry__waiting">Waiting for {entry.interviewer} to let you in. This page opens the interview by itself.</p>
            ) : (
              <p role="status">Someone else is already the candidate in this interview.</p>
            )}
            <p><Link to="/dashboard" className="back-link">Back to your rooms</Link></p>
          </section>
        )}
      </div>
    </>
  )
}

export default InterviewEntry
