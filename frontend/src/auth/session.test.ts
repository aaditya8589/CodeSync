import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { afterLoginPath, currentUsername, endExpiredSession, hasSession, loginPath } from "./session"

// Tests run without a browser, so storage and the address bar are small stand-ins
let stored: Record<string, string> = {}
const location = { pathname: "/", search: "", assign: vi.fn() }

const token = (claims: object) => `x.${btoa(JSON.stringify(claims))}.sig`
const at = (path: string, search = "") => { location.pathname = path; location.search = search }

beforeEach(() => {
  stored = {}
  location.assign = vi.fn()
  at("/")
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => stored[key] ?? null,
    setItem: (key: string, value: string) => { stored[key] = value },
    removeItem: (key: string) => { delete stored[key] },
  })
  vi.stubGlobal("window", { location })
})
afterEach(() => vi.unstubAllGlobals())

describe("session", () => {
  it("a token that has not expired is a session", () => {
    stored.token = token({ sub: "ada", exp: Date.now() / 1000 + 600 })
    expect(hasSession()).toBe(true)
    expect(currentUsername()).toBe("ada")
  })

  it("an expired, malformed or missing token is not", () => {
    stored.token = token({ sub: "ada", exp: Date.now() / 1000 - 1 })
    expect(hasSession()).toBe(false)
    stored.token = "garbage"
    expect(hasSession()).toBe(false)
    delete stored.token
    expect(hasSession()).toBe(false)
  })

  it("the login page remembers where to come back to", () => {
    at("/rooms/abc", "?x=1")
    expect(loginPath(true)).toBe("/login?expired=1&next=%2Frooms%2Fabc%3Fx%3D1")
    at("/")
    expect(loginPath()).toBe("/login")
  })

  it("after signing in, goes back only to pages on this site", () => {
    at("/login", "?next=%2Frooms%2Fabc")
    expect(afterLoginPath()).toBe("/rooms/abc")
    at("/login", "?next=%2F%2Fevil.example.com")
    expect(afterLoginPath()).toBe("/dashboard")
    at("/login", "?next=https%3A%2F%2Fevil.example.com")
    expect(afterLoginPath()).toBe("/dashboard")
    at("/login")
    expect(afterLoginPath()).toBe("/dashboard")
  })

  it("an expired session is cleared and sent to sign in", () => {
    stored.token = token({ sub: "ada" })
    at("/rooms/abc")
    endExpiredSession()
    expect(stored.token).toBeUndefined()
    expect(location.assign).toHaveBeenCalledWith("/login?expired=1&next=%2Frooms%2Fabc")
  })
})
