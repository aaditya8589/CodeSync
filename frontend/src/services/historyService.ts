import { API_BASE_URL } from "../config"

export interface Version {
  fromRevision: number
  toRevision: number
  authors: string[]
  startedAt: string
  endedAt: string
  edits: number
}

export interface DocumentHistory {
  documentId: string
  fileName: string
  currentRevision: number
  // Oldest revision that can be viewed; null until the file's first edit
  historyStartRevision: number | null
  versions: Version[]
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("token")
  if (!token) throw new Error("No authentication token found")

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...init.headers,
    },
  })

  if (!response.ok) {
    throw new Error(await response.text() || `Request failed: ${response.status}`)
  }
  return response.json()
}

const documentPath = (roomId: string, documentId: string) =>
  `/api/rooms/${roomId}/documents/${documentId}`

export function getHistory(roomId: string, documentId: string): Promise<DocumentHistory> {
  return request(`${documentPath(roomId, documentId)}/history`)
}

export async function getRevisionContent(roomId: string, documentId: string, revision: number): Promise<string> {
  const result = await request<{ revision: number; content: string }>(
    `${documentPath(roomId, documentId)}/revisions/${revision}`
  )
  return result.content
}

export function restoreRevision(
  roomId: string,
  documentId: string,
  revision: number
): Promise<{ revision: number; changed: boolean }> {
  return request(`${documentPath(roomId, documentId)}/restore`, {
    method: "POST",
    body: JSON.stringify({ revision }),
  })
}
