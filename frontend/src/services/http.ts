import { endExpiredSession } from "../auth/session"

/**
 * fetch for API calls made while signed in. A 401 means the login expired (or the token was
 * removed on the server's side), so instead of showing an error the user is sent to sign in
 * again and brought back afterwards.
 */
export async function authorizedFetch(input: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, init)
  if (response.status === 401) {
    endExpiredSession()
    throw new Error("Your session has expired. Please sign in again.")
  }
  return response
}
