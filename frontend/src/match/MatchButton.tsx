import type { Match } from "../services/matchService"
import { formatClock } from "./clock"
import { useCountdown } from "./useCountdown"

// The countdown lives here so only this button re-renders every quarter second, not the room
function MatchButton({ match, offset, pressed, onClick }: {
  match: Match | null
  offset: number
  pressed: boolean
  onClick: () => void
}) {
  const left = useCountdown(match, offset)
  const live = Boolean(match?.running && left > 0)
  return (
    <button
      type="button"
      className={`button button--quiet${live ? " match-button--live" : ""}`}
      aria-pressed={pressed}
      onClick={onClick}
      title={live ? "Time left in the match" : "Compete against the room"}
    >
      {live ? <>Match <span className="match-clock">{formatClock(left)}</span></> : "Compete"}
    </button>
  )
}

export default MatchButton
