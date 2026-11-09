import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext'
import { getErrorMessage } from '../api/client'
import { ROLE_HOME } from '../auth/roles'
import FormField from '../components/FormField'
import AuthShell from '../components/AuthShell'

export default function RegisterPage() {
  const { register, user } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    // Client-side check = instant feedback only. It is NOT security:
    // the backend validates again, because anyone can call the API directly.
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    setSubmitting(true)
    try {
      const u = await register(name.trim(), email.trim(), password)
      navigate(ROLE_HOME[u.role], { replace: true })   // always /patient for self-registration
    } catch (err) {
      setError(getErrorMessage(err))   // e.g. 409 "Email already registered"
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="For patients. Doctors are added by the hospital admin.">
          <form onSubmit={handleSubmit}>
            {error && (
              <p role="alert" className="mb-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>
            )}
            <FormField id="name" label="Full name" value={name} onChange={setName} autoComplete="name" />
            <FormField id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
            <FormField id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="new-password" minLength={8} maxLength={72}/>
            <FormField id="confirm" label="Confirm password" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" />
            <button type="submit" disabled={submitting}
              className="w-full rounded-md bg-ward-700 py-2.5 font-semibold text-white hover:bg-ward-800 disabled:opacity-60">
              {submitting ? 'Creating account…' : 'Create account'}
            </button>
            <p className="mt-6 text-sm text-muted">
              Already registered? <Link to="/login" className="font-semibold text-ward-700 underline-offset-2 hover:underline">Sign in</Link>
            </p>
          </form>
    </AuthShell>
  )
}