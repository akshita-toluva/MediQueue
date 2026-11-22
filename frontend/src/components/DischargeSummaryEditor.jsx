import { useEffect, useState } from 'react'
import api, { getErrorMessage } from '../api/client'

const FIELD = 'mt-1 w-full rounded-md border border-white/30 bg-white/95 px-2 py-1.5 text-ink focus:outline-none focus:ring-2 focus:ring-board'

// Doctor side of the discharge summary for the patient IN THE ROOM.
// The AI text is a DRAFT until approved: the patient sees nothing until then (enforced by the backend).
export default function DischargeSummaryEditor({ appointmentId }) {
  const [summary, setSummary] = useState(undefined)   // undefined = loading, null = not written yet
  const [notes, setNotes] = useState('')
  const [followUp, setFollowUp] = useState('')
  const [busy, setBusy] = useState('')                // '' | 'save' | 'generate' | 'approve'
  const [error, setError] = useState('')
  const [justSaved, setJustSaved] = useState(false)

  const base = `/api/doctors/me/appointments/${appointmentId}/discharge-summary`

  useEffect(() => {
    let active = true
    api.get(`/api/doctors/me/appointments/${appointmentId}/discharge-summary`)
      .then(({ data }) => {
        if (!active) return
        setSummary(data)
        setNotes(data.diagnosisNotes)
        setFollowUp(data.followUpInstructions ?? '')
      })
      .catch((err) => {
        if (!active) return
        if (err.response?.status === 404) setSummary(null)   // none yet: show an empty form
        else setError(getErrorMessage(err))
      })
    return () => { active = false }
  }, [appointmentId])

  // Every button goes through here: one action at a time, the server's reply replaces local state
  async function run(kind, request) {
    setBusy(kind)
    setError('')
    setJustSaved(false)
    try {
      const { data } = await request()
      setSummary(data)
      if (kind === 'save') setJustSaved(true)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusy('')
    }
  }

  const save = (e) => {
    e.preventDefault()
    run('save', () => api.put(base, { diagnosisNotes: notes, followUpInstructions: followUp }))
  }
  const generate = () => run('generate', () => api.post(`${base}/diet-suggestion`))
  // Send the id of the text ON SCREEN: if it changed meanwhile, the server answers 409
  const approve = () => run('approve', () => api.put(`${base}/diet-suggestion/approval`, { generationId: summary.dietSuggestionId }))

  if (summary === undefined) {
    return error
      ? <p className="mt-3 text-sm text-red-200">{error}</p>
      : <p className="mt-3 text-sm text-ward-100">Loading discharge summary…</p>
  }

  // Unsaved edits? Then generating would use a summary the doctor hasn't saved yet
  const dirty = summary
    ? notes.trim() !== summary.diagnosisNotes || (followUp.trim() || null) !== summary.followUpInstructions
    : notes.trim() !== ''

  return (
    <div className="mt-3 w-full max-w-xl rounded-md bg-white/10 px-3 py-3 text-sm">
      <p className="text-xs uppercase tracking-wide text-ward-100">
        Discharge summary · {summary ? 'saved' : 'not written yet'}
      </p>

      <form onSubmit={save} className="mt-2 space-y-2">
        <label className="block">
          <span className="text-xs text-ward-100">Diagnosis notes</span>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} required maxLength={4000} rows={3} className={FIELD} />
        </label>
        <label className="block">
          <span className="text-xs text-ward-100">Follow-up instructions (optional)</span>
          <textarea value={followUp} onChange={(e) => setFollowUp(e.target.value)} maxLength={2000} rows={2} className={FIELD} />
        </label>
        <div className="flex items-center gap-3">
          <button type="submit" disabled={busy !== '' || !dirty || !notes.trim()}
            className="rounded-md bg-board px-3 py-1.5 font-semibold text-ward-950 disabled:opacity-50">
            {busy === 'save' ? 'Saving…' : 'Save summary'}
          </button>
          {justSaved && !dirty && <span className="text-board">Saved</span>}
          {dirty && summary && <span className="text-ward-100">Unsaved changes</span>}
        </div>
      </form>

      {summary && (
        <div className="mt-4 border-t border-white/20 pt-3">
          <p className="text-xs uppercase tracking-wide text-ward-100">AI diet suggestion</p>
          {summary.dietSuggestion ? (
            <>
              <p className="mt-1 text-xs">
                {summary.dietApproved
                  ? <span className="text-board">✓ Approved · visible to the patient</span>
                  : <span className="font-semibold text-amber-200">Draft · NOT visible to the patient until you approve</span>}
              </p>
              <p className="mt-2 whitespace-pre-line rounded bg-black/20 px-3 py-2">{summary.dietSuggestion}</p>
              <p className="mt-1 text-xs text-ward-100">{summary.dietDisclaimer}</p>
            </>
          ) : (
            <p className="mt-1 text-ward-100">None yet. Uses only the verified health profile: no name, no notes.</p>
          )}
          <div className="mt-2 flex flex-wrap gap-2">
            <button onClick={generate} disabled={busy !== '' || dirty}
              className="rounded-md border border-white/40 px-3 py-1 text-xs hover:bg-white/10 disabled:opacity-50">
              {busy === 'generate' ? 'Generating…' : summary.dietSuggestion ? 'Regenerate' : 'Generate diet suggestion'}
            </button>
            {summary.dietSuggestion && !summary.dietApproved && (
              <button onClick={approve} disabled={busy !== ''}
                className="rounded-md bg-board px-3 py-1 text-xs font-semibold text-ward-950 disabled:opacity-50">
                {busy === 'approve' ? 'Approving…' : 'I have read it · Approve'}
              </button>
            )}
          </div>
        </div>
      )}

      {error && <p role="alert" className="mt-2 text-red-200">{error}</p>}
    </div>
  )
}