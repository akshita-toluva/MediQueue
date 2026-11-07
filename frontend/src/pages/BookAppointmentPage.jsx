import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import api, { getErrorMessage } from '../api/client'

export default function BookAppointmentPage() {
  const [doctors, setDoctors] = useState(null)     // null = loading
  const [department, setDepartment] = useState('')
  const [doctorId, setDoctorId] = useState('')
  const [symptoms, setSymptoms] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [booked, setBooked] = useState(null)       // AppointmentResponse after success

  // One call loads every AVAILABLE doctor; departments and filtering are derived locally.
  useEffect(() => {
    api.get('/api/doctors/available')
      .then((res) => setDoctors(res.data))
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  // useMemo: recompute only when the doctor list changes, not on every keystroke
  const departments = useMemo(
    () => [...new Set((doctors ?? []).map((d) => d.department))].sort(),
    [doctors],
  )
  const doctorsInDept = (doctors ?? []).filter((d) => d.department === department)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      // Backend derives department from the doctor and priority from the symptoms (rule-based triage)
      const { data } = await api.post('/api/appointments/book', {
        doctorId: Number(doctorId),
        symptomDescription: symptoms.trim(),
      })
      setBooked(data)
    } catch (err) {
      setError(getErrorMessage(err))   // e.g. doctor became unavailable meanwhile
    } finally {
      setSubmitting(false)
    }
  }

  if (booked) return <TokenSlip appointment={booked} />

  return (
    <section className="max-w-xl">
      <h2 className="text-2xl font-bold tracking-tight">Book a visit</h2>
      <p className="mt-2 text-muted">Choose a department and doctor, then tell us why you're coming in.</p>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
      {doctors === null && !error && <p className="mt-6 text-muted">Loading doctors…</p>}
      {doctors && doctors.length === 0 && (
        <p className="mt-6 text-muted">No doctors are available right now. Please try again later.</p>
      )}

      {doctors && doctors.length > 0 && (
        <form onSubmit={handleSubmit} className="mt-8">
          <label htmlFor="department" className="block text-sm font-semibold mb-1.5">Department</label>
          <select id="department" required value={department}
            // Changing department resets the doctor: the old one isn't in the new list
            onChange={(e) => { setDepartment(e.target.value); setDoctorId('') }}
            className="w-full rounded-md border border-line bg-white px-3 py-2.5 mb-5">
            <option value="" disabled>Select a department</option>
            {departments.map((d) => <option key={d} value={d}>{d}</option>)}
          </select>

          <label htmlFor="doctor" className="block text-sm font-semibold mb-1.5">Doctor</label>
          <select id="doctor" required value={doctorId} disabled={!department}
            onChange={(e) => setDoctorId(e.target.value)}
            className="w-full rounded-md border border-line bg-white px-3 py-2.5 mb-5 disabled:bg-paper disabled:text-muted">
            <option value="" disabled>{department ? 'Select a doctor' : 'Choose a department first'}</option>
            {doctorsInDept.map((d) => (
              <option key={d.id} value={d.id}>{d.name} (about {d.avgConsultationTime} min per patient)</option>
            ))}
          </select>

          <label htmlFor="symptoms" className="block text-sm font-semibold mb-1.5">What brings you in?</label>
          <textarea id="symptoms" required rows={4} maxLength={500} value={symptoms}
            onChange={(e) => setSymptoms(e.target.value)}
            className="w-full rounded-md border border-line bg-white px-3 py-2.5" />
          <p className="mt-1.5 mb-6 text-sm text-muted">
            Describe your main symptom. Urgent symptoms such as chest pain or difficulty breathing are seen first.
          </p>

          <button type="submit" disabled={submitting}
            className="w-full rounded-md bg-ward-700 py-2.5 font-semibold text-white hover:bg-ward-800 disabled:opacity-60">
            {submitting ? 'Booking…' : 'Book visit'}
          </button>
        </form>
      )}
    </section>
  )
}

// Confirmation styled like the paper slip a patient gets at the OPD counter.
function TokenSlip({ appointment: a }) {
  const emergency = a.priority === 'EMERGENCY'
  return (
    <section className="max-w-md">
      <h2 className="text-2xl font-bold tracking-tight">Visit booked</h2>
      <div className="mt-6 overflow-hidden rounded-xl bg-ward-950 text-white">
        <div className="px-6 py-5">
          <p className="text-ward-100">Your place in the queue</p>
          <p className="font-token font-bold text-board text-6xl mt-1">{a.queuePosition}</p>
        </div>
        <dl className="grid grid-cols-2 gap-y-3 border-t border-dashed border-ward-700 px-6 py-5 text-sm">
          <dt className="text-ward-100">Doctor</dt><dd>{a.doctorName}</dd>
          <dt className="text-ward-100">Department</dt><dd>{a.department}</dd>
          <dt className="text-ward-100">Booking number</dt><dd className="font-token">{a.appointmentId}</dd>
        </dl>
      </div>

      {emergency && (
        <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">
          Your symptoms were marked as urgent, so you'll be seen first. If this is life-threatening, go to the emergency department now.
        </p>
      )}

      <Link to="/patient" className="mt-6 inline-block font-semibold text-ward-700 hover:underline">Back to your visits</Link>
    </section>
  )
}