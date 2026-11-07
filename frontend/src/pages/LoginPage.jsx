import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext'
import { getErrorMessage } from '../api/client'
import { ROLE_HOME } from '../auth/roles'
import FormField from '../components/FormField'
import AuthShell from '../components/AuthShell'

export default function LoginPage() {
  const { login, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // Already logged in? Don't show the form; go to this role's home.
  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />

  async function handleSubmit(e) {
    e.preventDefault()   // stop the browser's default full-page form submit
    setError('')
    setSubmitting(true)
    try {
      const u = await login(email.trim(), password)
      // If a guard sent them here from a page (e.g. /patient/book), return there,
      // but only if that page belongs to their role.
      const from = location.state?.from
      const home = ROLE_HOME[u.role]
      navigate(from && from.startsWith(home) ? from : home, { replace: true })
    } catch (err) {
      // 401 -> "Invalid email or password"; 429 -> "Try again in N seconds" (Day 10B)
      setError(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Sign in" subtitle="Book a visit or check your place in the queue.">
          <form onSubmit={handleSubmit} noValidate={false}>
            {error && (
              <p role="alert" className="mb-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>
            )}
            <FormField id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
            <FormField id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
            <button type="submit" disabled={submitting}
              className="w-full rounded-md bg-ward-700 py-2.5 font-semibold text-white hover:bg-ward-800 disabled:opacity-60">
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
            <p className="mt-6 text-sm text-muted">
              New patient? <Link to="/register" className="font-semibold text-ward-700 underline-offset-2 hover:underline">Create an account</Link>
            </p>
          </form>
    </AuthShell>
  )
}