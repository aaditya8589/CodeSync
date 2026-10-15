// The login token is a JWT; its payload names the user and when the login expires. Reading it
// here is only for display and to skip requests that would fail anyway: the server checks the
// signature and expiry on every request.
function payload(): { sub?: string; exp?: number } | null {
  const token = localStorage.getItem("token")
  if (!token) return null
  try {
    return JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")))
  } catch {
    return null
  }
}

export function currentUsername(): string | null {
  return payload()?.sub ?? null
}

/** A token that has not expired yet. A malformed one counts as none. */
export function hasSession(): boolean {
  const claims = payload()
  return Boolean(claims?.sub) && (claims?.exp === undefined || claims.exp * 1000 > Date.now())
}

export function signOut(): void {
  localStorage.removeItem("token")
}

/** The login page, remembering where to come back to. */
export function loginPath(expired = false): string {
  const here = window.location.pathname + window.location.search
  const params = new URLSearchParams()
  if (expired) params.set("expired", "1")
  if (here !== "/" && !here.startsWith("/login") && !here.startsWith("/register")) params.set("next", here)
  const query = params.toString()
  return query ? `/login?${query}` : "/login"
}

let leaving = false

/** The server said the login is no longer valid: start again from the login page. */
export function endExpiredSession(): void {
  signOut()
  // Several requests can fail at once; and on the login page itself there is nowhere to go
  if (leaving || window.location.pathname.startsWith("/login")) return
  leaving = true
  window.location.assign(loginPath(true))
}

/** Where to go after signing in: back to the page that needed it, or the dashboard. */
export function afterLoginPath(): string {
  const next = new URLSearchParams(window.location.search).get("next")
  // Only paths on this site: "//evil.example" or "https://..." would send the user elsewhere
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard"
}

/**
 * Every tab of a browser shares one login. When another tab signs in as someone else or signs
 * out, this tab would keep showing the old user's room while its requests already go out as the
 * new one. Reloading makes what the tab shows match who it acts as. (The "storage" event only
 * fires in the other tabs, never in the one that made the change.)
 */
export function followLoginChangesInOtherTabs(): () => void {
  const onChange = (event: StorageEvent) => {
    if (event.key === "token" || event.key === null) window.location.reload()
  }
  window.addEventListener("storage", onChange)
  return () => window.removeEventListener("storage", onChange)
}
