import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import Login from "./pages/Login"
import Register from "./pages/Register"
import Dashboard from "./pages/Dashboard"
import Room from "./pages/Room"
import WebSocketTest from "./components/WebSocketTest"

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/rooms/:roomId" element={<Room />} />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route
          path="/websocket-test"
          element={<WebSocketTest />}
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App