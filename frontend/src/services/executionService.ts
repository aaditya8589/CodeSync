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

const STATUS_TEXT: Record<ExecutionStatus, string> = {
  SUCCESS: "Finished",
  COMPILE_ERROR: "Compilation error",
  RUNTIME_ERROR: "Runtime error",
  TIME_LIMIT_EXCEEDED: "Time limit exceeded (2 s)",
  MEMORY_LIMIT_EXCEEDED: "Memory limit exceeded (256 MB)",
  INTERNAL_ERROR: "Could not run the code",
}

export function formatResult(result: ExecutionResult): string {
  const exit = result.exitCode !== null && result.status !== "SUCCESS" ? `, exit code ${result.exitCode}` : ""
  const lines = [`${STATUS_TEXT[result.status]} in ${result.durationMs} ms${exit}`]

  if (result.stdout) lines.push("", result.stdout.trimEnd())
  if (result.stderr) lines.push("", "--- stderr ---", result.stderr.trimEnd())
  if (result.outputTruncated) lines.push("", "(output truncated at 64 KB)")

  return lines.join("\n")
}
