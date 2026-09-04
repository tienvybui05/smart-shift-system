import {
  BellOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  ReloadOutlined,
  RightOutlined,
  ScheduleOutlined,
  SwapOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Button, Empty, Skeleton, Tag } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../api/apiError.js'
import useAuth from '../hooks/useAuth.js'
import { getDashboard } from '../services/dashboardService.js'

const DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'Asia/Ho_Chi_Minh',
})

const SHORT_DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'short',
  day: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
})

const TIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
  timeZone: 'Asia/Ho_Chi_Minh',
})

const MONEY_FORMATTER = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

function formatMinutes(value) {
  const minutes = Number(value || 0)
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  if (!hours) return `${remainder} phút`
  if (!remainder) return `${hours} giờ`
  return `${hours} giờ ${remainder} phút`
}

function formatDate(value) {
  return value ? DATE_FORMATTER.format(new Date(value)) : '—'
}

function formatShortDate(value) {
  return value ? SHORT_DATE_FORMATTER.format(new Date(value)) : '—'
}

function formatTime(value) {
  return value ? TIME_FORMATTER.format(new Date(value)) : '—'
}

function formatMoney(value) {
  return MONEY_FORMATTER.format(Number(value || 0))
}

function DashboardHeader({ data, loading, onRefresh, user }) {
  const employeeView = user.role === 'ROLE_EMPLOYEE'
  const title = employeeView ? 'Tổng quan của tôi' : 'Tổng quan vận hành'
  const scope = employeeView
    ? `${data.scopeName} · ${user.positionName}`
    : data.scopeName

  return (
    <section className="dashboard-page-header">
      <div>
        <h1>{title}</h1>
        <p>{formatDate(data.generatedAt)} · {scope}</p>
      </div>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={onRefresh}>
        Làm mới
      </Button>
    </section>
  )
}

function MetricCard({ icon, label, value, note, tone = 'neutral' }) {
  return (
    <article className={`dashboard-metric dashboard-metric--${tone}`}>
      <span className="dashboard-metric-icon">{icon}</span>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <small>{note}</small>
      </div>
    </article>
  )
}

function ShiftRow({ employeeView, shift }) {
  return (
    <article className="dashboard-shift-row">
      <span
        className="dashboard-shift-color"
        style={{ backgroundColor: shift.colorCode || '#6b5ce7' }}
      />
      <div className="dashboard-shift-main">
        <strong>{shift.shiftName}</strong>
        <span>{shift.locationName}{shift.positionName ? ` · ${shift.positionName}` : ''}</span>
      </div>
      <div className="dashboard-shift-time">
        <strong>{formatShortDate(shift.startAt)}</strong>
        <span>{formatTime(shift.startAt)}–{formatTime(shift.endAt)}</span>
      </div>
      {!employeeView && (
        <div className="dashboard-shift-coverage">
          <Tag color={shift.understaffed ? 'warning' : 'success'}>
            {shift.assignedEmployees}/{shift.requiredEmployees} người
          </Tag>
          <span>{shift.understaffed ? 'Thiếu nhân sự' : 'Đủ nhân sự'}</span>
        </div>
      )}
    </article>
  )
}

function ShiftList({ employeeView = false, shifts }) {
  if (!shifts.length) {
    return (
      <Empty
        description={employeeView ? 'Chưa có ca sắp tới' : 'Chưa có ca trong 7 ngày tới'}
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }
  return (
    <div className="dashboard-shift-list">
      {shifts.map((shift) => (
        <ShiftRow employeeView={employeeView} key={shift.workShiftId} shift={shift} />
      ))}
    </div>
  )
}

function PanelHeading({ action, title, subtitle }) {
  return (
    <div className="dashboard-panel-heading">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

function StatusRow({ icon, label, value, tone = 'neutral' }) {
  return (
    <div className={`dashboard-status-row dashboard-status-row--${tone}`}>
      <span className="dashboard-status-icon">{icon}</span>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function ActionRow({ count, icon, label, to }) {
  return (
    <Link className="dashboard-action-row" to={to}>
      <span className="dashboard-action-icon">{icon}</span>
      <span>{label}</span>
      <strong>{count}</strong>
      <RightOutlined />
    </Link>
  )
}

function EmployeeDashboard({ data }) {
  const summary = data.employee
  const nextShift = data.upcomingShifts[0]

  return (
    <>
      <section className="dashboard-metric-grid">
        <MetricCard
          icon={<CalendarOutlined />}
          label="Ca trong tuần"
          value={summary.shiftsThisWeek}
          note={`${formatMinutes(summary.scheduledMinutesThisWeek)} theo lịch`}
          tone="purple"
        />
        <MetricCard
          icon={<ClockCircleOutlined />}
          label="Giờ công tháng này"
          value={formatMinutes(summary.workedMinutesThisMonth)}
          note={`${summary.onTimeRate}% lượt chấm công đúng giờ`}
          tone="blue"
        />
        <MetricCard
          icon={<DollarOutlined />}
          label="Lương tạm tính"
          value={formatMoney(summary.estimatedPayThisMonth)}
          note="Theo giờ công đã ghi nhận"
          tone="green"
        />
        <MetricCard
          icon={<FileTextOutlined />}
          label="Yêu cầu đang chờ"
          value={summary.pendingRequests}
          note="Nghỉ phép, nhận ca và đổi ca"
          tone={summary.pendingRequests ? 'orange' : 'neutral'}
        />
      </section>

      <section className="dashboard-next-shift">
        <PanelHeading
          action={<Link to="/my-schedule">Xem lịch <RightOutlined /></Link>}
          title="Ca tiếp theo"
          subtitle="Ca gần nhất trong lịch đã công bố"
        />
        {nextShift ? (
          <div className="dashboard-next-shift-body">
            <span
              className="dashboard-next-date"
              style={{ borderColor: nextShift.colorCode || '#6b5ce7' }}
            >
              <small>{formatShortDate(nextShift.startAt)}</small>
              <strong>{formatTime(nextShift.startAt)}</strong>
            </span>
            <div>
              <h3>{nextShift.shiftName}</h3>
              <p>{formatTime(nextShift.startAt)}–{formatTime(nextShift.endAt)}</p>
              <span>{nextShift.locationName} · {nextShift.positionName}</span>
            </div>
            <Tag color="processing">Đã phân công</Tag>
          </div>
        ) : (
          <Empty description="Bạn chưa có ca sắp tới" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </section>

      <div className="dashboard-columns">
        <section className="dashboard-panel">
          <PanelHeading
            action={<Link to="/my-schedule">Xem tất cả</Link>}
            title="Lịch sắp tới"
            subtitle="Tối đa 5 ca gần nhất"
          />
          <ShiftList employeeView shifts={data.upcomingShifts} />
        </section>

        <section className="dashboard-panel">
          <PanelHeading title="Cần theo dõi" subtitle="Các mục liên quan đến tài khoản của bạn" />
          <div className="dashboard-action-list">
            <ActionRow
              count={summary.availableOpenShifts}
              icon={<ScheduleOutlined />}
              label="Ca trống có thể đăng ký"
              to="/open-shifts"
            />
            <ActionRow
              count={summary.pendingRequests}
              icon={<SwapOutlined />}
              label="Yêu cầu đang chờ xử lý"
              to="/shift-swaps"
            />
            <ActionRow
              count={summary.unreadNotifications}
              icon={<BellOutlined />}
              label="Thông báo chưa đọc"
              to="/notifications"
            />
            <ActionRow
              count={`${summary.onTimeRate}%`}
              icon={<CheckCircleOutlined />}
              label="Tỷ lệ chấm công đúng giờ"
              to="/attendance"
            />
          </div>
        </section>
      </div>
    </>
  )
}

function ManagementDashboard({ data }) {
  const summary = data.management
  const pendingTotal = summary.pendingTimeOff
    + summary.pendingShiftSwaps
    + summary.pendingOpenShiftClaims
  const coverageRate = summary.shiftsToday
    ? Math.round((summary.staffedShiftsToday / summary.shiftsToday) * 100)
    : 0

  return (
    <>
      <section className="dashboard-metric-grid">
        <MetricCard
          icon={<TeamOutlined />}
          label="Nhân viên hoạt động"
          value={summary.activeEmployees}
          note={`${summary.inactiveEmployees} tài khoản đã khóa`}
          tone="purple"
        />
        <MetricCard
          icon={<CalendarOutlined />}
          label="Ca hôm nay"
          value={summary.shiftsToday}
          note={`${coverageRate}% ca đủ nhân sự`}
          tone="blue"
        />
        <MetricCard
          icon={<ExclamationCircleOutlined />}
          label="Ca thiếu người"
          value={summary.understaffedShiftsToday}
          note="Theo mức nhân sự tối thiểu"
          tone={summary.understaffedShiftsToday ? 'orange' : 'green'}
        />
        <MetricCard
          icon={<FileTextOutlined />}
          label="Chờ xử lý"
          value={pendingTotal}
          note="Đơn nghỉ, nhận ca và đổi ca"
          tone={pendingTotal ? 'orange' : 'neutral'}
        />
      </section>

      <div className="dashboard-columns">
        <section className="dashboard-panel">
          <PanelHeading title="Tình hình hôm nay" subtitle={data.scopeName} />
          <div className="dashboard-status-list">
            <StatusRow icon={<CheckCircleOutlined />} label="Ca đủ nhân sự" tone="success" value={summary.staffedShiftsToday} />
            <StatusRow icon={<ClockCircleOutlined />} label="Đã check-in" tone="info" value={summary.checkedInToday} />
            <StatusRow icon={<ExclamationCircleOutlined />} label="Đi trễ" tone="warning" value={summary.lateToday} />
            <StatusRow icon={<ExclamationCircleOutlined />} label="Vắng mặt" tone="danger" value={summary.absentToday} />
            <StatusRow
              icon={<ClockCircleOutlined />}
              label="Đến giờ nhưng chưa check-in"
              tone={summary.missingCheckInsToday ? 'warning' : 'neutral'}
              value={summary.missingCheckInsToday}
            />
          </div>
        </section>

        <section className="dashboard-panel">
          <PanelHeading title="Việc chờ duyệt" subtitle="Mở từng mục để kiểm tra chi tiết" />
          <div className="dashboard-action-list">
            <ActionRow count={summary.pendingTimeOff} icon={<FileTextOutlined />} label="Đơn xin nghỉ" to="/time-off/review" />
            <ActionRow count={summary.pendingShiftSwaps} icon={<SwapOutlined />} label="Đổi hoặc nhường ca" to="/shift-swaps/review" />
            <ActionRow count={summary.pendingOpenShiftClaims} icon={<ScheduleOutlined />} label="Đăng ký nhận ca" to="/open-shifts/review" />
          </div>
        </section>
      </div>

      <section className="dashboard-workload">
        <div>
          <span>Giờ đã xếp trong tuần</span>
          <strong>{formatMinutes(summary.scheduledMinutesThisWeek)}</strong>
        </div>
        <div>
          <span>Giờ công thực tế trong tuần</span>
          <strong>{formatMinutes(summary.workedMinutesThisWeek)}</strong>
        </div>
        <div>
          <span>Chi phí nhân công tháng này</span>
          <strong>{formatMoney(summary.estimatedLaborCostThisMonth)}</strong>
        </div>
      </section>

      <section className="dashboard-panel dashboard-panel--wide">
        <PanelHeading
          action={<Link to="/admin/work-shifts">Mở lịch làm việc <RightOutlined /></Link>}
          title="Lịch 7 ngày tới"
          subtitle="Các ca đã công bố gần nhất"
        />
        <ShiftList shifts={data.upcomingShifts} />
      </section>
    </>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await getDashboard())
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Không thể tải dữ liệu tổng quan.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let mounted = true
    getDashboard()
      .then((result) => {
        if (mounted) setData(result)
      })
      .catch((requestError) => {
        if (mounted) {
          setError(getApiErrorMessage(requestError, 'Không thể tải dữ liệu tổng quan.'))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [])

  const employeeView = useMemo(
    () => user.role === 'ROLE_EMPLOYEE',
    [user.role],
  )

  if (!data && loading) {
    return <section className="dashboard-loading"><Skeleton active paragraph={{ rows: 10 }} /></section>
  }

  if (!data) {
    return (
      <Alert
        action={<Button onClick={loadDashboard}>Thử lại</Button>}
        description="Máy chủ chưa trả về số liệu dashboard."
        message={error || 'Không thể tải dữ liệu tổng quan.'}
        showIcon
        type="error"
      />
    )
  }

  return (
    <div className="dashboard-page">
      {error && <Alert closable message={error} showIcon type="warning" />}
      <DashboardHeader data={data} loading={loading} onRefresh={loadDashboard} user={user} />
      {employeeView
        ? <EmployeeDashboard data={data} />
        : <ManagementDashboard data={data} />}
    </div>
  )
}
