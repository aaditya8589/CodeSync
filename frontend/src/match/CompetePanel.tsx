import { Link } from "react-router-dom"
import type { MatchSummary } from "../services/matchService"
import NewMatchForm from "./NewMatchForm"

interface CompetePanelProps {
  matches: MatchSummary[]
  onCreate: (slugs: string[], minutes: number) => Promise<void>
  onClose: () => void
}

const STATE_TEXT = { LOBBY: "waiting to start", RUNNING: "running", ENDED: "over" }

// In an ordinary room: make a match room for the people here, and find the ones already made
function CompetePanel({ matches, onCreate, onClose }: CompetePanelProps) {
  return (
    <aside className="problem-panel match-panel" aria-label="Compete">
      <div className="problem-panel__head">
        <strong className="match-panel__title">Compete</strong>
        <button type="button" className="button button--quiet" onClick={onClose}>Hide</button>
      </div>
      <div className="problem-panel__body">
        {matches.length > 0 && (
          <section className="match__made">
            <h3>Matches from this room</h3>
            <ul>
              {matches.map((match) => (
                <li key={match.roomId}>
                  <Link to={`/match/${match.roomId}`}>{match.title}</Link>
                  <span className="match__note"> by {match.host}, {STATE_TEXT[match.state]}</span>
                </li>
              ))}
            </ul>
          </section>
        )}
        <NewMatchForm
          heading="Start a match from this room"
          onCreate={onCreate}
        />
      </div>
    </aside>
  )
}

export default CompetePanel
