import { API_BASE_URL } from "../config"

export interface RoomDocument {
  id: string
  fileName: string
  content: string
  updatedAt: string
  revision: number
}

export async function getDocuments(roomId: string): Promise<RoomDocument[]> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms/${roomId}/documents`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  )

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Failed to load documents: ${response.status} ${errorText}`)
  }

  return response.json()
}
export async function createDocument(roomId: string, fileName: string): Promise<RoomDocument> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(`${API_BASE_URL}/api/rooms/${roomId}/documents`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fileName }),
  })

  if (!response.ok) {
    throw new Error(await response.text() || `Could not create the file: ${response.status}`)
  }

  return response.json()
}
