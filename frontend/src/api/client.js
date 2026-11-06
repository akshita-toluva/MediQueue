import axios from 'axios'

const TOKEN_KEY = 'mediqueue_token'

// One place that reads/writes the JWT
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// IMPROVEMENT 1: no baseURL. Every call is relative ("/api/...").
// Dev: the Vite proxy forwards it to :8080. Production: Caddy serves frontend + backend
// on ONE domain. Same code everywhere, no VITE_API_URL env var, no CORS.
const api = axios.create({ timeout: 15000 })

// REQUEST interceptor: attach the JWT to every call automatically
api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// RESPONSE interceptor: expired/invalid token -> clear it and go to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const isAuthCall = error.config?.url?.startsWith('/api/auth/')
    // Skip auth calls: a wrong password on the login page is a normal 401, not "session expired"
    if (status === 401 && !isAuthCall) {
      tokenStorage.clear()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

// Turns any backend error into one readable sentence for the UI
export function getErrorMessage(error) {
  if (!error.response) {
    return 'Cannot reach the server. Check your connection and try again.'
  }
  const { status, data, headers } = error.response

  // IMPROVEMENT 2: understands Day 10B rate limiting (429 + Retry-After)
  if (status === 429) {
    const wait = headers['retry-after']
    return wait
      ? `Too many attempts. Try again in ${wait} seconds.`
      : 'Too many attempts. Please wait and try again.'
  }
  // GlobalExceptionHandler shape: { "error": "..." }
  if (data?.error) return data.error
  // Validation errors shape: { "email": "must not be blank", ... }
  if (data && typeof data === 'object') return Object.values(data).join(' ')
  return 'Something went wrong. Please try again.'
}

export default api