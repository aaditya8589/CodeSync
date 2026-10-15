import { BrowserRouter, Routes, Route, Navigate, useParams } from "react-router-dom"
import Login from "./pages/Login"
import Register from "./pages/Register"
import Dashboard from "./pages/Dashboard"
import Room from "./pages/Room"
import Problems from "./pages/Problems"
import Problem from "./pages/Problem"
import RequireSession from "./auth/RequireSession"
import NewMatch from "./pages/NewMatch"
import MatchEntry from "./pages/MatchEntry"

// A fresh Room for every room id, so going from one room to another (e.g. into a new match
// room) starts from nothing instead of carrying the last room's files and connection along
function RoomPage() {
  const { roomId } = useParams()
  return <Room key={roomId} />
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<RequireSession><Dashboard /></RequireSession>} />
        <Route path="/rooms/:roomId" element={<RequireSession><RoomPage /></RequireSession>} />
        <Route path="/problems" element={<RequireSession><Problems /></RequireSession>} />
        <Route path="/problems/:slug" element={<RequireSession><Problem /></RequireSession>} />
        <Route path="/matches/new" element={<RequireSession><NewMatch /></RequireSession>} />
        <Route path="/match/:roomId" element={<RequireSession><MatchEntry /></RequireSession>} />
        <Route path="/" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App