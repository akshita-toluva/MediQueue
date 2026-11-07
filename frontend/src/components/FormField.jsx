// Label + input pair. `htmlFor`/`id` link them, so clicking the label focuses the input
// and screen readers announce the label (accessibility).
export default function FormField({ id, label, type = 'text', value, onChange, autoComplete }) {
  return (
    <div className="mb-4">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700 mb-1">{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required
        className="w-full rounded-md border border-line bg-white px-3 py-2.5 text-base
                                      focus:outline-none focus:border-ward-700 focus:ring-2 focus:ring-ward-700/20"
      />
    </div>
  )
}