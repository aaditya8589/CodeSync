const API_BASE_URL = "http://localhost:8080"

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