import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import api, { getErrorMessage } from '../api/client'
import { useLiveQueue } from '../api/useLiveQueue'


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
  const [live, setLive] = useState({})   // appointmentId -> latest pushed update

  const load = useCallback(() => {
      api.get('/api/appointments/my')
        .then((res) => setAppointments(res.data))   // keep pushed updates: they're newer than this list
        .catch((err) => setError(getErrorMessage(err)))
  }, [])

  useEffect(() => { load() }, [load])

  const handleUpdate = useCallback((msg) => {
      // Seen or missed: the visit leaves the queue -> reload so it moves to "Past visits"
      if (!ACTIVE.includes(msg.status)) { load(); return }
      // 'at' lets a card tell whether this push is newer than its last manual Refresh
      setLive((prev) => ({ ...prev, [msg.appointmentId]: { ...msg, at: Date.now() } }))
    }, [load])

    const livePosition = (a) => live[a.appointmentId]?.queuePosition ?? a.queuePosition
    const active = (appointments ?? [])
      .filter((a) => ACTIVE.includes(a.status))
      .sort((a, b) => livePosition(a) - livePosition(b))
    const past = (appointments ?? []).filter((a) => !ACTIVE.includes(a.status))

    const connected = useLiveQueue(active.map((a) => a.appointmentId), handleUpdate)

    return (
      <section>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-2xl font-bold tracking-tight">Your visits</h2>
          <div className="flex flex-wrap gap-3">
           <Link to="/patient/profile"
             className="rounded-md border border-ward-700 px-4 py-2.5 font-semibold text-ward-700 hover:bg-ward-50">
             Health profile
           </Link>
           <Link to="/patient/book"
             className="rounded-md bg-ward-700 px-4 py-2.5 font-semibold text-white hover:bg-ward-800">
             Book a visit
           </Link>
          </div>
        </div>

        {active.length > 0 && (
          <p className="mt-3 text-sm text-muted" role="status">
            {connected
              ? <><span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-green-600" />Live: your place updates automatically</>
              : 'Live updates paused. Use Refresh to check your place.'}
          </p>
        )}

        {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
        {!error && appointments === null && <p className="mt-5 text-muted">Loading your visits…</p>}
        {appointments && appointments.length === 0 && <p className="mt-5 text-muted">You have no visits yet.</p>}

        {active.length > 0 && (
          <div className="mt-6 grid gap-4 md:grid-cols-2">
            {active.map((a) => <ActiveVisit key={a.appointmentId} appointment={a} update={live[a.appointmentId]} />)}
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

  function ActiveVisit({ appointment: a, update })
  {
    const [status, setStatus] = useState(null)
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    async function refresh() {
      setLoading(true); setError('')
      try {
        const { data } = await api.get(`/api/appointments/${a.appointmentId}/queue-status`)
        setStatus({ ...data, at: Date.now() })
      } catch (err) {
        setError(getErrorMessage(err))
      } finally {
        setLoading(false)
      }
    }

    useEffect(() => { refresh() }, [a.appointmentId])   // eslint-disable-line react-hooks/exhaustive-deps

    // Two sources: the live push and the manual Refresh. Show whichever arrived last.
    const latest = update && (!status || update.at > status.at) ? update : status
    const position = latest?.queuePosition ?? a.queuePosition
    const emergency = (latest?.priority ?? a.priority) === 'EMERGENCY'

    const visitStatus = latest?.status ?? a.status
    const withDoctor = visitStatus === 'IN_PROGRESS'

    return (
      <article className="overflow-hidden rounded-xl bg-ward-950 text-white">
        <div className="flex items-start justify-between px-6 py-5">
          <div>
             {withDoctor ? (
               <>
                <p className="text-ward-100">Your visit</p>
                <p className="font-token text-4xl font-bold text-board" aria-live="polite">With the doctor</p>
                <p className="mt-1 text-ward-100">Please go in now.</p>
               </>
               ) : (
               <>
               <p className="text-ward-100">Your place in the queue</p>
               <p className="font-token text-6xl font-bold text-board" aria-live="polite">{position}</p>
               {latest && (
               <p className="mt-1 text-ward-100">
                {position === 1 ? "You're next" : `About ${latest.estimatedWaitTime} min wait`}
               </p>
               )}
               </>
             )}
          </div>
          {emergency && <span className="rounded bg-alert px-2 py-1 text-xs font-bold">URGENT</span>}
        </div>
        <dl className="grid grid-cols-2 gap-y-2 border-t border-dashed border-ward-700 px-6 py-4 text-sm">
          <dt className="text-ward-100">Doctor</dt><dd>{a.doctorName}</dd>
          <dt className="text-ward-100">Department</dt><dd>{a.department}</dd>
          <dt className="text-ward-100">Booked</dt><dd>{fmt(a.bookedAt)}</dd>
          <dt className="text-ward-100">Status</dt><dd>{STATUS_LABEL[visitStatus]}</dd>
        </dl>
        <div className="flex items-center justify-between px-6 pb-5">
          {error ? <p className="text-sm text-red-300">{error}</p> : <span className="text-xs text-ward-100">Estimate, not a guarantee</span>}
          <button onClick={refresh} disabled={loading}
            className="rounded-md border border-ward-100 px-3 py-1.5 text-sm hover:bg-ward-800 disabled:opacity-60">
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </article>
    )
  }