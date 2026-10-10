// Set VITE_API_URL when building for a deployed backend, e.g. https://api.example.com
export const API_BASE_URL: string = import.meta.env.VITE_API_URL ?? "http://localhost:8080"

// Same host as the API; http becomes ws and https becomes wss
export const WEBSOCKET_URL = API_BASE_URL.replace(/^http/, "ws") + "/ws"
