import { useCallback, useEffect, useState } from 'react'
import api, { getErrorMessage } from '../api/client'

const fmtTime = (iso) => new Intl.DateTimeFormat('en-IN', { timeStyle: 'short' }).format(new Date(iso))
const minutesSince = (iso, now) => Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60000))

export default function DoctorHome() {
  const [queue, setQueue] = useState(null)              // null = loading; { doctorId, available, current, waiting }
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)               // one action at a time: all buttons disabled meanwhile
  const [confirmNoShow, setConfirmNoShow] = useState(null) // appointmentId waiting for a second click
  const [now, setNow] = useState(() => Date.now())

  const load = useCallback(async () => {
    try {
      const { data } = await api.get('/api/doctors/me/queue')
      setQueue(data)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }, [])

  useEffect(() => { load() }, [load])

  // Re-render every 30s so "In consultation for N min" stays current without asking the server
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(id)
  }, [])

  // Every button goes through here: lock the UI, call the API, then reload the queue.
  // Reload even on error: the server's state is the truth (e.g. someone else already called this patient).
  async function act(request) {
    setBusy(true)
    setError('')
    try {
      await request()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      await load()
      setBusy(false)
      setConfirmNoShow(null)
    }
  }

  const callNext = () => act(() => api.post('/api/doctors/me/call-next'))
  const resolve = (id, outcome) => act(() => api.patch(`/api/appointments/${id}/resolve`, null, { params: { outcome } }))
  const setPriority = (id, priority) => act(() => api.patch(`/api/appointments/${id}/priority`, null, { params: { priority } }))
  const toggleAvailable = () => act(() =>
    api.patch(`/api/doctors/${queue.doctorId}/availability`, null, { params: { available: !queue.available } }))

  if (queue === null) {
    return error
      ? <p role="alert" className="border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>
      : <p className="text-muted">Loading your queue…</p>
  }

  const { current, waiting, available } = queue

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Today's queue</h2>
          <p className="mt-1 text-sm text-muted">{waiting.length} waiting</p>
        </div>
        <div className="flex items-center gap-3">
          {/* Paused = no NEW bookings; patients already waiting are still seen */}
          <button onClick={toggleAvailable} disabled={busy}
            className={`rounded-md border px-3 py-2 text-sm font-semibold disabled:opacity-60 ${
              available ? 'border-line bg-white text-ink hover:bg-paper' : 'border-alert bg-red-50 text-alert'}`}>
            {available ? 'Pause new bookings' : 'Bookings paused · Resume'}
          </button>
          <button onClick={load} disabled={busy}
            className="rounded-md border border-line bg-white px-3 py-2 text-sm hover:bg-paper disabled:opacity-60">
            Refresh
          </button>
        </div>
      </div>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}

      {/* Now seeing */}
      <div className="mt-6 overflow-hidden rounded-xl bg-ward-950 text-white">
        {current ? (
          <div className="flex flex-wrap items-start justify-between gap-4 px-6 py-5">
            <div className="min-w-0">
              <p className="text-ward-100">Now seeing</p>
              <p className="mt-1 text-3xl font-bold">{current.patientName}</p>
              <p className="mt-2 text-ward-100">{current.symptoms || 'No symptoms recorded'}</p>
              <p className="mt-2 text-sm text-ward-100">
                In consultation for {minutesSince(current.consultationStartedAt, now)} min
                (since {fmtTime(current.consultationStartedAt)})
              </p>
            </div>
            <div className="flex items-center gap-3">
              {current.priority === 'EMERGENCY' && <span className="rounded bg-alert px-2 py-1 text-xs font-bold">URGENT</span>}
              <button onClick={() => resolve(current.appointmentId, 'COMPLETED')} disabled={busy}
                className="rounded-md bg-board px-4 py-2.5 font-semibold text-ward-950 hover:brightness-95 disabled:opacity-60">
                Complete visit
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-4 px-6 py-5">
            <p className="text-ward-100">{waiting.length ? 'No one in consultation.' : 'No one waiting.'}</p>
            {/* The server picks who is next — the button sends no patient id */}
            <button onClick={callNext} disabled={busy || waiting.length === 0}
              className="rounded-md bg-board px-4 py-2.5 font-semibold text-ward-950 hover:brightness-95 disabled:opacity-50">
              Call next patient
            </button>
          </div>
        )}
      </div>

      {/* Waiting list */}
      <h3 className="mt-10 mb-3 font-semibold">Waiting</h3>
      {waiting.length === 0 ? (
        <p className="text-muted">Nobody is waiting.</p>
      ) : (
        <ol className="divide-y divide-line rounded-xl border border-line bg-white">
          {waiting.map((p) => {
            const urgent = p.priority === 'EMERGENCY'
            return (
              <li key={p.appointmentId} className="flex flex-wrap items-start gap-4 px-4 py-4">
                <span className="font-token w-8 text-2xl font-bold text-ward-700">{p.queuePosition}</span>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">
                    {p.patientName}
                    {urgent && <span className="ml-2 rounded bg-alert px-1.5 py-0.5 text-xs font-bold text-white">URGENT</span>}
                  </p>
                  <p className="mt-1 text-sm text-muted">{p.symptoms || 'No symptoms recorded'}</p>
                  <p className="mt-1 text-xs text-muted">Booked {fmtTime(p.bookedAt)}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {/* Reversible and audit-logged on the server (who, from, to, when) */}
                  <button onClick={() => setPriority(p.appointmentId, urgent ? 'NORMAL' : 'EMERGENCY')} disabled={busy}
                    className="rounded-md border border-line px-3 py-1.5 text-sm hover:bg-paper disabled:opacity-60">
                    {urgent ? 'Mark normal' : 'Mark urgent'}
                  </button>
                  {/* Not reversible -> needs a second click */}
                  {confirmNoShow === p.appointmentId ? (
                    <>
                      <button onClick={() => resolve(p.appointmentId, 'NO_SHOW')} disabled={busy}
                        className="rounded-md bg-alert px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60">
                        Confirm no-show
                      </button>
                      <button onClick={() => setConfirmNoShow(null)} className="px-2 py-1.5 text-sm text-muted hover:underline">
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button onClick={() => setConfirmNoShow(p.appointmentId)} disabled={busy}
                      className="rounded-md border border-line px-3 py-1.5 text-sm text-alert hover:bg-red-50 disabled:opacity-60">
                      No-show
                    </button>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}