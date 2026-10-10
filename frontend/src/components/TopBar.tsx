import type { ReactNode } from "react"
import { Link, useNavigate } from "react-router-dom"
import { signOut } from "../auth/session"

interface TopBarProps {
  title?: string
  children?: ReactNode
}

function TopBar({ title, children }: TopBarProps) {
  const navigate = useNavigate()

  return (
    <header className="topbar">
      <div className="topbar__trail">
        <Link to="/dashboard" className="brand">CodeSync</Link>
        {title && (
          <>
            <span className="topbar__sep" aria-hidden="true">/</span>
            <span className="topbar__title">{title}</span>
          </>
        )}
      </div>
      <div className="topbar__end">
        {children}
        <button
          type="button"
          className="button button--quiet"
          onClick={() => {
            signOut()
            navigate("/login")
          }}
        >
          Sign out
        </button>
      </div>
    </header>
  )
}

export default TopBar
