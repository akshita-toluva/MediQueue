import { createContext, useContext, useState } from 'react'
import api, { tokenStorage } from '../api/client'

const USER_KEY = 'mediqueue_user'
const AuthContext = createContext(null)

// On page load / refresh: restore the user, but ONLY if a token still exists.
// (If the 401 interceptor cleared the token, a leftover user entry is ignored.)
function loadUser() {
  if (!tokenStorage.get()) return null
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null   // corrupted entry -> treat as logged out
  }
}

export function AuthProvider({ children }) {
  // Passing the FUNCTION (not loadUser()) means it runs once, not on every render
  const [user, setUser] = useState(loadUser)

  // Backend AuthResponse = { token, role, name } for login, register and bootstrap
  function saveSession(data) {
    tokenStorage.set(data.token)
    const u = { name: data.name, role: data.role }
    localStorage.setItem(USER_KEY, JSON.stringify(u))
    setUser(u)
    return u   // caller uses the role to decide where to navigate
  }

  async function login(email, password) {
    const { data } = await api.post('/api/auth/login', { email, password })
    return saveSession(data)
  }

  async function register(name, email, password) {
    // Self-registration is PATIENT only; the backend rejects DOCTOR/ADMIN with 403.
    // Doctors and admins are created by an admin (Admin portal, later).
    const { data } = await api.post('/api/auth/register', { name, email, password, role: 'PATIENT' })
    return saveSession(data)   // backend returns a token, so the user is logged in immediately
  }

  async function logout() {
    try {
      // Revoke the token on the SERVER (Day 9 Redis blocklist), not just forget it locally.
      // Otherwise a copied token would keep working until it expires.
      await api.post('/api/auth/logout')
    } catch {
      // Token may already be expired/revoked; still log out locally
    } finally {
      tokenStorage.clear()
      localStorage.removeItem(USER_KEY)
      setUser(null)
    }
  }

  const value = { user, isLoggedIn: !!user, login, register, logout }
  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}