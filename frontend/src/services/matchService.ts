import { API_BASE_URL } from "../config"
import { authorizedFetch } from "./http"

export interface MatchProblem {
  letter: string
  slug: string
  title: string
  difficulty: string
}

export interface StandingCell {
  problemSlug: string
  solved: boolean
  rejected: number
  solvedAtMinute: number | null
}

export interface StandingRow {
  rank: number
  username: string
  solved: number
  penalty: number
  cells: StandingCell[]
}

export interface Match {
  id: string
  host: string
  startedAt: string
  endsAt: string
  serverTime: string
  running: boolean
  joined: boolean
  problems: MatchProblem[]
  standings: StandingRow[]
}

export interface MatchChangedMessage {
  event: "STARTED" | "JOINED" | "SUBMITTED" | "ENDED"
  username: string
}

async function call(path: string, init: RequestInit = {}): Promise<Response> {
  const token = localStorage.getItem("token")
  if (!token) throw new Error("No authentication token found")
  const response = await authorizedFetch(`${API_BASE_URL}/api/rooms/${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...init.headers },
  })
  if (!response.ok) throw new Error((await response.text()) || `Request failed: ${response.status}`)
  return response
}

/** The room's running or last match, or null if it never had one. */
export async function getMatch(roomId: string): Promise<Match | null> {
  const response = await call(`${roomId}/match`)
  return response.status === 204 ? null : response.json()
}

export const startMatch = async (roomId: string, problemSlugs: string[], durationMinutes: number): Promise<Match> =>
  (await call(`${roomId}/match`, { method: "POST", body: JSON.stringify({ problemSlugs, durationMinutes }) })).json()

export const joinMatch = async (roomId: string): Promise<Match> =>
  (await call(`${roomId}/match/join`, { method: "POST" })).json()

export const endMatch = async (roomId: string): Promise<Match> =>
  (await call(`${roomId}/match/end`, { method: "POST" })).json()
