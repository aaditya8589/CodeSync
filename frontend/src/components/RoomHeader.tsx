interface RoomHeaderProps {
  roomName: string
  roomId: string
}

function RoomHeader({ roomName, roomId }: RoomHeaderProps) {
  return (
    <header>
      <div>
        <h1>CodeSync</h1>
        <h2>{roomName}</h2>
      </div>

      <div>
        <span>Room ID: {roomId}</span>
      </div>
    </header>
  )
}

export default RoomHeader