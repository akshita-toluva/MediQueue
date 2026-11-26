import { useEffect, useState } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import api, { getErrorMessage } from '../api/client'
import { Link } from 'react-router'

// Colour-blind-validated pair on a white surface (blue / orange). Text never uses these colours.
const SERIES_1 = '#2a78d6'
const SERIES_2 = '#eb6834'
const GRID = '#CFDDD9'      // --color-line: recessive grid
const AXIS = '#557069'      // --color-muted: axis text
const RANGES = [7, 30, 90]
const tooltipStyle = { contentStyle: { borderRadius: 8, borderColor: GRID }, itemStyle: { color: '#17302C' }, labelStyle: { color: '#17302C', fontWeight: 600 }, cursor: { fill: '#F6F8F7' } }

export default function AdminHome() {
  const [days, setDays] = useState(30)
  const [load, setLoad] = useState(null)      // null = loading
  const [peak, setPeak] = useState(null)
  const [noShow, setNoShow] = useState(null)
  const [accuracy, setAccuracy] = useState(null)
  const [error, setError] = useState('')

  // Today's load: live numbers, independent of the range filter
  useEffect(() => {
    let active = true
    api.get('/api/admin/department-load')
      .then(({ data }) => { if (active) setLoad(data) })
      .catch((err) => { if (active) setError(getErrorMessage(err)) })
    return () => { active = false }
  }, [])

  // History charts follow the range filter
  useEffect(() => {
    let active = true
    Promise.all([
      api.get('/api/admin/peak-hours', { params: { days } }),
      api.get('/api/admin/no-show-rates', { params: { days } }),
      api.get('/api/admin/wait-accuracy', { params: { days } }),
    ])
      .then(([p, n,w]) => {
        if (!active) return
        setPeak(p.data)
        setNoShow(n.data.map((r) => ({ ...r, ratePct: Math.round(r.rate * 1000) / 10 })))
        setAccuracy(w.data)
      })
      .catch((err) => { if (active) setError(getErrorMessage(err)) })
    return () => { active = false }
  }, [days])

  return (
    <section>
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold tracking-tight">Hospital dashboard</h2>
        <Link to="/admin/doctors" className="text-sm text-ward-700 underline">Manage doctors →</Link>
      </div>

      {error && <p role="alert" className="mt-5 border-l-4 border-alert bg-red-50 px-3 py-2 text-sm text-alert">{error}</p>}

      <Card title="Department load · today" subtitle="Patients waiting or in consultation vs doctors accepting bookings">
        {load === null ? <Loading /> : load.length === 0 ? <Empty text="No departments yet." /> : (
          <>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={load} barGap={2}>
                <CartesianGrid vertical={false} stroke={GRID} />
                <XAxis dataKey="department" tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={{ stroke: GRID }} />
                <YAxis allowDecimals={false} tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} width={32} />
                <Tooltip {...tooltipStyle} />
                <Legend wrapperStyle={{ fontSize: 13 }} formatter={(value) => <span style={{ color: '#17302C' }}>{value}</span>} />
                <Bar dataKey="activeAppointments" name="Patients" fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={36} />
                <Bar dataKey="availableDoctors" name="Available doctors" fill={SERIES_2} radius={[4, 4, 0, 0]} maxBarSize={36} />
              </BarChart>
            </ResponsiveContainer>
            <LoadTable rows={load} />
          </>
        )}
      </Card>

      {/* One filter row above the history charts */}
      <div className="mt-10 flex flex-wrap items-center gap-2" role="group" aria-label="Time range">
        <span className="mr-1 text-sm text-muted">History:</span>
        {RANGES.map((d) => (
          <button key={d} onClick={() => setDays(d)} aria-pressed={days === d}
            className={`rounded-md border px-3 py-1.5 text-sm ${days === d
              ? 'border-ward-700 bg-ward-700 font-semibold text-white'
              : 'border-line bg-white hover:bg-paper'}`}>
            Last {d} days
          </button>
        ))}
      </div>

      <div className="mt-4 grid gap-6 lg:grid-cols-2">
        <Card title="Peak booking hours" subtitle={`Bookings per hour of day, last ${days} days`}>
          {peak === null ? <Loading /> : peak.every((h) => h.count === 0) ? <Empty text="No bookings in this period." /> : (
            <>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={peak} barCategoryGap={2}>
                  <CartesianGrid vertical={false} stroke={GRID} />
                  <XAxis dataKey="hour" tickFormatter={(h) => String(h).padStart(2, '0')} interval={2}
                    tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={{ stroke: GRID }} />
                  <YAxis allowDecimals={false} tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} width={32} />
                  <Tooltip {...tooltipStyle} labelFormatter={(h) => `${String(h).padStart(2, '0')}:00–${String(h).padStart(2, '0')}:59`}
                    formatter={(v) => [v, 'Bookings']} />
                  <Bar dataKey="count" fill={SERIES_1} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
              <DataTable headers={['Hour', 'Bookings']}
                rows={peak.filter((h) => h.count > 0).map((h) => [`${String(h.hour).padStart(2, '0')}:00`, h.count])} />
            </>
          )}
        </Card>

        <Card title="No-show rate by department" subtitle={`Share of finished visits where the patient didn't come, last ${days} days`}>
          {noShow === null ? <Loading /> : noShow.length === 0 ? <Empty text="No finished visits in this period." /> : (
            <>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={noShow}>
                  <CartesianGrid vertical={false} stroke={GRID} />
                  <XAxis dataKey="department" tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={{ stroke: GRID }} />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} width={44} />
                  <Tooltip {...tooltipStyle}
                    formatter={(v, _name, item) => [`${v}% (${item.payload.noShows} of ${item.payload.resolved} visits)`, 'No-show rate']} />
                  <Bar dataKey="ratePct" fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={48} />
                </BarChart>
              </ResponsiveContainer>
              <DataTable headers={['Department', 'No-shows', 'Finished visits', 'Rate']}
                rows={noShow.map((r) => [r.department, r.noShows, r.resolved, `${r.ratePct}%`])} />
            </>
          )}
        </Card>
      </div>
      <Card title="Wait-time accuracy"
        subtitle={`How close the wait we told patients at booking was to their real wait, last ${days} days`}>
        {accuracy === null ? <Loading /> : accuracy.overall.visits === 0 ? (
          <Empty text="No completed visits with a booking-time estimate yet. Counting starts with visits booked after the Day 28 update." />
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat label={`Within ±${accuracy.toleranceMinutes} min`} value={`${accuracy.overall.withinTolerancePct}%`}
                note={`${accuracy.overall.visits} completed visits`} />
              <Stat label="Average miss" value={`${accuracy.overall.meanAbsErrorMin} min`} note="Size of the error, either direction" />
              <Stat label="Bias" value={biasText(accuracy.overall.biasMin)} note="Positive = waited longer than told" />
            </div>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={accuracy.distribution} margin={{ top: 16 }}>
                <CartesianGrid vertical={false} stroke={GRID} />
                <XAxis dataKey="label" tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={{ stroke: GRID }} />
                <YAxis allowDecimals={false} tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} width={32} />
                <Tooltip {...tooltipStyle} formatter={(v) => [v, 'Visits']} />
                <Bar dataKey="count" fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={56} />
              </BarChart>
            </ResponsiveContainer>
            <DataTable headers={['Department', 'Visits', `Within ±${accuracy.toleranceMinutes} min`, 'Average miss', 'Bias']}
              rows={accuracy.byDepartment.map((d) => [d.department ?? d.label, d.visits, `${d.withinTolerancePct}%`,
                `${d.meanAbsErrorMin} min`, biasText(d.biasMin)])} />
          </>
        )}
      </Card>
    </section>
  )
}

function Card({ title, subtitle, children }) {
  return (
    <section className="mt-6 rounded-xl border border-line bg-white p-5">
      <h3 className="font-semibold">{title}</h3>
      {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

const Loading = () => <p className="text-sm text-muted">Loading…</p>
const Empty = ({ text }) => <p className="text-sm text-muted">{text}</p>

// Big number + label; text only, no colour meaning
function Stat({ label, value, note }) {
  return (
    <div className="rounded-lg border border-line bg-paper px-4 py-3">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold">{value}</p>
      <p className="mt-1 text-xs text-muted">{note}</p>
    </div>
  )
}

const biasText = (b) => (b === 0 ? 'On target' : b > 0 ? `${b} min longer` : `${Math.abs(b)} min shorter`)


// Table view: the same numbers in text (screen readers, printing, exact values)
function DataTable({ headers, rows }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-ward-700">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <thead><tr className="border-b border-line text-muted">{headers.map((h) => <th key={h} className="py-1 pr-4 font-medium">{h}</th>)}</tr></thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-line/60">{r.map((c, j) => <td key={j} className="py-1 pr-4">{c}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}

// Always visible: the overload signal must not depend on reading a chart
function LoadTable({ rows }) {
  return (
    <table className="mt-4 w-full text-left text-sm">
      <thead>
        <tr className="border-b border-line text-muted">
          <th className="py-1.5 pr-4 font-medium">Department</th>
          <th className="py-1.5 pr-4 font-medium">Patients</th>
          <th className="py-1.5 pr-4 font-medium">Doctors available</th>
          <th className="py-1.5 font-medium">Patients per doctor</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => {
          const overloaded = r.patientsPerAvailableDoctor === null && r.activeAppointments > 0
          return (
            <tr key={r.department} className="border-b border-line/60">
              <td className="py-1.5 pr-4">{r.department}</td>
              <td className="py-1.5 pr-4">{r.activeAppointments}</td>
              <td className="py-1.5 pr-4">{r.availableDoctors} of {r.totalDoctors}</td>
              <td className="py-1.5">
                {overloaded
                  ? <span className="font-semibold text-alert">⚠ No doctor available</span>
                  : r.patientsPerAvailableDoctor === null ? '—' : r.patientsPerAvailableDoctor.toFixed(1)}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}