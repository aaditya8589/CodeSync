import { useState, type FormEvent } from "react"
import { joinRoom } from "../services/roomService"

interface JoinRoomProps {
  onRoomJoined: (roomId: string) => void
}

function JoinRoom({ onRoomJoined }: JoinRoomProps) {
  const [roomId, setRoomId] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError("")
    setLoading(true)

    const id = roomId.trim()
    try {
      await joinRoom(id)
      setRoomId("")
      onRoomJoined(id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not join the room")
    } finally {
      setLoading(false)
    }
  }

  return (
    <form className="side-form" onSubmit={handleSubmit}>
      <h2>Join a room</h2>
      <div className="field">
        <label htmlFor="room-id">Room ID someone shared with you</label>
        <input
          id="room-id"
          className="input"
          value={roomId}
          onChange={(event) => setRoomId(event.target.value)}
          placeholder="e.g. 038ceccb-2f6b-41f4-..."
          required
        />
      </div>
      <button type="submit" className="button" disabled={loading}>
        {loading ? "Joining..." : "Join room"}
      </button>
      {error && <p className="message message--error" role="alert">{error}</p>}
    </form>
  )
}

export default JoinRoom
