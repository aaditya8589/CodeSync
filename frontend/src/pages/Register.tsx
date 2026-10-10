import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import AuthLayout from "../components/AuthLayout"
import { API_BASE_URL } from "../config"

// Validation errors arrive as {"field": "message"}; other errors as plain text
function readableError(body: string, status: number): string {
  try {
    const fields = JSON.parse(body)
    if (fields && typeof fields === "object") return Object.values(fields).join(". ")
  } catch {
    // not JSON
  }
  return body || `Registration failed (${status})`
}

function Register() {
  const navigate = useNavigate()

  const [username, setUsername] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const handleRegister = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault()

    setError("")
    setLoading(true)

    try {
      const response = await fetch(
        `${API_BASE_URL}/api/auth/register`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            username,
            email,
            password,
          }),
        }
      )

      if (!response.ok) {
        throw new Error(readableError(await response.text(), response.status))
      }

      navigate("/login")
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Registration failed"
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <form className="auth__form" onSubmit={handleRegister}>
        <h2>Create an account</h2>

        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            className="input"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
            minLength={3}
            maxLength={50}
          />
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            className="input"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            className="input"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={8}
          />
        </div>

        {error && <p className="message message--error" role="alert">{error}</p>}

        <button type="submit" className="button button--primary" disabled={loading}>
          {loading ? "Creating account..." : "Create account"}
        </button>

        <p className="auth__switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </AuthLayout>
  )
}

export default Register
