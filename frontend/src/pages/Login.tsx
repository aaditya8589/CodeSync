import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { login } from "../services/authService"

function Login() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const navigate = useNavigate()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    setError("")
    setLoading(true)

    try {
      const data = await login(email, password)

      // Store JWT
      localStorage.setItem("token", data.token)

      console.log("Login successful")

      // Redirect to dashboard
      navigate("/dashboard")

    } catch (error) {
      setError("Invalid email or password")
      console.error(error)

    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h1>Login to CodeSync</h1>

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email">Email</label>

          <input
            id="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div>
          <label htmlFor="password">Password</label>

          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        {error && <p>{error}</p>}

        <button type="submit" disabled={loading}>
          {loading ? "Logging in..." : "Login"}
        </button>
      </form>
    </div>
  )
}

export default Login