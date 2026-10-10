import { API_BASE_URL } from "../config"

export type ExecutionStatus =
  | "SUCCESS"
  | "COMPILE_ERROR"
  | "RUNTIME_ERROR"
  | "TIME_LIMIT_EXCEEDED"
  | "MEMORY_LIMIT_EXCEEDED"
  | "INTERNAL_ERROR"

export interface ExecutionResult {
  status: ExecutionStatus
  stdout: string
  stderr: string
  exitCode: number | null
  durationMs: number
  outputTruncated: boolean
}

export async function runDocument(
  roomId: string,
  documentId: string,
  stdin: string
): Promise<ExecutionResult> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms/${roomId}/documents/${documentId}/run`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ stdin }),
    }
  )

  if (!response.ok) {
    throw new Error(await response.text() || `Run failed: ${response.status}`)
  }

  return response.json()
}
