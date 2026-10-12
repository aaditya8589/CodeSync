import { useEffect, useState } from "react"
import { Link, Navigate, useNavigate, useParams } from "react-router-dom"
import DifficultyTag from "../arena/DifficultyTag"
import ProblemStatement from "../arena/ProblemStatement"
import TopBar from "../components/TopBar"
import {
  VERDICT_TEXT,
  attachProblem,
  getProblem,
  mySubmissions,
  verdictTone,
  type ProblemDetail,
  type SubmissionSummary,
} from "../services/arenaService"
import { createRoom, getMyRooms, type Room } from "../services/roomService"

const timeFormat: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }

function Problem() {
  const { slug = "" } = useParams<{ slug: string }>()
  const navigate = useNavigate()
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([])
  const [rooms, setRooms] = useState<Room[]>([])
  const [roomId, setRoomId] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([getProblem(slug), mySubmissions(slug), getMyRooms()])
      .then(([detail, mine, myRooms]) => {
        setProblem(detail)
        setSubmissions(mine)
        setRooms(myRooms)
      })
      .catch((reason: Error) => setError(reason.message))
  }, [slug])

  if (!localStorage.getItem("token")) return <Navigate to="/login" replace />

  const openIn = async (target: string) => {
    setBusy(true)
    setError("")
    try {
      await attachProblem(target, slug)
      navigate(`/rooms/${target}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Couldn't open the problem in that room")
      setBusy(false)
    }
  }

  const newRoom = async () => {
    if (!problem) return
    setBusy(true)
    setError("")
    try {
      const room = await createRoom(problem.title)
      await openIn(room.id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Couldn't create a room")
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title={problem?.title ?? "Problem"} />
      <div className="problem-page">
        <main>
          <p><Link to="/problems" className="back-link">All problems</Link></p>
          {error && <p className="message message--error" role="alert">{error}</p>}
          {!problem && !error && <p className="empty">Loading...</p>}
          {problem && (
            <>
              <div className="problem-page__title">
                <h1>{problem.title}</h1>
                <DifficultyTag difficulty={problem.difficulty} />
                {problem.solved && <span className="verdict verdict--ok">Solved</span>}
              </div>
              <ProblemStatement problem={problem} />
            </>
          )}
        </main>

        <aside className="side-forms">
          <section className="side-form">
            <h2>Solve it together</h2>
            <p className="side-form__hint">
              The problem appears next to the code for everyone in the room, and anyone can submit.
            </p>
            <button type="button" className="button button--primary" onClick={() => void newRoom()} disabled={busy || !problem}>
              New room for this problem
            </button>
            {rooms.length > 0 && (
              <div className="field">
                <label htmlFor="existing-room">Or use one of your rooms</label>
                <div className="side-form__row">
                  <select id="existing-room" className="input" value={roomId} onChange={(event) => setRoomId(event.target.value)}>
                    <option value="">Choose a room</option>
                    {rooms.map((room) => <option key={room.id} value={room.id}>{room.name}</option>)}
                  </select>
                  <button type="button" className="button" onClick={() => void openIn(roomId)} disabled={busy || !roomId}>
                    Open
                  </button>
                </div>
              </div>
            )}
          </section>

          <section className="side-form">
            <h2>Your submissions</h2>
            {submissions.length === 0 ? (
              <p className="side-form__hint">None yet.</p>
            ) : (
              <ul className="submission-list">
                {submissions.map((submission) => (
                  <li key={submission.id}>
                    <span className={`verdict verdict--${verdictTone(submission.verdict)}`}>
                      {VERDICT_TEXT[submission.verdict]}
                    </span>
                    <span className="submission-list__meta">
                      {submission.passed}/{submission.total} tests, {submission.fileName},{" "}
                      {new Date(submission.createdAt).toLocaleString([], timeFormat)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
    </>
  )
}

export default Problem
