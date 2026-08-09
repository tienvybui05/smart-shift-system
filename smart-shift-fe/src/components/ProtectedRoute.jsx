import { Navigate, Outlet, useLocation } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import AppLoading from './AppLoading.jsx'

export default function ProtectedRoute() {
  const { initializing, isAuthenticated } = useAuth()
  const location = useLocation()

  if (initializing) return <AppLoading />

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
