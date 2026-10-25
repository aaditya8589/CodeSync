import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import DifficultyTag from "../arena/DifficultyTag"
import TopBar from "../components/TopBar"
import { getProgress, type Progress as ProgressData, type ProblemProgress } from "../services/arenaService"

const STATUS_TEXT: Record<ProblemProgress["status"], string> = {
  SOLVED: "Solved",
  ATTEMPTED: "Tried",
  NEW: "Not started",
}

const day = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })

function Progress() {
  const [progress, setProgress] = useState<ProgressData | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    getProgress()
      .then(setProgress)
      .catch(() => setError("Couldn't load your progress. Check that the backend is running, then reload."))
  }, [])

  const solved = progress?.problems.filter((problem) => problem.status === "SOLVED").length ?? 0

  return (
    <>
      <TopBar title="Progress" />
      <main className="page">
        <h1>Your progress</h1>
        {progress && (
          <p className="page__lead">
            {solved === 0
              ? "Nothing solved yet. Start with an Easy problem; the tutor can help in any room."
              : `You've solved ${solved} of ${progress.problems.length} problems.`}
          </p>
        )}

        {error && <p className="message message--error" role="alert">{error}</p>}
        {!progress && !error && <p className="empty">Loading your progress...</p>}

        {progress && (
          <>
            <ul className="progress-levels">
              {progress.difficulties.map((level) => (
                <li key={level.difficulty} className="progress-level">
                  <div className="progress-level__head">
                    <DifficultyTag difficulty={level.difficulty} />
                    <span className="progress-level__count"><strong>{level.solved}</strong> / {level.total} solved</span>
                  </div>
                  <div className="progress-bar" role="progressbar" aria-label={`${level.difficulty} solved`}
                       aria-valuemin={0} aria-valuemax={level.total} aria-valuenow={level.solved}>
                    <span className="progress-bar__solved" style={{ width: `${(100 * level.solved) / level.total}%` }} />
                    <span className="progress-bar__tried" style={{ width: `${(100 * level.attempted) / level.total}%` }} />
                  </div>
                  {level.attempted > 0 && <p className="progress-level__note">{level.attempted} tried, not solved yet</p>}
                </li>
              ))}
            </ul>

            <table className="progress-table">
              <thead>
                <tr>
                  <th scope="col">Problem</th>
                  <th scope="col">Difficulty</th>
                  <th scope="col">Status</th>
                  <th scope="col">Submissions</th>
                </tr>
              </thead>
              <tbody>
                {progress.problems.map((problem) => (
                  <tr key={problem.slug}>
                    <th scope="row"><Link to={`/problems/${problem.slug}`}>{problem.title}</Link></th>
                    <td><DifficultyTag difficulty={problem.difficulty} /></td>
                    <td className={`progress-status progress-status--${problem.status.toLowerCase()}`}>
                      {STATUS_TEXT[problem.status]}
                      {problem.solvedAt && <small> on {day(problem.solvedAt)}</small>}
                    </td>
                    <td>{problem.attempts || ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </main>
    </>
  )
}

export default Progress
