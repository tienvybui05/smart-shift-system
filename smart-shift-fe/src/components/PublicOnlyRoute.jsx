import { Navigate, Outlet } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import AppLoading from './AppLoading.jsx'

export default function PublicOnlyRoute() {
  const { initializing, isAuthenticated } = useAuth()

  if (initializing) return <AppLoading />
  if (isAuthenticated) return <Navigate to="/" replace />

  return <Outlet />
}
