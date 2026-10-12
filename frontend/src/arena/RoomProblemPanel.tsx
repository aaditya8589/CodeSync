import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import { listProblems, type ProblemDetail, type ProblemSummary } from "../services/arenaService"
import DifficultyTag from "./DifficultyTag"
import ProblemStatement from "./ProblemStatement"

interface RoomProblemPanelProps {
  problem: ProblemDetail | null
  onChoose: (slug: string | null) => void
  onClose: () => void
}

// The problem the room is working on, next to the code, or a list to pick one from
function RoomProblemPanel({ problem, onChoose, onClose }: RoomProblemPanelProps) {
  const [problems, setProblems] = useState<ProblemSummary[]>([])

  useEffect(() => {
    listProblems().then(setProblems).catch(() => setProblems([]))
  }, [])

  return (
    <aside className="problem-panel" aria-label="Problem">
      <div className="problem-panel__head">
        <select
          className="input"
          aria-label="Problem for this room"
          value={problem?.slug ?? ""}
          onChange={(event) => onChoose(event.target.value || null)}
        >
          <option value="">No problem</option>
          {problems.map((item) => (
            <option key={item.slug} value={item.slug}>{item.title}</option>
          ))}
        </select>
        <button type="button" className="button button--quiet" onClick={onClose}>Hide</button>
      </div>

      <div className="problem-panel__body">
        {problem ? (
          <>
            <div className="problem-page__title">
              <h2>{problem.title}</h2>
              <DifficultyTag difficulty={problem.difficulty} />
            </div>
            <ProblemStatement problem={problem} />
          </>
        ) : (
          <div className="problem-panel__empty">
            <p>Pick a problem above to solve it together. Everyone in the room sees it here, and anyone can submit.</p>
            <p><Link to="/problems">Browse all problems</Link></p>
          </div>
        )}
      </div>
    </aside>
  )
}

export default RoomProblemPanel
