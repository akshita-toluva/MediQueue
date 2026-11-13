import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import api, { getErrorMessage } from '../api/client'

// Codes must match the backend enums exactly; labels are what the patient reads
const CONDITIONS = {
  DIABETES: 'Diabetes', HYPERTENSION: 'High blood pressure', CHRONIC_KIDNEY_DISEASE: 'Chronic kidney disease',
  HEART_DISEASE: 'Heart disease', HIGH_CHOLESTEROL: 'High cholesterol', CELIAC_DISEASE: 'Celiac disease',
  PREGNANCY: 'Pregnancy',
}
const ALLERGIES = {
  PEANUT: 'Peanut', TREE_NUT: 'Tree nuts', MILK: 'Milk', EGG: 'Egg', WHEAT: 'Wheat',
  SOY: 'Soy', FISH: 'Fish', SHELLFISH: 'Shellfish', SESAME: 'Sesame',
}
const fmt = (iso) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))

export default function HealthProfilePage() {
  const [conditions, setConditions] = useState(null)   // null = loading
  const [allergies, setAllergies] = useState([])
  const [updatedAt, setUpdatedAt] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    api.get('/api/patients/me/health-profile')
      .then(({ data }) => {
        setConditions(data.conditions)
        setAllergies(data.foodAllergies)
        setUpdatedAt(data.updatedAt)
      })
      .catch((err) => setError(getErrorMessage(err)))
  }, [])

  // Any change hides the old "Saved" note, so it never claims unsaved ticks are stored
  const toggle = (list, setList) => (code) => {
    setSaved(false)
    setList(list.includes(code) ? list.filter((c) => c !== code) : [...list, code])
  }

  async function handleSave(e) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      // PUT sends the full picture: unticked = not present
      const { data } = await api.put('/api/patients/me/health-profile', { conditions, foodAllergies: allergies })
      setConditions(data.conditions)
      setAllergies(data.foodAllergies)
      setUpdatedAt(data.updatedAt)
      setSaved(true)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="max-w-2xl">
      <Link to="/patient" className="text-sm text-ward-700 hover:underline">← Your visits</Link>
      <h2 className="mt-3 text-2xl font-bold tracking-tight">Health profile</h2>
      <p className="mt-2 text-muted">
        Self-reported. Your doctor sees this only for visits booked with them. Leave everything unticked if none apply.
      </p>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}
      {conditions === null && !error && <p className="mt-6 text-muted">Loading your profile…</p>}

      {conditions !== null && (
        <form onSubmit={handleSave}>
          <CheckboxGroup legend="Conditions" options={CONDITIONS} selected={conditions} onToggle={toggle(conditions, setConditions)} />
          <CheckboxGroup legend="Food allergies" options={ALLERGIES} selected={allergies} onToggle={toggle(allergies, setAllergies)} />

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <button type="submit" disabled={saving}
              className="rounded-md bg-ward-700 px-4 py-2.5 font-semibold text-white hover:bg-ward-800 disabled:opacity-60">
              {saving ? 'Saving…' : 'Save profile'}
            </button>
            <span role="status" className="text-sm text-muted">
              {saved ? 'Saved. ' : ''}
              {updatedAt ? `Last updated ${fmt(updatedAt)}` : 'Not filled in yet'}
            </span>
          </div>
        </form>
      )}
    </section>
  )
}

// fieldset + legend: screen readers announce the group name with each checkbox
function CheckboxGroup({ legend, options, selected, onToggle }) {
  return (
    <fieldset className="mt-8">
      <legend className="font-semibold">{legend}</legend>
      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        {Object.entries(options).map(([code, label]) => (
          <label key={code} className="flex cursor-pointer items-center gap-2 rounded-md border border-line bg-white px-3 py-2">
            <input type="checkbox" checked={selected.includes(code)} onChange={() => onToggle(code)}
              className="h-4 w-4 accent-ward-700" />
            {label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}