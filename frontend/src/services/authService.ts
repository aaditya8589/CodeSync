import { API_BASE_URL } from "../config"

interface LoginResponse {
  token: string
}

export async function login(
  email: string,
  password: string
): Promise<LoginResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/auth/login`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
      }),
    }
  )

  if (!response.ok) {
    // The server says when the account has no password because it signs in with Google
    throw new Error((await response.text()) || "Invalid email or password")
  }

  return response.json()
}