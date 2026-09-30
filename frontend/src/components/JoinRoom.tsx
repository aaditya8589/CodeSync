import { useState } from "react"
import { joinRoom } from "../services/roomService"

interface JoinRoomProps {
  onRoomJoined: () => void
}

function JoinRoom({ onRoomJoined }: JoinRoomProps) {
  const [roomId, setRoomId] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [success, setSuccess] = useState("")

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    setError("")
    setSuccess("")
    setLoading(true)

    try {
      await joinRoom(roomId)

      setSuccess("Joined room successfully")
      setRoomId("")

      onRoomJoined()
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to join room"
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h2>Join Room</h2>

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="room-id">Room ID</label>

          <input
            id="room-id"
            type="text"
            value={roomId}
            onChange={(event) => setRoomId(event.target.value)}
            placeholder="Enter room ID"
            required
          />
        </div>

        <button type="submit" disabled={loading}>
          {loading ? "Joining..." : "Join Room"}
        </button>
      </form>

      {error && <p>{error}</p>}

      {success && <p>{success}</p>}
    </div>
  )
}

export default JoinRoom
