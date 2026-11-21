import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import api, { getErrorMessage } from '../api/client'

const EMPTY_FORM = { name: '', email: '', password: '', role: 'DOCTOR', department: '', avgConsultationTime: 15 }
const INPUT = 'w-full rounded-md border border-line bg-white px-3 py-2 text-base focus:outline-none focus:border-ward-700 focus:ring-2 focus:ring-ward-700/20'

export default function AdminDoctorsPage() {
  const [doctors, setDoctors] = useState(null)   // null = loading
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)      // bump to re-fetch the list
  const [busyId, setBusyId] = useState(null)     // row whose Pause/Resume is in flight

  useEffect(() => {
    let active = true
    api.get('/api/admin/doctors')
      .then(({ data }) => { if (active) setDoctors(data) })
      .catch((err) => { if (active) setError(getErrorMessage(err)) })
    return () => { active = false }
  }, [refresh])

  async function toggle(d) {
    setBusyId(d.doctorId)
    setError('')
    try {
      // Same endpoint doctors use; the backend allows ADMIN on any doctor
      await api.patch(`/api/doctors/${d.doctorId}/availability`, null, { params: { available: !d.available } })
      setRefresh((n) => n + 1)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  // Existing names feed the datalist, so the admin picks "Cardiology" instead of typing a variant
  const departments = doctors ? [...new Set(doctors.map((d) => d.department))] : []

  return (
    <section>
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold tracking-tight">Doctors &amp; staff</h2>
        <Link to="/admin" className="text-sm text-ward-700 underline">← Dashboard</Link>
      </div>
      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}

      <AddStaffForm departments={departments} onCreated={() => setRefresh((n) => n + 1)} />

      <section className="mt-6 rounded-xl border border-line bg-white p-5">
        <h3 className="font-semibold">All doctors</h3>
        {doctors === null ? <p className="mt-3 text-sm text-muted">Loading…</p>
          : doctors.length === 0 ? <p className="mt-3 text-sm text-muted">No doctors yet.</p> : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-line text-muted">
                  <th className="py-2 pr-3 font-medium">Name</th>
                  <th className="pr-3 font-medium">Email</th>
                  <th className="pr-3 font-medium">Department</th>
                  <th className="pr-3 font-medium">Avg min</th>
                  <th className="pr-3 font-medium">Status</th>
                  <th><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody>
                {doctors.map((d) => (
                  <tr key={d.doctorId} className="border-b border-line last:border-0">
                    <td className="py-2 pr-3 font-medium">{d.name}</td>
                    <td className="pr-3">{d.email}</td>
                    <td className="pr-3">{d.department}</td>
                    <td className="pr-3">{d.avgConsultationTime}</td>
                    <td className="pr-3">{d.available ? '● Available' : '○ Paused'}</td>
                    <td className="py-2 text-right">
                      <button onClick={() => toggle(d)} disabled={busyId === d.doctorId}
                        className="rounded-md border border-line px-3 py-1 hover:bg-paper disabled:opacity-50">
                        {d.available ? 'Pause' : 'Resume'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  )
}

function AddStaffForm({ departments, onCreated }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const isDoctor = form.role === 'DOCTOR'

  async function handleSubmit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    const body = {
      name: form.name.trim(), email: form.email.trim(), password: form.password, role: form.role,
      ...(isDoctor && { department: form.department, avgConsultationTime: Number(form.avgConsultationTime) }),
    }
    try {
      const { data } = await api.post('/api/auth/admin/register', body)
      setMessage(`Created ${data.name} (${data.role}). Share the email and temporary password with them privately.`)
      setForm(EMPTY_FORM)
      onCreated()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-6 rounded-xl border border-line bg-white p-5">
      <h3 className="font-semibold">Add doctor or admin</h3>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <label className="text-sm font-medium">Full name
          <input className={INPUT} value={form.name} onChange={set('name')} required maxLength={100} />
        </label>
        <label className="text-sm font-medium">Email (their login)
          <input className={INPUT} type="email" value={form.email} onChange={set('email')} required autoComplete="off" />
        </label>
        <label className="text-sm font-medium">Temporary password
          {/* new-password stops the browser autofilling the ADMIN's own saved password here */}
          <input className={INPUT} type="password" value={form.password} onChange={set('password')}
            required minLength={8} maxLength={72} autoComplete="new-password" />
        </label>
        <label className="text-sm font-medium">Role
          <select className={INPUT} value={form.role} onChange={set('role')}>
            <option value="DOCTOR">Doctor</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
        {isDoctor && (
          <>
            <label className="text-sm font-medium">Department
              <input className={INPUT} list="dept-options" value={form.department} onChange={set('department')} required maxLength={60} />
              <datalist id="dept-options">
                {departments.map((d) => <option key={d} value={d} />)}
              </datalist>
            </label>
            <label className="text-sm font-medium">Avg consultation (minutes)
              <input className={INPUT} type="number" min={1} max={120} value={form.avgConsultationTime}
                onChange={set('avgConsultationTime')} required />
            </label>
          </>
        )}
      </div>
      {error && <p role="alert" className="mt-4 text-sm text-alert">{error}</p>}
      {message && <p role="status" className="mt-4 text-sm text-ward-700">{message}</p>}
      <button type="submit" disabled={saving}
        className="mt-5 rounded-md bg-ward-700 px-4 py-2 text-sm font-semibold text-white hover:bg-ward-800 disabled:opacity-50">
        {saving ? 'Creating…' : 'Create account'}
      </button>
    </form>
  )
}