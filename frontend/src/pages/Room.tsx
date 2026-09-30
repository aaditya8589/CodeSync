import { useEffect, useState } from "react"
import { useParams, Link } from "react-router-dom"
import { getRoom, type Room as RoomType } from "../services/roomService"

function Room() {
  const { roomId } = useParams<{ roomId: string }>()

  const [room, setRoom] = useState<RoomType | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    const fetchRoom = async () => {
      if (!roomId) {
        setError("Room ID is missing")
        setLoading(false)
        return
      }

      try {
        const data = await getRoom(roomId)
        setRoom(data)
      } catch (error) {
        setError(
          error instanceof Error
            ? error.message
            : "Failed to load room"
        )
      } finally {
        setLoading(false)
      }
    }

    fetchRoom()
  }, [roomId])

  if (loading) {
    return <p>Loading room...</p>
  }

  if (error) {
    return (
      <div>
        <h1>Unable to open room</h1>
        <p>{error}</p>
        <Link to="/dashboard">Back to Dashboard</Link>
      </div>
    )
  }

  if (!room) {
    return <p>Room not found</p>
  }

  return (
    <div>
      <h1>{room.name}</h1>

      <p>Room ID: {room.id}</p>

      <p>Owner ID: {room.ownerId}</p>

      <Link to="/dashboard">Back to Dashboard</Link>
    </div>
  )
}

export default Room