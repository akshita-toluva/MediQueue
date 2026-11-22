import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import api, { getErrorMessage } from '../api/client'

const fmt = (iso) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))

// Patient's report. The backend decides what's visible: unapproved AI text never arrives here.
export default function DischargeSummaryPage() {
  const { appointmentId } = useParams()
  const [summary, setSummary] = useState(undefined)   // undefined = loading, null = not written yet
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api.get(`/api/patients/me/appointments/${appointmentId}/discharge-summary`)
      .then(({ data }) => { if (active) setSummary(data) })
      .catch((err) => {
        if (!active) return
        if (err.response?.status === 404) setSummary(null)
        else setError(getErrorMessage(err))
      })
    return () => { active = false }
  }, [appointmentId])

  return (
    <section className="max-w-2xl">
      <Link to="/patient" className="text-sm text-ward-700 underline">← Your visits</Link>
      <h2 className="mt-3 text-2xl font-bold tracking-tight">Discharge summary</h2>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
      {!error && summary === undefined && <p className="mt-5 text-muted">Loading…</p>}
      {summary === null && (
        <p className="mt-5 text-muted">Your doctor hasn't written a discharge summary for this visit yet.</p>
      )}

      {summary && (
        <article className="mt-6 rounded-xl border border-line bg-white p-6">
          <p className="text-sm text-muted">
            {summary.doctorName} · {summary.department} · updated {fmt(summary.updatedAt)}
          </p>

          <h3 className="mt-5 font-semibold">Diagnosis</h3>
          <p className="mt-1 whitespace-pre-line">{summary.diagnosisNotes}</p>

          {summary.followUpInstructions && (
            <>
              <h3 className="mt-5 font-semibold">Follow-up</h3>
              <p className="mt-1 whitespace-pre-line">{summary.followUpInstructions}</p>
            </>
          )}

          {summary.dietSuggestion && (
            <section className="mt-6 rounded-lg border-l-4 border-board bg-paper p-4" aria-labelledby="diet-heading">
              <h3 id="diet-heading" className="font-semibold">Diet suggestions</h3>
              {/* Warning BEFORE the advice, so it is read first */}
              <p role="note" className="mt-1 text-sm font-semibold">⚠ {summary.dietDisclaimer}</p>
              <p className="mt-3 whitespace-pre-line">{summary.dietSuggestion}</p>
            </section>
          )}
        </article>
      )}
    </section>
  )
}