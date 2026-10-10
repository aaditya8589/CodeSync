// The login token is a JWT; its payload names the user. Reading it here is only for
// display: the server checks the signature on every request.
export function currentUsername(): string | null {
  const token = localStorage.getItem("token")
  if (!token) return null
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")
    return JSON.parse(atob(payload)).sub ?? null
  } catch {
    return null
  }
}

export function signOut(): void {
  localStorage.removeItem("token")
}
