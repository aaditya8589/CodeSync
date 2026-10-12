import { useEffect, useState } from "react"
import { Link, Navigate } from "react-router-dom"
import DifficultyTag from "../arena/DifficultyTag"
import TopBar from "../components/TopBar"
import { listProblems, type ProblemSummary } from "../services/arenaService"

function Problems() {
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    listProblems()
      .then(setProblems)
      .catch(() => setError("Couldn't load the problems. Check that the backend is running, then reload."))
  }, [])

  if (!localStorage.getItem("token")) return <Navigate to="/login" replace />

  const solved = problems?.filter((problem) => problem.solved).length ?? 0

  return (
    <>
      <TopBar title="Problems" />
      <main className="page">
        <h1>Problems</h1>
        <p className="page__lead">
          Pick a problem, solve it together in a room, and submit it to be judged on hidden tests.
          {problems && ` You've solved ${solved} of ${problems.length}.`}
        </p>

        {error && <p className="message message--error" role="alert">{error}</p>}
        {!problems && !error && <p className="empty">Loading problems...</p>}

        {problems && (
          <ul className="problem-list">
            {problems.map((problem) => (
              <li key={problem.slug}>
                <Link to={`/problems/${problem.slug}`}>
                  <span className={`problem-list__check${problem.solved ? " problem-list__check--done" : ""}`}
                        aria-label={problem.solved ? "Solved" : "Not solved yet"} />
                  <span className="problem-list__title">{problem.title}</span>
                  <DifficultyTag difficulty={problem.difficulty} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  )
}

export default Problems
