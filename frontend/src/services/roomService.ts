const API_BASE_URL = "http://localhost:8080"

export interface Room {
  id: string
  name: string
  ownerId: string
  createdAt: string
}

export async function getMyRooms(): Promise<Room[]> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  )

  if (!response.ok) {
    throw new Error("Failed to fetch rooms")
  }

  return response.json()
}