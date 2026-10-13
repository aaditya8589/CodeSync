import { useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { getGoogleClientId, loadGoogleIdentity, signInWithGoogle } from "../auth/google"
import { afterLoginPath } from "../auth/session"

// Google draws its own button (its branding rules require that), into the div below
function GoogleSignIn({ text }: { text: "signin_with" | "signup_with" }) {
  const buttonRef = useRef<HTMLDivElement>(null)
  const [clientId, setClientId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const navigate = useNavigate()

  // Off on this server, or the server is down: the email form still works, so stay quiet
  useEffect(() => {
    let cancelled = false
    getGoogleClientId()
      .then((id) => { if (!cancelled) setClientId(id) })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // Runs once the container below exists
  useEffect(() => {
    if (!clientId) return
    let cancelled = false

    loadGoogleIdentity()
      .then((google) => {
        const container = buttonRef.current
        if (cancelled || !container) return
        google.initialize({
          client_id: clientId,
          ux_mode: "popup",
          callback: async ({ credential }) => {
            setBusy(true)
            setError("")
            try {
              localStorage.setItem("token", await signInWithGoogle(credential))
              navigate(afterLoginPath())
            } catch (reason) {
              setError(reason instanceof Error ? reason.message : "Google sign-in failed")
            } finally {
              setBusy(false)
            }
          },
        })
        container.replaceChildren()
        google.renderButton(container, {
          type: "standard",
          theme: "filled_black",
          size: "large",
          text,
          shape: "rectangular",
          width: Math.min(360, container.clientWidth || 360),
        })
      })
      .catch(() => {
        if (!cancelled) setError("Google sign-in couldn't load. Check your connection or use your email.")
      })

    return () => {
      cancelled = true
    }
  }, [clientId, navigate, text])

  if (!clientId) return null

  return (
    <div className="google-signin">
      <div className="google-signin__divider" aria-hidden="true"><span>or</span></div>
      <div ref={buttonRef} className="google-signin__button" aria-busy={busy} />
      {busy && <p className="message">Signing in with Google...</p>}
      {error && <p className="message message--error" role="alert">{error}</p>}
    </div>
  )
}

export default GoogleSignIn
