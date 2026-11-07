export default function PlaceholderPage({ title, note }) {
  return (
    <section>
       <h2 className="text-2xl font-bold tracking-tight">{title}</h2>
       <p className="mt-3 text-muted">{note}</p>
    </section>
  )
}