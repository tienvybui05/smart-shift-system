import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

const PAYROLL_VIEWER_ROLES = new Set(['ROLE_EMPLOYEE', 'ROLE_MANAGER'])

export default function EmployeeOrManagerRoute() {
  const { user } = useAuth()

  if (!PAYROLL_VIEWER_ROLES.has(user?.role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
