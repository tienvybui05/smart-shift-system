import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

export default function EmployeeOnlyRoute() {
  const { user } = useAuth()

  if (user?.role !== 'ROLE_EMPLOYEE') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
