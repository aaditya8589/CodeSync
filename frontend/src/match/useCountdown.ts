import { useEffect, useState } from "react"
import type { Match } from "../services/matchService"
import { remainingMs } from "./clock"

/** Milliseconds left in the match, on the server's clock, updated four times a second while it runs. */
export function useCountdown(match: Match | null, offset: number): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!match?.running) return
    const timer = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [match?.running, match?.endsAt])
  // No end time while the match waits in its lobby
  return match?.endsAt ? remainingMs(match.endsAt, offset, now) : 0
}
