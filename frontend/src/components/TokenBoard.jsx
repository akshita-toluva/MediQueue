// The brand panel: a hospital waiting-hall token display.
// Decorative sample data, so it's hidden from screen readers (aria-hidden).
export default function TokenBoard() {
  return (
    <aside className="bg-ward-800 text-white px-6 py-6 sm:px-12 lg:py-12 flex flex-col">
      <div className="flex items-center gap-2">
        <span className="font-token font-bold text-sm bg-board text-ward-950 rounded px-1.5 py-0.5">MQ</span>
        <span className="text-lg font-semibold">MediQueue</span>
      </div>

      <div className="hidden lg:block my-auto" aria-hidden="true">
        <p className="text-ward-100">Now calling</p>
        <div className="mt-3 inline-block overflow-hidden rounded-xl bg-ward-950 px-8 py-6">
          <span className="token-in block font-token font-bold text-board text-7xl xl:text-8xl tracking-wider">
            C-014
          </span>
        </div>
        <p className="mt-3 text-lg">Cardiology, room 3</p>

        <p className="mt-10 text-sm text-ward-100">Up next</p>
        <ul className="mt-2 flex gap-2 font-token font-bold">
          <li className="rounded bg-alert px-2 py-1">E-003</li>
          <li className="rounded bg-ward-700 px-2 py-1">C-015</li>
          <li className="rounded bg-ward-700 px-2 py-1">C-016</li>
        </ul>
        <p className="mt-2 text-sm text-ward-100">Emergency cases are called first.</p>
      </div>

      <p className="mt-4 lg:mt-0 max-w-sm text-ward-100">
        Book an OPD visit and follow your place in the queue as it moves.
      </p>
    </aside>
  )
}