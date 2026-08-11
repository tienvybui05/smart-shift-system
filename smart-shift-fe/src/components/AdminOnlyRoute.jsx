import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

export default function AdminOnlyRoute() {
  const { user } = useAuth()

  if (user?.role !== 'ROLE_ADMIN') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
