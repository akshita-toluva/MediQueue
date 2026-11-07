import { Outlet, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext'

const ROLE_LABEL = { PATIENT: 'Patient', DOCTOR: 'Doctor', ADMIN: 'Admin' }

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()                         // revokes on the server, then clears locally
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen">
          <header className="bg-ward-800 text-white">
            <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
              <div className="flex items-center gap-2">
                <span className="font-token font-bold text-sm bg-board text-ward-950 rounded px-1.5 py-0.5">MQ</span>
                <span className="text-lg font-semibold">MediQueue</span>
              </div>
              <div className="flex items-center gap-4 text-sm">
                <span>
                  {user.name} <span className="text-ward-100">({ROLE_LABEL[user.role]})</span>
                </span>
                <button onClick={handleLogout}
                  className="rounded-md border border-ward-100/40 px-3 py-1.5 hover:bg-ward-700">
                  Log out
                </button>
              </div>
            </div>
          </header>
          <main className="mx-auto max-w-5xl px-4 py-10">
            <Outlet />
          </main>
    </div>
  )
}