import { API_BASE_URL } from "../config"
import { authorizedFetch } from "./http"

export type Verdict =
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TIME_LIMIT_EXCEEDED"
  | "MEMORY_LIMIT_EXCEEDED"
  | "RUNTIME_ERROR"
  | "COMPILE_ERROR"
  | "INTERNAL_ERROR"

export interface ProblemSummary {
  slug: string
  title: string
  difficulty: string
  solved: boolean
}

export interface ProblemDetail extends ProblemSummary {
  statement: string
  inputFormat: string
  outputFormat: string
  constraints: string
  examples: { input: string; output: string }[]
  testCount: number
}

export interface TestResult {
  number: number
  sample: boolean
  verdict: Verdict
  timeMs: number
  // Only filled for examples; hidden tests reveal nothing but the verdict
  input: string | null
  expectedOutput: string | null
  actualOutput: string | null
  errorOutput: string | null
}

export interface JudgeResult {
  verdict: Verdict
  passed: number
  total: number
  failedTest: number | null
  maxTimeMs: number
  compileOutput: string
  tests: TestResult[]
}

export interface SubmissionSummary {
  id: string
  verdict: Verdict
  passed: number
  total: number
  timeMs: number
  language: string
  fileName: string
  roomId: string | null
  createdAt: string
}

export const VERDICT_TEXT: Record<Verdict, string> = {
  ACCEPTED: "Accepted",
  WRONG_ANSWER: "Wrong answer",
  TIME_LIMIT_EXCEEDED: "Time limit exceeded",
  MEMORY_LIMIT_EXCEEDED: "Memory limit exceeded",
  RUNTIME_ERROR: "Runtime error",
  COMPILE_ERROR: "Compilation error",
  INTERNAL_ERROR: "Couldn't judge",
}

export function verdictTone(verdict: Verdict): "ok" | "err" | "limit" {
  if (verdict === "ACCEPTED") return "ok"
  if (verdict === "TIME_LIMIT_EXCEEDED" || verdict === "MEMORY_LIMIT_EXCEEDED") return "limit"
  return "err"
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("token")
  if (!token) throw new Error("No authentication token found")

  const response = await authorizedFetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...init.headers },
  })
  if (!response.ok) {
    throw new Error(await response.text() || `Request failed: ${response.status}`)
  }
  return response.json()
}

export const listProblems = () => request<ProblemSummary[]>("/api/problems")

export const getProblem = (slug: string) => request<ProblemDetail>(`/api/problems/${slug}`)

export const mySubmissions = (slug: string) => request<SubmissionSummary[]>(`/api/problems/${slug}/submissions`)

export const attachProblem = (roomId: string, slug: string | null) =>
  request<{ slug: string | null }>(`/api/rooms/${roomId}/problem`, {
    method: "PUT",
    body: JSON.stringify({ slug }),
  })

export const submitDocument = (roomId: string, documentId: string) =>
  request<JudgeResult>(`/api/rooms/${roomId}/submit`, {
    method: "POST",
    body: JSON.stringify({ documentId }),
  })
