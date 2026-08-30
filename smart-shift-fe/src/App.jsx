import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AdminOrManagerRoute from './components/AdminOrManagerRoute.jsx'
import AdminOnlyRoute from './components/AdminOnlyRoute.jsx'
import AppLoading from './components/AppLoading.jsx'
import EmployeeOnlyRoute from './components/EmployeeOnlyRoute.jsx'
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
const SchedulePeriodManagementPage = lazy(
  () => import('./pages/schedules/SchedulePeriodManagementPage.jsx'),
)
const WorkShiftManagementPage = lazy(
  () => import('./pages/workshifts/WorkShiftManagementPage.jsx'),
)
const AvailabilityManagementPage = lazy(
  () => import('./pages/availability/AvailabilityManagementPage.jsx'),
)
const MyWorkSchedulePage = lazy(
  () => import('./pages/schedules/MyWorkSchedulePage.jsx'),
)
const MyTimeOffRequestsPage = lazy(
  () => import('./pages/timeoff/MyTimeOffRequestsPage.jsx'),
)
const TimeOffReviewPage = lazy(
  () => import('./pages/timeoff/TimeOffReviewPage.jsx'),
)
const MyOpenShiftsPage = lazy(
  () => import('./pages/openshifts/MyOpenShiftsPage.jsx'),
)
const MyAttendancePage = lazy(
  () => import('./pages/attendance/MyAttendancePage.jsx'),
)
const AttendanceManagementPage = lazy(
  () => import('./pages/attendance/AttendanceManagementPage.jsx'),
)
const NotificationPage = lazy(
  () => import('./pages/notifications/NotificationPage.jsx'),
)
const MyPayrollPage = lazy(
  () => import('./pages/payroll/MyPayrollPage.jsx'),
)
const PayrollManagementPage = lazy(
  () => import('./pages/payroll/PayrollManagementPage.jsx'),
)
const OpenShiftClaimReviewPage = lazy(
  () => import('./pages/openshifts/OpenShiftClaimReviewPage.jsx'),
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
            <Route path="my-schedule" element={<MyWorkSchedulePage />} />
            <Route path="availability" element={<AvailabilityManagementPage />} />
            <Route path="time-off" element={<MyTimeOffRequestsPage />} />
            <Route path="notifications" element={<NotificationPage />} />
            <Route element={<EmployeeOnlyRoute />}>
              <Route path="open-shifts" element={<MyOpenShiftsPage />} />
              <Route path="attendance" element={<MyAttendancePage />} />
              <Route path="payroll" element={<MyPayrollPage />} />
            </Route>
            <Route element={<AdminOrManagerRoute />}>
              <Route
                path="attendance/manage"
                element={<AttendanceManagementPage />}
              />
              <Route
                path="payroll/manage"
                element={<PayrollManagementPage />}
              />
              <Route path="time-off/review" element={<TimeOffReviewPage />} />
              <Route
                path="open-shifts/review"
                element={<OpenShiftClaimReviewPage />}
              />
              <Route
                path="admin/shift-templates"
                element={<ShiftTemplateManagementPage />}
              />
              <Route
                path="admin/schedule-periods"
                element={<SchedulePeriodManagementPage />}
              />
              <Route
                path="admin/work-shifts"
                element={<WorkShiftManagementPage />}
              />
            </Route>
            <Route element={<AdminOnlyRoute />}>
              <Route path="admin/employees" element={<UserManagementPage />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
