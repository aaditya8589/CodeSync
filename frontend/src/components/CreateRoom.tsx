import { useState } from "react"
import { createRoom, type Room } from "../services/roomService"

interface CreateRoomProps {
  onRoomCreated: (room: Room) => void
}

function CreateRoom({ onRoomCreated }: CreateRoomProps) {
  const [name, setName] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [success, setSuccess] = useState("")

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    setError("")
    setSuccess("")
    setLoading(true)

    try {
      const room = await createRoom(name)

      onRoomCreated(room)

      setSuccess(`Room "${room.name}" created successfully`)
      setName("")
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Failed to create room"
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h2>Create Room</h2>

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="room-name">Room name</label>

          <input
            id="room-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="e.g. DSA Practice"
            required
            minLength={3}
            maxLength={100}
          />
        </div>

        <button type="submit" disabled={loading}>
          {loading ? "Creating..." : "Create Room"}
        </button>
      </form>

      {error && <p>{error}</p>}

      {success && <p>{success}</p>}
    </div>
  )
}

export default CreateRoom