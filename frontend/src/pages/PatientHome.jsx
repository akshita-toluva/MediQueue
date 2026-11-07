import { useEffect, useState } from 'react'
import api, { getErrorMessage } from '../api/client'

export default function PatientHome() {
  const [appointments, setAppointments] = useState(null)   // null = still loading
  const [error, setError] = useState('')

  useEffect(() => {
    // The request interceptor adds "Authorization: Bearer <token>" automatically
    api.get('/api/appointments/my')
      .then((res) => setAppointments(res.data))
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  return (
    <section>
       <h2 className="text-2xl font-bold tracking-tight">Your visits</h2>
          {error && <p role="alert" className="mt-3 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
          {!error && appointments === null && <p className="mt-3 text-muted">Loading your visits…</p>}
          {appointments && appointments.length === 0 && (
            <p className="mt-3 text-muted">You have no visits booked yet. Booking opens in the next update.</p>
          )}
          {appointments && appointments.length > 0 && (
            <p className="mt-3">You have {appointments.length} visit(s).</p>
       )}
    </section>
  )
}