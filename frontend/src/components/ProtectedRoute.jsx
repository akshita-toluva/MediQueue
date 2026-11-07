import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../context/AuthContext'
import { ROLE_HOME } from '../auth/roles'

// Wraps a group of routes. Renders them (via <Outlet/>) only if the user is allowed.
// REMINDER: this is UX, not security. The backend still checks every API call.
export default function ProtectedRoute({ roles }) {
  const { user } = useAuth()
  const location = useLocation()

  // Not logged in -> login page, remembering where they wanted to go
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  // Logged in but wrong role (e.g. a patient typing /admin) -> their own home
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={ROLE_HOME[user.role]} replace />
  }
  return <Outlet />
}