import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { hasSession, loginPath } from "./session"

// Guards a page before it mounts, so none of its requests start with a missing or dead token
function RequireSession({ children }: { children: ReactNode }) {
  if (!hasSession()) {
    return <Navigate to={loginPath(localStorage.getItem("token") !== null)} replace />
  }
  return children
}

export default RequireSession
