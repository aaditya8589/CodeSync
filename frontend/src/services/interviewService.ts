import { API_BASE_URL } from "../config"
import { authorizedFetch } from "./http"

export type InterviewState = "LOBBY" | "RUNNING" | "ENDED"
export type Recommendation = "STRONG_HIRE" | "HIRE" | "LEAN_HIRE" | "LEAN_NO_HIRE" | "NO_HIRE"

export interface InterviewEntryNote {
  text: string
  at: string
  // Minutes since the start; null before it
  minute: number | null
}

export interface Scorecard {
  problemSolving: number
  code: number
  communication: number
  complexity: number
  recommendation: Recommendation
  feedback: string
  at: string
}

/**
 * An interview as one of its two people sees it. pendingCandidate, guide, nextHint, hintsLeft
 * and notes are only filled in for the interviewer; problem is null for the candidate until
 * it is revealed.
 */
export interface Interview {
  id: string
  role: "INTERVIEWER" | "CANDIDATE"
  interviewer: string
  candidate: string | null
  pendingCandidate: string | null
  state: InterviewState
  durationMinutes: number
  startedAt: string | null
  endsAt: string | null
  serverTime: string
  meetingLink: string | null
  revealed: boolean
  problem: { slug: string; title: string; difficulty: string; expectedComplexity: string | null } | null
  hintsGiven: InterviewEntryNote[]
  guide: string | null
  nextHint: string | null
  hintsLeft: number | null
  notes: InterviewEntryNote[] | null
  lastSubmission: { verdict: string; passed: number; total: number; growthSummary: string | null; at: string } | null
  scorecard: Scorecard | null
  nextRoomId: string | null
}

export interface InterviewEntry {
  roomId: string
  interviewer: string
  state: InterviewState
  durationMinutes: number
  myStatus: "INTERVIEWER" | "CANDIDATE" | "PENDING" | "TAKEN" | "NONE"
}

export interface InterviewSummary {
  roomId: string
  role: "INTERVIEWER" | "CANDIDATE"
  partner: string | null
  problemTitle: string | null
  difficulty: string | null
  state: InterviewState
  createdAt: string
  hintsGiven: number
  averageScore: number | null
  recommendation: Recommendation | null
}

export interface InterviewChangedMessage {
  event: "REQUESTED" | "APPROVED" | "STARTED" | "REVEALED" | "HINT" | "ENDED" | "SCORED" | "SWAPPED"
  username: string
  roomId: string | null
}

export const RECOMMENDATION_TEXT: Record<Recommendation, string> = {
  STRONG_HIRE: "Strong hire",
  HIRE: "Hire",
  LEAN_HIRE: "Lean hire",
  LEAN_NO_HIRE: "Lean no hire",
  NO_HIRE: "No hire",
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

/** The link to share: whoever opens it can ask to be the candidate. */
export const interviewLink = (roomId: string) => `${window.location.origin}/interview/${roomId}`

export const createInterview = (request: { problemSlug?: string; difficulty?: string; durationMinutes: number; meetingLink?: string }) =>
  post<{ roomId: string }>("interviews", request)

export const getInterviewEntry = (roomId: string) => call<InterviewEntry>(`interviews/${roomId}`)
export const askToJoinInterview = (roomId: string) => post<InterviewEntry>(`interviews/${roomId}/requests`)
export const myInterviews = () => call<InterviewSummary[]>("interviews/mine")

export const getInterview = (roomId: string) => call<Interview>(`rooms/${roomId}/interview`)
export const approveCandidate = (roomId: string, username: string) =>
  post<Interview>(`rooms/${roomId}/interview/candidate/${encodeURIComponent(username)}/approve`)
export const declineCandidate = (roomId: string, username: string) =>
  post<Interview>(`rooms/${roomId}/interview/candidate/${encodeURIComponent(username)}/decline`)
export const startInterview = (roomId: string) => post<Interview>(`rooms/${roomId}/interview/start`)
export const revealProblem = (roomId: string) => post<Interview>(`rooms/${roomId}/interview/reveal`)
export const giveHint = (roomId: string) => post<Interview>(`rooms/${roomId}/interview/hints`)
export const addNote = (roomId: string, text: string) => post<Interview>(`rooms/${roomId}/interview/notes`, { text })
export const endInterview = (roomId: string) => post<Interview>(`rooms/${roomId}/interview/end`)
export const saveScorecard = (roomId: string, card: Omit<Scorecard, "at">) =>
  post<Interview>(`rooms/${roomId}/interview/scorecard`, card)
export const swapRoles = (roomId: string) => post<{ roomId: string }>(`rooms/${roomId}/interview/swap`)
