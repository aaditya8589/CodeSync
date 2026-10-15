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

export type MatchState = "LOBBY" | "RUNNING" | "ENDED"

/** The match of a match room. startedAt and endsAt are null in the lobby. */
export interface Match {
  id: string
  host: string
  state: MatchState
  durationMinutes: number
  startedAt: string | null
  endsAt: string | null
  serverTime: string
  running: boolean
  joined: boolean
  problems: MatchProblem[]
  standings: StandingRow[]
  // People waiting to be let in; only filled in for the host
  requests: string[]
}

/** What a match link shows before you are let in. */
export interface MatchEntry {
  roomId: string
  title: string
  host: string
  state: MatchState
  problemCount: number
  durationMinutes: number
  myStatus: "NONE" | "PENDING" | "APPROVED" | "DECLINED"
}

export interface MatchSummary {
  roomId: string
  title: string
  host: string
  state: MatchState
}

export interface MatchChangedMessage {
  event: "CREATED" | "REQUESTED" | "APPROVED" | "STARTED" | "SUBMITTED" | "ENDED"
  username: string
  matchRoomId: string | null
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("token")
  if (!token) throw new Error("No authentication token found")
  const response = await authorizedFetch(`${API_BASE_URL}/api/${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...init.headers },
  })
  if (!response.ok) throw new Error((await response.text()) || `Request failed: ${response.status}`)
  return response.json()
}

const post = <T>(path: string, body?: unknown) =>
  call<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) })

/** The link to share: whoever opens it can ask the host to let them in. */
export const matchLink = (roomId: string) => `${window.location.origin}/match/${roomId}`

export const createMatch = (problemSlugs: string[], durationMinutes: number, fromRoomId?: string) =>
  post<{ roomId: string; title: string }>("matches", { problemSlugs, durationMinutes, fromRoomId })

export const getMatchEntry = (roomId: string) => call<MatchEntry>(`matches/${roomId}`)

export const requestToJoin = (roomId: string) => post<MatchEntry>(`matches/${roomId}/requests`)

export const getMatch = (roomId: string) => call<Match>(`rooms/${roomId}/match`)

export const matchesMadeFrom = (roomId: string) => call<MatchSummary[]>(`rooms/${roomId}/matches`)

export const approvePlayer = (roomId: string, username: string) =>
  post<Match>(`rooms/${roomId}/match/requests/${encodeURIComponent(username)}/approve`)

export const declinePlayer = (roomId: string, username: string) =>
  post<Match>(`rooms/${roomId}/match/requests/${encodeURIComponent(username)}/decline`)

export const startMatch = (roomId: string) => post<Match>(`rooms/${roomId}/match/start`)

export const endMatch = (roomId: string) => post<Match>(`rooms/${roomId}/match/end`)
