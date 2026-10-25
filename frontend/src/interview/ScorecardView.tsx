import { RECOMMENDATION_TEXT, type Scorecard } from "../services/interviewService"
import { SCORE_AREAS, SCORE_TEXT } from "./scores"
import TutorText from "../tutor/TutorText"

// The scorecard as the candidate receives it
function ScorecardView({ card }: { card: Scorecard }) {
  const average = (card.problemSolving + card.code + card.communication + card.complexity) / 4
  return (
    <section className="scorecard" aria-label="Your scorecard">
      <div className="scorecard__head">
        <strong className={`scorecard__verdict scorecard__verdict--${card.recommendation.toLowerCase().replace(/_/g, "-")}`}>
          {RECOMMENDATION_TEXT[card.recommendation]}
        </strong>
        <span className="scorecard__average">{average.toFixed(1)} / 4</span>
      </div>
      <ul className="scorecard__areas">
        {SCORE_AREAS.map(({ key, label }) => (
          <li key={key}>
            <span>{label}</span>
            <span className="scorecard__bar" aria-hidden="true">
              {[1, 2, 3, 4].map((n) => <span key={n} className={n <= card[key] ? "on" : ""} />)}
            </span>
            <span className="scorecard__score">{card[key]} {SCORE_TEXT[card[key]]}</span>
          </li>
        ))}
      </ul>
      {card.feedback && <div className="scorecard__feedback"><TutorText text={card.feedback} /></div>}
    </section>
  )
}

export default ScorecardView
