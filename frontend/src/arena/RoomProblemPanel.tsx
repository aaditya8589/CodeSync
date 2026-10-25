import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import { listProblems, type ProblemDetail, type ProblemSummary } from "../services/arenaService"
import TutorPanel from "../tutor/TutorPanel"
import DifficultyTag from "./DifficultyTag"
import ProblemStatement from "./ProblemStatement"

interface RoomProblemPanelProps {
  roomId: string
  problem: ProblemDetail | null
  // The file open in the editor, which the tutor reads
  openFile: { id: string; fileName: string } | null
  // Off in match rooms: there the tutor would be a teammate nobody else has
  tutorAllowed: boolean
  onChoose: (slug: string | null) => void
  onClose: () => void
}

// The problem the room is working on, next to the code, or a list to pick one from
function RoomProblemPanel({ roomId, problem, openFile, tutorAllowed, onChoose, onClose }: RoomProblemPanelProps) {
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  const [tab, setTab] = useState<"problem" | "tutor">("problem")
  // Once opened, the tutor stays mounted (hidden) so switching tabs keeps the conversation
  const [tutorOpened, setTutorOpened] = useState(false)
  const showTutor = tutorAllowed && tab === "tutor"

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
            {tutorAllowed && (
              <div className="panel-tabs" role="tablist" aria-label="Problem panel">
                <button type="button" role="tab" aria-selected={!showTutor} className="panel-tabs__tab"
                        onClick={() => setTab("problem")}>Statement</button>
                <button type="button" role="tab" aria-selected={showTutor} className="panel-tabs__tab"
                        onClick={() => { setTab("tutor"); setTutorOpened(true) }}>Tutor</button>
              </div>
            )}
            {tutorAllowed && tutorOpened && (
              <div hidden={!showTutor}>
                {/* A new problem starts a new conversation */}
                <TutorPanel key={problem.slug} roomId={roomId} documentId={openFile?.id ?? null}
                            fileName={openFile?.fileName ?? null} />
              </div>
            )}
            {!showTutor && <ProblemStatement problem={problem} />}
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
