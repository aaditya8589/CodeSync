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
    throw new Error("Invalid email or password")
  }

  return response.json()
}