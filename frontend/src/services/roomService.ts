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
export async function createRoom(name: string): Promise<Room> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        name,
      }),
    }
  )

  if (!response.ok) {
    const errorText = await response.text()

    throw new Error(
      `Failed to create room: ${response.status} ${errorText}`
    )
  }

  return response.json()
}
export async function joinRoom(roomId: string): Promise<void> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms/${roomId}/join`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  )

  if (!response.ok) {
    const errorText = await response.text()

    throw new Error(
      `Failed to join room: ${response.status} ${errorText}`
    )
  }
}

export async function getRoom(roomId: string): Promise<Room> {
  const token = localStorage.getItem("token")

  if (!token) {
    throw new Error("No authentication token found")
  }

  const response = await fetch(
    `${API_BASE_URL}/api/rooms/${roomId}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  )

  if (!response.ok) {
    const errorText = await response.text()

    throw new Error(
      `Failed to load room: ${response.status} ${errorText}`
    )
  }

  return response.json()
}