
import { getMyRooms, type Room } from "../services/roomService"
import CreateRoom from "../components/CreateRoom"
import JoinRoom from "../components/JoinRoom"
import { useCallback, useEffect, useState } from "react"
import { Link } from "react-router-dom"
function Dashboard() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const fetchRooms = useCallback(async () => {
    try {
      const data = await getMyRooms()
      setRooms(data)
    } catch (error) {
      setError("Failed to load rooms")
      console.error(error)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    // Load once on mount; state is only set after the request resolves
    getMyRooms()
      .then(setRooms)
      .catch((error) => {
        setError("Failed to load rooms")
        console.error(error)
      })
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p>Loading rooms...</p>
  }

  if (error) {
    return <p>{error}</p>
  }

  return (
    <div>
      <h1>CodeSync Dashboard</h1>

      <CreateRoom
  onRoomCreated={(room) => {
    setRooms((currentRooms) => [...currentRooms, room])
  }}
/>
<JoinRoom
  onRoomJoined={fetchRooms}
/>

      <h2>My Rooms</h2>

      {rooms.length === 0 ? (
        <p>You are not a member of any rooms.</p>
      ) : (
        <ul>
          {rooms.map((room) => (
  <li key={room.id}>
    <Link to={`/rooms/${room.id}`}>
      {room.name}
    </Link>
  </li>
))}
        </ul>
      )}
    </div>
  )
}

export default Dashboard