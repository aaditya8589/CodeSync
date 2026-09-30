import { useEffect, useState } from "react"
import { getMyRooms, type Room } from "../services/roomService"

function Dashboard() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    const fetchRooms = async () => {
      try {
        const data = await getMyRooms()
        setRooms(data)
      } catch (error) {
        setError("Failed to load rooms")
        console.error(error)
      } finally {
        setLoading(false)
      }
    }

    fetchRooms()
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

      <h2>My Rooms</h2>

      {rooms.length === 0 ? (
        <p>You are not a member of any rooms.</p>
      ) : (
        <ul>
          {rooms.map((room) => (
            <li key={room.id}>
              {room.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default Dashboard