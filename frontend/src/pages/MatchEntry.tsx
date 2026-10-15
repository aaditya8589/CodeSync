import { useCallback, useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import TopBar from "../components/TopBar"
import { getMatchEntry, requestToJoin, type MatchEntry as Entry } from "../services/matchService"

// While waiting to be let in, ask again this often. (The asker is not in the room yet, so the
// room's live channel is closed to them.)
const POLL_MS = 2000

// Where a shared match link lands: ask to join, wait for the host, then go in
function MatchEntry() {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const [entry, setEntry] = useState<Entry | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)

  const show = useCallback((found: Entry) => {
    if (found.myStatus === "APPROVED") {
      navigate(`/rooms/${found.roomId}`, { replace: true })
      return
    }
    setEntry(found)
  }, [navigate])

  useEffect(() => {
    if (!roomId) return
    getMatchEntry(roomId).then(show).catch(() => setError("This match link doesn't lead to a match."))
  }, [roomId, show])

  useEffect(() => {
    if (!roomId || entry?.myStatus !== "PENDING") return
    const timer = setInterval(() => { getMatchEntry(roomId).then(show).catch(() => {}) }, POLL_MS)
    return () => clearInterval(timer)
  }, [roomId, entry?.myStatus, show])

  const ask = async () => {
    if (!roomId) return
    setBusy(true)
    setError("")
    try {
      show(await requestToJoin(roomId))
    } catch (reason) {
      setError((reason instanceof Error && reason.message) || "Could not ask to join")
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title="Match" />
      <div className="narrow-page match-entry">
        {error && <p className="message message--error" role="alert">{error}</p>}
        {!entry && !error && <p className="empty">Opening the match...</p>}
        {entry && (
          <section className="side-form">
            <h1>{entry.title}</h1>
            <p className="match__note">
              Hosted by <strong>{entry.host}</strong>. {entry.problemCount} problem{entry.problemCount > 1 ? "s" : ""},
              {" "}{entry.durationMinutes} minutes. {entry.state === "LOBBY" ? "Not started yet." : entry.state === "RUNNING" ? "Already running." : "Over."}
            </p>

            {entry.state === "ENDED" && entry.myStatus !== "PENDING" ? (
              <p>This match is over.</p>
            ) : entry.myStatus === "NONE" ? (
              <button type="button" className="button button--primary" disabled={busy} onClick={() => void ask()}>
                {busy ? "Asking..." : "Ask to join"}
              </button>
            ) : entry.myStatus === "PENDING" ? (
              <p role="status" className="match-entry__waiting">Waiting for {entry.host} to let you in. This page opens the match by itself.</p>
            ) : (
              <p role="status">{entry.host} didn't let you into this match.</p>
            )}
            <p><Link to="/dashboard" className="back-link">Back to your rooms</Link></p>
          </section>
        )}
      </div>
    </>
  )
}

export default MatchEntry
