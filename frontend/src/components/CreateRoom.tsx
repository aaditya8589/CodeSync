import { useState, type FormEvent } from "react"
import { createRoom, type Room } from "../services/roomService"

interface CreateRoomProps {
  onRoomCreated: (room: Room) => void
}

function CreateRoom({ onRoomCreated }: CreateRoomProps) {
  const [name, setName] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError("")
    setLoading(true)

    try {
      onRoomCreated(await createRoom(name))
      setName("")
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not create the room")
    } finally {
      setLoading(false)
    }
  }

  return (
    <form className="side-form" onSubmit={handleSubmit}>
      <h2>New room</h2>
      <div className="field">
        <label htmlFor="room-name">Name</label>
        <input
          id="room-name"
          className="input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="e.g. Graphs practice"
          required
          minLength={3}
          maxLength={100}
        />
      </div>
      <button type="submit" className="button button--primary" disabled={loading}>
        {loading ? "Creating..." : "Create room"}
      </button>
      {error && <p className="message message--error" role="alert">{error}</p>}
    </form>
  )
}

export default CreateRoom
