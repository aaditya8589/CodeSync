import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import Login from "./pages/Login"
import Register from "./pages/Register"
import Dashboard from "./pages/Dashboard"
import Room from "./pages/Room"
import Problems from "./pages/Problems"
import Problem from "./pages/Problem"
import RequireSession from "./auth/RequireSession"

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<RequireSession><Dashboard /></RequireSession>} />
        <Route path="/rooms/:roomId" element={<RequireSession><Room /></RequireSession>} />
        <Route path="/problems" element={<RequireSession><Problems /></RequireSession>} />
        <Route path="/problems/:slug" element={<RequireSession><Problem /></RequireSession>} />
        <Route path="/" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App