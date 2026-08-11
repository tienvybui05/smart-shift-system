import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AdminOnlyRoute from './components/AdminOnlyRoute.jsx'
import AppLoading from './components/AppLoading.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import PublicOnlyRoute from './components/PublicOnlyRoute.jsx'
import './App.css'

const AppLayout = lazy(() => import('./components/AppLayout.jsx'))
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'))
const LoginPage = lazy(() => import('./pages/LoginPage.jsx'))
const UserManagementPage = lazy(() => import('./pages/users/UserManagementPage.jsx'))
const ShiftTemplateManagementPage = lazy(
  () => import('./pages/shifts/ShiftTemplateManagementPage.jsx'),
)

export default function App() {
  return (
    <Suspense fallback={<AppLoading />}>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route element={<AdminOnlyRoute />}>
              <Route path="admin/employees" element={<UserManagementPage />} />
              <Route
                path="admin/shift-templates"
                element={<ShiftTemplateManagementPage />}
              />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
