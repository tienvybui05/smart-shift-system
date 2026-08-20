import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

const REVIEWER_ROLES = new Set(['ROLE_ADMIN', 'ROLE_MANAGER'])

export default function AdminOrManagerRoute() {
  const { user } = useAuth()

  if (!REVIEWER_ROLES.has(user?.role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
