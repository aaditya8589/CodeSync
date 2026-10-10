import { useEffect, useState } from "react"
import { Link, Navigate, useNavigate } from "react-router-dom"
import CreateRoom from "../components/CreateRoom"
import JoinRoom from "../components/JoinRoom"
import TopBar from "../components/TopBar"
import { getMyRooms, type Room } from "../services/roomService"

const dateFormat: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" }

function Dashboard() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const navigate = useNavigate()

  useEffect(() => {
    // Load once on mount; state is only set after the request resolves
    getMyRooms()
      .then(setRooms)
      .catch(() => setError("Couldn't load your rooms. Check that the backend is running, then reload."))
      .finally(() => setLoading(false))
  }, [])

  if (!localStorage.getItem("token")) {
    return <Navigate to="/login" replace />
  }

  return (
    <>
      <TopBar />
      <div className="dashboard">
        <main>
          <h1>Your rooms</h1>

          {loading && <p className="empty">Loading rooms...</p>}
          {error && <p className="message message--error" role="alert">{error}</p>}

          {!loading && !error && rooms.length === 0 && (
            <p className="empty">
              No rooms yet. Create one, or join with a room ID someone shared with you.
            </p>
          )}

          {rooms.length > 0 && (
            <ul className="room-list">
              {[...rooms]
                .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                .map((room) => (
                  <li key={room.id}>
                    <Link to={`/rooms/${room.id}`}>
                      <span className="room-list__name">{room.name}</span>
                      <span className="room-list__date">
                        Created {new Date(room.createdAt).toLocaleDateString([], dateFormat)}
                      </span>
                    </Link>
                  </li>
                ))}
            </ul>
          )}
        </main>

        <aside className="side-forms">
          <CreateRoom onRoomCreated={(room) => navigate(`/rooms/${room.id}`)} />
          <JoinRoom onRoomJoined={(roomId) => navigate(`/rooms/${roomId}`)} />
        </aside>
      </div>
    </>
  )
}

export default Dashboard
