import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import AuthLayout from "../components/AuthLayout"
import { login } from "../services/authService"

function Login() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)

  const navigate = useNavigate()

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError("")
    setLoading(true)

    try {
      const data = await login(email, password)
      localStorage.setItem("token", data.token)
      navigate("/dashboard")
    } catch (reason) {
      // A network failure is not a wrong password, so say which one it was
      setError(
        reason instanceof TypeError
          ? "Can't reach the server. Check that the backend is running."
          : "That email and password don't match an account."
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <form className="auth__form" onSubmit={handleSubmit}>
        <h2>Sign in</h2>

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
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        {error && <p className="message message--error" role="alert">{error}</p>}

        <button type="submit" className="button button--primary" disabled={loading}>
          {loading ? "Signing in..." : "Sign in"}
        </button>

        <p className="auth__switch">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </AuthLayout>
  )
}

export default Login
