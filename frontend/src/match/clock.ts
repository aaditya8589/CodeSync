// The server decides when a match ends. A browser's clock can be minutes off, so the countdown
// uses the difference between the server's time (sent with every match) and the browser's.

/** How far the server's clock is ahead of this browser's, in ms. */
export function serverOffset(serverTime: string, receivedAt: number): number {
  return Date.parse(serverTime) - receivedAt
}

export function remainingMs(endsAt: string, offset: number, now: number): number {
  return Math.max(0, Date.parse(endsAt) - (now + offset))
}

/** 5:07, 59:59, 1:00:00 */
export function formatClock(ms: number): string {
  const total = Math.ceil(ms / 1000)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const ss = String(seconds).padStart(2, "0")
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, "0")}:${ss}` : `${minutes}:${ss}`
}

/** A match file is private while its time has not run out (on the server's clock). */
export function isPrivateNow(privateUntil: string | null | undefined, offset: number, now: number): boolean {
  return privateUntil != null && Date.parse(privateUntil) > now + offset
}
