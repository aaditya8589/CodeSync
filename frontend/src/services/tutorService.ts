import { API_BASE_URL } from "../config"
import { SseParser } from "../tutor/sse"
import { authorizedFetch } from "./http"

export type TutorLevel = "NUDGE" | "APPROACH" | "STEPS" | "REVIEW" | "ASK"

export interface TutorTurn {
  role: "STUDENT" | "TUTOR"
  text: string
}

export interface TutorStatus {
  enabled: boolean
  provider: string
  leftThisHour: number
  leftToday: number
}

export interface TutorHandlers {
  onText: (text: string) => void
}

export interface TutorAnswerEnd {
  codeRemoved: boolean
}

const authHeaders = (): Record<string, string> => {
  const token = localStorage.getItem("token")
  if (!token) throw new Error("No authentication token found")
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }
}

export async function getTutorStatus(): Promise<TutorStatus> {
  const response = await authorizedFetch(`${API_BASE_URL}/api/tutor/status`, { headers: authHeaders() })
  if (!response.ok) throw new Error(await response.text() || "Could not reach the tutor")
  return response.json()
}

/**
 * Asks for a hint and streams the answer to onText as it is written. Resolves when the answer
 * is complete; rejects with a message to show (a match, a limit, the model being down).
 */
export async function askTutor(
  roomId: string,
  request: { documentId: string; level: TutorLevel; question?: string; history: TutorTurn[] },
  handlers: TutorHandlers,
  signal?: AbortSignal,
): Promise<TutorAnswerEnd> {
  const response = await authorizedFetch(`${API_BASE_URL}/api/rooms/${roomId}/tutor`, {
    method: "POST",
    headers: { ...authHeaders(), Accept: "text/event-stream" },
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok || !response.body) {
    throw new Error(await response.text() || `The tutor could not answer (${response.status})`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  for (;;) {
    const { value, done } = await reader.read()
    const events = parser.push(done ? decoder.decode() : decoder.decode(value, { stream: true }))
    for (const event of events) {
      const data = JSON.parse(event.data) as { text?: string; message?: string; codeRemoved?: boolean }
      if (event.event === "error") throw new Error(data.message || "The tutor could not answer")
      if (event.event === "done") return { codeRemoved: Boolean(data.codeRemoved) }
      if (data.text) handlers.onText(data.text)
    }
    if (done) throw new Error("The tutor's answer was cut off. Try again.")
  }
}
