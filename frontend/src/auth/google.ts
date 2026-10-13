import { API_BASE_URL } from "../config"

// The parts of Google Identity Services (https://accounts.google.com/gsi/client) this app uses
interface GoogleCredentialResponse {
  credential: string
}

interface GoogleIdentity {
  initialize(options: {
    client_id: string
    callback: (response: GoogleCredentialResponse) => void
    ux_mode?: "popup" | "redirect"
  }): void
  renderButton(parent: HTMLElement, options: {
    type?: "standard" | "icon"
    theme?: "outline" | "filled_blue" | "filled_black"
    size?: "large" | "medium" | "small"
    text?: "signin_with" | "signup_with" | "continue_with"
    shape?: "rectangular" | "pill"
    width?: number
  }): void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdentity } }
  }
}

const SCRIPT_URL = "https://accounts.google.com/gsi/client"
let scriptLoading: Promise<GoogleIdentity> | null = null

export function loadGoogleIdentity(): Promise<GoogleIdentity> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  scriptLoading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script")
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => window.google?.accounts?.id ? resolve(window.google.accounts.id) : reject(new Error("Google sign-in did not load"))
    script.onerror = () => {
      scriptLoading = null
      reject(new Error("Google sign-in did not load"))
    }
    document.head.appendChild(script)
  })
  return scriptLoading
}

/** The client id to show Google's button with, or null when the server has Google sign-in off. */
export async function getGoogleClientId(): Promise<string | null> {
  const response = await fetch(`${API_BASE_URL}/api/auth/google/config`)
  if (!response.ok) return null
  const { clientId } = (await response.json()) as { clientId: string | null }
  return clientId || null
}

/** Exchanges the ID token from Google's button for a CodeSync token. */
export async function signInWithGoogle(credential: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/auth/google`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ credential }),
  })
  if (!response.ok) {
    throw new Error((await response.text()) || "Google sign-in failed")
  }
  return ((await response.json()) as { token: string }).token
}
