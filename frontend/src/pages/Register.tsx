import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { API_BASE_URL } from "../config"

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
        const errorText = await response.text()
        throw new Error(
          errorText || `Registration failed: ${response.status}`
        )
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
    <div>
      <h1>Create your CodeSync account</h1>
      <p>Register to start collaborating.</p>

      <form onSubmit={handleRegister}>
        <div>
          <label htmlFor="username">Username</label>
          <br />
          <input
            id="username"
            type="text"
            value={username}
            onChange={(event) =>
              setUsername(event.target.value)
            }
            required
            minLength={3}
            maxLength={50}
          />
        </div>

        <div>
          <label htmlFor="email">Email</label>
          <br />
          <input
            id="email"
            type="email"
            value={email}
            onChange={(event) =>
              setEmail(event.target.value)
            }
            required
          />
        </div>

        <div>
          <label htmlFor="password">Password</label>
          <br />
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) =>
              setPassword(event.target.value)
            }
            required
            minLength={8}
          />
        </div>

        {error && (
          <p>
            {error}
          </p>
        )}

        <button type="submit" disabled={loading}>
          {loading ? "Creating account..." : "Register"}
        </button>
      </form>

      <p>
        Already have an account?{" "}
        <Link to="/login">Login</Link>
      </p>
    </div>
  )
}

export default Register