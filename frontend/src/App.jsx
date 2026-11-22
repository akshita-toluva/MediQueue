import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './context/AuthContext'
import { ROLE_HOME } from './auth/roles'
import ProtectedRoute from './components/ProtectedRoute'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PatientHome from './pages/PatientHome'
import NotFoundPage from './pages/NotFoundPage'
import BookAppointmentPage from './pages/BookAppointmentPage'
import DoctorHome from './pages/DoctorHome'
import HealthProfilePage from './pages/HealthProfilePage'
import AdminHome from './pages/AdminHome'
import AdminDoctorsPage from './pages/AdminDoctorsPage'
import DischargeSummaryPage from './pages/DischargeSummaryPage'

// "/" sends each user to the right place
function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? ROLE_HOME[user.role] : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/" element={<HomeRedirect />} />

      {/* Each role's pages: guard (role check) -> layout (navbar) -> page */}
      <Route element={<ProtectedRoute roles={['PATIENT']} />}>
        <Route element={<AppLayout />}>
          <Route path="/patient" element={<PatientHome />} />
          <Route path="/patient/book" element={<BookAppointmentPage />} />
          <Route path="/patient/profile" element={<HealthProfilePage />} />
          <Route path="/patient/visits/:appointmentId/summary" element={<DischargeSummaryPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['DOCTOR']} />}>
        <Route element={<AppLayout />}>
           <Route path="/doctor" element={<DoctorHome />} />        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route element={<AppLayout />}>
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/doctors" element={<AdminDoctorsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}