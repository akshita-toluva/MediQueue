import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import api, { getErrorMessage } from '../api/client'

const ACTIVE = ['PENDING', 'IN_PROGRESS']
const STATUS_LABEL = {
  PENDING: 'Waiting', IN_PROGRESS: 'With the doctor', COMPLETED: 'Seen',
  CANCELLED: 'Cancelled', NO_SHOW: 'Missed',
}
// Backend sends LocalDateTime (no zone) -> browser reads it as local time, which matches the server in IST
const fmt = (iso) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))

export default function PatientHome() {
  const [appointments, setAppointments] = useState(null)   // null = loading
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/api/appointments/my')
      .then((res) => setAppointments(res.data))
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  const active = (appointments ?? []).filter((a) => ACTIVE.includes(a.status)).sort((a, b) => a.queuePosition - b.queuePosition)
  const past = (appointments ?? []).filter((a) => !ACTIVE.includes(a.status))

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-2xl font-bold tracking-tight">Your visits</h2>
        <Link to="/patient/book"
          className="rounded-md bg-ward-700 px-4 py-2.5 font-semibold text-white hover:bg-ward-800">
          Book a visit
        </Link>
      </div>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
      {!error && appointments === null && <p className="mt-5 text-muted">Loading your visits…</p>}
      {appointments && appointments.length === 0 && (
        <p className="mt-5 text-muted">You have no visits yet.</p>
      )}

      {active.length > 0 && (
        <div className="mt-8 grid gap-4 md:grid-cols-2">
          {active.map((a) => <ActiveVisit key={a.appointmentId} appointment={a} />)}
        </div>
      )}

      {past.length > 0 && (
        <>
          <h3 className="mt-10 mb-3 font-semibold">Past visits</h3>
          <ul className="divide-y divide-line rounded-xl border border-line bg-white">
            {past.map((a) => (
              <li key={a.appointmentId} className="flex flex-wrap justify-between gap-2 px-4 py-3 text-sm">
                <span><span className="font-semibold">{a.doctorName}</span> · {a.department}</span>
                <span className="text-muted">{fmt(a.bookedAt)} · {STATUS_LABEL[a.status] ?? a.status}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

// One card per visit still in the queue. Fetches its own live status:
// position can change after booking (emergencies jump ahead), so the list's copy may be old.
function ActiveVisit({ appointment: a }) {
  const [status, setStatus] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function refresh() {
    setLoading(true); setError('')
    try {
      const { data } = await api.get(`/api/appointments/${a.appointmentId}/queue-status`)
      setStatus(data)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [a.appointmentId])   // eslint-disable-line react-hooks/exhaustive-deps

  const position = status?.queuePosition ?? a.queuePosition
  const emergency = a.priority === 'EMERGENCY'

  return (
    <article className="overflow-hidden rounded-xl bg-ward-950 text-white">
      <div className="flex items-start justify-between px-6 py-5">
        <div>
          <p className="text-ward-100">Your place in the queue</p>
          <p className="font-token text-6xl font-bold text-board" aria-live="polite">{position}</p>
          {status && (
            <p className="mt-1 text-ward-100">
              {status.estimatedWaitTime > 0 ? `About ${status.estimatedWaitTime} min wait` : "You're next"}
            </p>
          )}
        </div>
        {emergency && <span className="rounded bg-alert px-2 py-1 text-xs font-bold">URGENT</span>}
      </div>
      <dl className="grid grid-cols-2 gap-y-2 border-t border-dashed border-ward-700 px-6 py-4 text-sm">
        <dt className="text-ward-100">Doctor</dt><dd>{a.doctorName}</dd>
        <dt className="text-ward-100">Department</dt><dd>{a.department}</dd>
        <dt className="text-ward-100">Booked</dt><dd>{fmt(a.bookedAt)}</dd>
        <dt className="text-ward-100">Status</dt><dd>{STATUS_LABEL[status?.status ?? a.status]}</dd>
      </dl>
      <div className ="flex items-center justify-between px-6 pb-5">
        {error ? <p className="text-sm text-red-300">{error}</p> : <span className="text-xs text-ward-100">Estimate, not a guarantee</span>}
        <button onClick={refresh} disabled={loading}
          className="rounded-md border border-ward-100 px-3 py-1.5 text-sm hover:bg-ward-800 disabled:opacity-60">
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>
    </article>
  )
}