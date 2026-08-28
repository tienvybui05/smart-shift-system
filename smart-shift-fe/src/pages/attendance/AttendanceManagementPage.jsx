import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  ReloadOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Alert, Button, Input, Select, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import AttendanceApprovalModal from '../../components/attendance/AttendanceApprovalModal.jsx'
import useAuth from '../../hooks/useAuth.js'
import {
  approveAttendance,
  getAttendances,
} from '../../services/attendanceService.js'
import { getLocations } from '../../services/referenceService.js'

const STATUS_CONFIG = {
  PRESENT: { label: 'Đúng giờ', color: 'success' },
  LATE: { label: 'Đi trễ', color: 'warning' },
  EARLY_LEAVE: { label: 'Về sớm', color: 'orange' },
  ABSENT: { label: 'Vắng mặt', color: 'error' },
}

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultRange() {
  const end = new Date()
  const start = new Date(end)
  start.setDate(end.getDate() - 6)
  return {
    startDate: toDateInputValue(start),
    endDate: toDateInputValue(end),
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function formatInstant(value, timezone) {
  if (!value) return '--:--'
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: timezone || 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function formatDateTime(value, timezone) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: timezone || 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function formatMinutes(value) {
  const minutes = Number(value || 0)
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  if (!hours) return `${remainder} phút`
  return remainder ? `${hours} giờ ${remainder} phút` : `${hours} giờ`
}

function AttendanceStatusTag({ status }) {
  const config = STATUS_CONFIG[status] || { label: status, color: 'default' }
  return <Tag color={config.color}>{config.label}</Tag>
}

export default function AttendanceManagementPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const defaultRange = getDefaultRange()
  const [messageApi, messageContext] = message.useMessage()
  const [attendances, setAttendances] = useState([])
  const [locations, setLocations] = useState(() => (
    isAdmin || !user.locationId
      ? []
      : [{ id: user.locationId, name: user.locationName }]
  ))
  const [locationsLoading, setLocationsLoading] = useState(isAdmin)
  const [locationError, setLocationError] = useState('')
  const [locationRefreshKey, setLocationRefreshKey] = useState(0)
  const [filterInputs, setFilterInputs] = useState({
    ...defaultRange,
    locationId: isAdmin ? undefined : user.locationId,
    status: undefined,
  })
  const [filters, setFilters] = useState(filterInputs)
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [editingAttendance, setEditingAttendance] = useState(null)

  useEffect(() => {
    if (!isAdmin) return undefined
    let mounted = true
    getLocations()
      .then((result) => {
        if (mounted) setLocations(result)
      })
      .catch((error) => {
        if (mounted) {
          setLocationError(getApiErrorMessage(error, 'Không thể tải danh sách chi nhánh.'))
        }
      })
      .finally(() => {
        if (mounted) setLocationsLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [isAdmin, locationRefreshKey])

  useEffect(() => {
    let mounted = true
    getAttendances(filters)
      .then((result) => {
        if (mounted) setAttendances(result)
      })
      .catch((error) => {
        if (!mounted) return
        setAttendances([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách chấm công.'))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [filters, messageApi, refreshKey])

  const summary = useMemo(() => ({
    total: attendances.length,
    approved: attendances.filter((item) => item.approvedAt).length,
    exceptions: attendances.filter(
      (item) => ['LATE', 'EARLY_LEAVE', 'ABSENT'].includes(item.status),
    ).length,
    actualMinutes: attendances.reduce(
      (total, item) => total + Number(item.actualMinutes || 0),
      0,
    ),
  }), [attendances])

  function validateRange() {
    if (!filterInputs.startDate || !filterInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày.')
      return false
    }
    if (filterInputs.endDate < filterInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return false
    }
    const days = (
      new Date(`${filterInputs.endDate}T00:00:00`)
      - new Date(`${filterInputs.startDate}T00:00:00`)
    ) / 86400000
    if (days >= 93) {
      messageApi.error('Chỉ được tra cứu tối đa 93 ngày.')
      return false
    }
    return true
  }

  function applyFilters() {
    if (!validateRange()) return
    setLoading(true)
    setFilters({ ...filterInputs })
  }

  function resetFilters() {
    const range = getDefaultRange()
    const resetValues = {
      ...range,
      locationId: isAdmin ? undefined : user.locationId,
      status: undefined,
    }
    setFilterInputs(resetValues)
    setLoading(true)
    setFilters(resetValues)
  }

  function retryLocations() {
    setLocationsLoading(true)
    setLocationError('')
    setLocationRefreshKey((current) => current + 1)
  }

  function refreshData() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleApproval(payload) {
    const updated = await approveAttendance(editingAttendance.id, payload)
    setAttendances((current) => {
      if (filters.status && filters.status !== updated.status) {
        return current.filter((item) => item.id !== updated.id)
      }
      return current.map((item) => item.id === updated.id ? updated : item)
    })
    messageApi.success(payload.checkInAt
      ? 'Đã lưu và duyệt bản chấm công.'
      : 'Đã ghi nhận nhân viên vắng mặt.')
  }

  const columns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 205,
      render: (_, item) => (
        <div className="employee-cell">
          <strong>{item.employeeName}</strong>
          <span>{item.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Ngày làm việc',
      dataIndex: 'workDate',
      width: 165,
      render: (value) => <strong className="my-schedule-date">{formatDate(value)}</strong>,
    },
    {
      title: 'Ca dự kiến',
      key: 'scheduledTime',
      width: 125,
      render: (_, item) => (
        <strong>
          {formatInstant(item.shiftStartAt, item.timezone)}–
          {formatInstant(item.shiftEndAt, item.timezone)}
        </strong>
      ),
    },
    {
      title: 'Giờ vào / ra',
      key: 'actualTime',
      width: 150,
      render: (_, item) => (
        <div className="attendance-time-cell">
          <span>Vào: <strong>{formatInstant(item.checkInAt, item.timezone)}</strong></span>
          <span>Ra: <strong>{formatInstant(item.checkOutAt, item.timezone)}</strong></span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 180,
      responsive: ['lg'],
    },
    {
      title: 'Giờ công',
      dataIndex: 'actualMinutes',
      width: 110,
      render: (value) => value === null ? '—' : <strong>{formatMinutes(value)}</strong>,
    },
    {
      title: 'Sai lệch',
      key: 'exceptions',
      width: 185,
      responsive: ['xl'],
      render: (_, item) => {
        const details = []
        if (item.lateMinutes > 0) details.push(`Trễ ${item.lateMinutes} phút`)
        if (item.earlyLeaveMinutes > 0) details.push(`Về sớm ${item.earlyLeaveMinutes} phút`)
        if (item.overtimeMinutes > 0) details.push(`Tăng ca ${item.overtimeMinutes} phút`)
        return details.length ? details.join(' · ') : <span className="empty-value">Không có</span>
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 110,
      render: (status) => <AttendanceStatusTag status={status} />,
    },
    {
      title: 'Xác nhận',
      key: 'approval',
      width: 150,
      render: (_, item) => item.approvedAt ? (
        <div className="employee-cell employee-cell--normal">
          <span><Tag color="success">Đã duyệt</Tag></span>
          <Tooltip title={formatDateTime(item.approvedAt, item.timezone)}>
            <small>{item.approvedByName}</small>
          </Tooltip>
        </div>
      ) : <Tag>Chưa duyệt</Tag>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 90,
      align: 'center',
      render: (_, item) => (
        <Tooltip title={item.approvedAt ? 'Xem và điều chỉnh lại' : 'Duyệt chấm công'}>
          <Button
            icon={item.approvedAt ? <EditOutlined /> : <CheckCircleOutlined />}
            onClick={() => setEditingAttendance(item)}
            type="text"
          />
        </Tooltip>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Quản lý nhân sự</span>
          <h1>Quản lý chấm công</h1>
          <p>Theo dõi giờ vào, giờ ra, sai lệch và xác nhận bảng công của nhân viên.</p>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={refreshData}>
          Làm mới
        </Button>
      </section>

      {locationError && (
        <Alert
          action={<Button onClick={retryLocations} size="small">Thử lại</Button>}
          message={locationError}
          showIcon
          type="error"
        />
      )}

      <section className="management-card attendance-management-filter-panel">
        <Input
          aria-label="Từ ngày"
          max={filterInputs.endDate}
          onChange={(event) => setFilterInputs((current) => ({
            ...current,
            startDate: event.target.value,
          }))}
          type="date"
          value={filterInputs.startDate}
        />
        <Input
          aria-label="Đến ngày"
          min={filterInputs.startDate}
          onChange={(event) => setFilterInputs((current) => ({
            ...current,
            endDate: event.target.value,
          }))}
          type="date"
          value={filterInputs.endDate}
        />
        <Select
          allowClear={isAdmin}
          disabled={!isAdmin}
          loading={locationsLoading}
          onChange={(value) => setFilterInputs((current) => ({
            ...current,
            locationId: value,
          }))}
          options={locations.map((location) => ({
            label: location.code ? `${location.code} — ${location.name}` : location.name,
            value: location.id,
          }))}
          placeholder="Tất cả chi nhánh"
          showSearch
          optionFilterProp="label"
          value={filterInputs.locationId}
        />
        <Select
          allowClear
          onChange={(value) => setFilterInputs((current) => ({
            ...current,
            status: value,
          }))}
          options={Object.entries(STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái"
          value={filterInputs.status}
        />
        <Button onClick={applyFilters} type="primary">Áp dụng</Button>
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="attendance-management-summary-grid">
        <article className="management-card attendance-summary-card">
          <TeamOutlined />
          <div><span>Bản chấm công</span><strong>{summary.total}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <CheckCircleOutlined />
          <div><span>Đã duyệt</span><strong>{summary.approved}</strong></div>
        </article>
        <article className="management-card attendance-summary-card attendance-summary-card--warning">
          <WarningOutlined />
          <div><span>Cần chú ý</span><strong>{summary.exceptions}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <ClockCircleOutlined />
          <div><span>Tổng giờ thực tế</span><strong>{formatMinutes(summary.actualMinutes)}</strong></div>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách chấm công</strong>
            <span>{attendances.length} bản ghi phù hợp bộ lọc</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={attendances}
          loading={loading}
          locale={{ emptyText: 'Không có bản chấm công phù hợp.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1450 }}
        />
      </section>

      <AttendanceApprovalModal
        attendance={editingAttendance}
        onClose={() => setEditingAttendance(null)}
        onSubmit={handleApproval}
        open={Boolean(editingAttendance)}
      />
    </>
  )
}
