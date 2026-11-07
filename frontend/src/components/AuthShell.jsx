import TokenBoard from './TokenBoard'

// Split screen on desktop (board | form); stacked on phones.
export default function AuthShell({ title, subtitle, children }) {
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <TokenBoard />
      <main className="flex items-center px-6 py-10 sm:px-12">
        <div className="w-full max-w-sm">
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  )
}