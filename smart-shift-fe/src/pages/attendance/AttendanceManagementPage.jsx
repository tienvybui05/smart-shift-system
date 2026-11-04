import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  MobileOutlined,
  ReloadOutlined,
  StopOutlined,
  TeamOutlined,
  WarningOutlined,
  WifiOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Badge,
  Button,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/apiError.js'
import AttendanceApprovalModal from '../../components/attendance/AttendanceApprovalModal.jsx'
import AttendanceCorrectionsPanel from '../../components/attendance/AttendanceCorrectionsPanel.jsx'
import AttendanceHistoryButton from '../../components/attendance/AttendanceHistoryButton.jsx'
import useAuth from '../../hooks/useAuth.js'
import {
  approveAttendance,
  getAttendances,
} from '../../services/attendanceService.js'
import {
  getDevices,
  reviewDevice,
} from '../../services/employeeDeviceService.js'
import { getLocations } from '../../services/referenceService.js'
import { downloadExcel } from '../../services/reportExportService.js'

const STATUS_CONFIG = {
  PRESENT: { label: 'Đúng giờ', color: 'success' },
  LATE: { label: 'Đi trễ', color: 'warning' },
  EARLY_LEAVE: { label: 'Về sớm', color: 'orange' },
  ABSENT: { label: 'Vắng mặt', color: 'error' },
}

const DEVICE_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', color: 'warning' },
  APPROVED: { label: 'Đã duyệt', color: 'success' },
  REVOKED: { label: 'Bị thu hồi', color: 'error' },
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
  const [searchParams, setSearchParams] = useSearchParams()
  const [exporting, setExporting] = useState(false)
  const activeTab = searchParams.get('tab') || 'attendances'

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

  // Attendance Filters
  const [filterInputs, setFilterInputs] = useState({
    ...defaultRange,
    locationId: isAdmin ? undefined : user.locationId,
    status: undefined,
  })
  const [filters, setFilters] = useState(filterInputs)
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [editingAttendance, setEditingAttendance] = useState(null)

  // Employee Devices State
  const [devices, setDevices] = useState([])
  const [devicesLoading, setDevicesLoading] = useState(true)
  const [deviceStatusFilter, setDeviceStatusFilter] = useState(undefined)
  const [deviceLocationFilter, setDeviceLocationFilter] = useState(isAdmin ? undefined : user.locationId)
  const [deviceRefreshKey, setDeviceRefreshKey] = useState(0)
  const [reviewingDeviceId, setReviewingDeviceId] = useState(null)

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

  // Load Attendances
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

  // Load Devices
  useEffect(() => {
    let mounted = true
    getDevices({
      locationId: deviceLocationFilter,
      status: deviceStatusFilter,
    })
      .then((result) => {
        if (mounted) setDevices(result)
      })
      .catch((error) => {
        if (!mounted) return
        setDevices([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách thiết bị.'))
      })
      .finally(() => {
        if (mounted) setDevicesLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [deviceLocationFilter, deviceStatusFilter, deviceRefreshKey, messageApi])

  const pendingDevicesCount = useMemo(
    () => devices.filter((d) => d.status === 'PENDING').length,
    [devices],
  )

  const summary = useMemo(() => ({
    total: attendances.length,
    approved: attendances.filter((item) => item.approvedAt).length,
    exceptions: attendances.filter(
      (item) => ['LATE', 'EARLY_LEAVE', 'ABSENT'].includes(item.status),
    ).length,
    unverified: attendances.filter((item) => item.unverifiedDevice).length,
    unverifiedWifi: attendances.filter((item) => item.unverifiedWifi).length,
    actualMinutes: attendances.reduce(
      (total, item) => total + Number(item.actualMinutes || 0),
      0,
    ),
  }), [attendances])

  function applyFilters() {
    if (!filterInputs.startDate || !filterInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày.')
      return
    }
    if (filterInputs.endDate < filterInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }
    const rangeDays = (
      new Date(`${filterInputs.endDate}T00:00:00`)
      - new Date(`${filterInputs.startDate}T00:00:00`)
    ) / 86400000
    if (rangeDays >= 93) {
      messageApi.error('Chỉ được tra cứu tối đa 93 ngày.')
      return
    }
    setLoading(true)
    setFilters(filterInputs)
  }

  function resetFilters() {
    const nextFilters = {
      ...defaultRange,
      locationId: isAdmin ? undefined : user.locationId,
      status: undefined,
    }
    setFilterInputs(nextFilters)
    setFilters(nextFilters)
    setLoading(true)
  }

  async function handleExportExcel() {
    setExporting(true)
    try {
      await downloadExcel('attendance', filters)
      messageApi.success('Đã tải bảng chấm công Excel theo bộ lọc đang áp dụng.')
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không xuất được bảng chấm công.'))
    } finally { setExporting(false) }
  }

  function refreshAll() {
    setLoading(true)
    setRefreshKey((k) => k + 1)
    setDevicesLoading(true)
    setDeviceRefreshKey((k) => k + 1)
  }

  function retryLocations() {
    setLocationError('')
    setLocationsLoading(true)
    setLocationRefreshKey((current) => current + 1)
  }

  async function handleApproval(payload) {
    if (!editingAttendance) return
    const updated = await approveAttendance(editingAttendance.id, payload)
    setAttendances((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    messageApi.success(payload.checkInAt
      ? 'Đã cập nhật và duyệt chấm công thành công.'
      : 'Đã xác nhận vắng mặt ca làm.')
    setEditingAttendance(null)
  }

  async function handleReviewDevice(deviceId, approved) {
    setReviewingDeviceId(deviceId)
    try {
      const updated = await reviewDevice(deviceId, { approved })
      setDevices((prev) => prev.map((d) => (d.id === deviceId ? updated : d)))
      messageApi.success(
        approved
          ? 'Đã phê duyệt thiết bị thành công. Nhân viên có thể dùng thiết bị này để chấm công.'
          : 'Đã thu hồi quyền của thiết bị.',
      )
    } catch (err) {
      messageApi.error(getApiErrorMessage(err, 'Thao tác trên thiết bị thất bại.'))
    } finally {
      setReviewingDeviceId(null)
    }
  }

  const attendanceColumns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      width: 190,
      fixed: 'left',
      render: (_, item) => (
        <div className="employee-cell">
          <strong>{item.employeeName}</strong>
          <small>{item.employeeCode}</small>
        </div>
      ),
    },
    {
      title: 'Ngày làm',
      dataIndex: 'workDate',
      width: 140,
      render: (value) => <strong>{formatDate(value)}</strong>,
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
      width: 160,
      render: (_, item) => (
        <div className="employee-cell employee-cell--normal">
          <div style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
            {item.approvedAt ? <Tag color="success">Đã duyệt</Tag> : <Tag>Chưa duyệt</Tag>}
            {item.unverifiedDevice && (
              <Tooltip title="Chấm công từ thiết bị mới / chưa phê duyệt">
                <Tag color="warning" icon={<MobileOutlined />}>TB lạ</Tag>
              </Tooltip>
            )}
            {item.unverifiedWifi && (
              <Tooltip title={`Không khớp mạng Wi-Fi chi nhánh (IP: ${item.checkInIpAddress || '—'}, Wi-Fi: ${item.checkInWifiSsid || '—'})`}>
                <Tag color="error" icon={<WifiOutlined />}>M?ng ch?a x?c minh</Tag>
              </Tooltip>
            )}
          </div>
          {item.approvedAt && (
            <Tooltip title={formatDateTime(item.approvedAt, item.timezone)}>
              <small>{item.approvedByName}</small>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 90,
      align: 'center',
      render: (_, item) => (
        <Space wrap>
        <Tooltip title={item.approvedAt ? 'Xem và điều chỉnh lại' : 'Duyệt chấm công'}>
          <Button
            icon={item.approvedAt ? <EditOutlined /> : <CheckCircleOutlined />}
            onClick={() => setEditingAttendance(item)}
            type="text"
          />
        </Tooltip>
        <AttendanceHistoryButton assignmentId={item.shiftAssignmentId} timezone={item.timezone} />
        </Space>
      ),
    },
  ]

  const deviceColumns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      width: 190,
      fixed: 'left',
      render: (_, item) => (
        <div className="employee-cell">
          <strong>{item.employeeName}</strong>
          <small>{item.employeeCode}</small>
        </div>
      ),
    },
    {
      title: 'Tên thiết bị',
      dataIndex: 'deviceLabel',
      width: 220,
      render: (label, item) => (
        <div>
          <strong style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <MobileOutlined /> {label || 'Thiết bị không đặt tên'}
          </strong>
          <small style={{ color: '#9ca3af', fontFamily: 'monospace' }}>
            {item.deviceFingerprint}
          </small>
        </div>
      ),
    },
    {
      title: 'Giải trình của nhân viên',
      dataIndex: 'deviceExplanation',
      width: 260,
      render: (val) => val ? (
        <Alert
          message={<span style={{ fontSize: 13 }}>{val}</span>}
          showIcon
          style={{ padding: '4px 8px' }}
          type="warning"
        />
      ) : (
        <span style={{ color: '#9ca3af', fontStyle: 'italic', fontSize: 13 }}>
          Không có giải trình
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (status) => {
        const conf = DEVICE_STATUS_CONFIG[status] || { label: status, color: 'default' }
        return <Tag color={conf.color}>{conf.label}</Tag>
      },
    },
    {
      title: 'Đăng ký lúc',
      dataIndex: 'registeredAt',
      width: 160,
      render: (val) => formatDateTime(val, 'Asia/Ho_Chi_Minh'),
    },
    {
      title: 'Lần cuối chấm công',
      dataIndex: 'lastSeenAt',
      width: 160,
      render: (val) => formatDateTime(val, 'Asia/Ho_Chi_Minh'),
    },
    {
      title: 'Người duyệt',
      key: 'approvedBy',
      width: 150,
      render: (_, item) => item.approvedByName ? (
        <div>
          <span>{item.approvedByName}</span>
          <div style={{ color: '#9ca3af', fontSize: 11 }}>
            {formatDateTime(item.approvedAt, 'Asia/Ho_Chi_Minh')}
          </div>
        </div>
      ) : <span style={{ color: '#9ca3af' }}>—</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 170,
      align: 'center',
      render: (_, item) => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
          {item.status !== 'APPROVED' && (
            <Popconfirm
              cancelText="Hủy"
              description="Các lần chấm công tiếp theo từ thiết bị này sẽ được công nhận."
              okText="Phê duyệt"
              onConfirm={() => handleReviewDevice(item.id, true)}
              title="Phê duyệt thiết bị này?"
            >
              <Button
                icon={<CheckCircleOutlined />}
                loading={reviewingDeviceId === item.id}
                size="small"
                type="primary"
              >
                Duyệt
              </Button>
            </Popconfirm>
          )}
          {item.status !== 'REVOKED' && (
            <Popconfirm
              cancelText="Hủy"
              description="Nhân viên sẽ không thể sử dụng thiết bị này để chấm công."
              okButtonProps={{ danger: true }}
              okText="Thu hồi"
              onConfirm={() => handleReviewDevice(item.id, false)}
              title="Thu hồi quyền thiết bị?"
            >
              <Button
                danger
                icon={<StopOutlined />}
                loading={reviewingDeviceId === item.id}
                size="small"
              >
                Thu hồi
              </Button>
            </Popconfirm>
          )}
        </div>
      ),
    },
  ]

  const tabItems = [
    { key: 'corrections', label: 'Điều chỉnh công', children: <AttendanceCorrectionsPanel reviewer locations={locations} onChanged={refreshAll} /> },
    {
      key: 'attendances',
      label: (
        <span>
          <TeamOutlined /> Bảng chấm công
        </span>
      ),
      children: (
        <>
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
              disabled={!isAdmin && Boolean(user.locationId)}
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
              <MobileOutlined />
              <div><span>Thiết bị chưa duyệt</span><strong>{summary.unverified}</strong></div>
            </article>
            <article className="management-card attendance-summary-card">
              <WifiOutlined />
              <div><span>Sai mạng Wi-Fi</span><strong>{summary.unverifiedWifi}</strong></div>
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
              <Button
                disabled={loading}
                loading={exporting}
                icon={<DownloadOutlined />}
                onClick={handleExportExcel}
              >
                Xuất Excel (.xlsx)
              </Button>
            </div>
            <Table
              columns={attendanceColumns}
              dataSource={attendances}
              loading={loading}
              locale={{ emptyText: 'Không có bản chấm công phù hợp.' }}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              rowKey="id"
              scroll={{ x: 1450 }}
            />
          </section>
        </>
      ),
    },
    {
      key: 'devices',
      label: (
        <span>
          <MobileOutlined /> Duyệt thiết bị
          {pendingDevicesCount > 0 && (
            <Badge count={pendingDevicesCount} style={{ marginLeft: 8 }} />
          )}
        </span>
      ),
      children: (
        <>
          <section className="management-card attendance-management-filter-panel">
            <Select
              allowClear={isAdmin}
              disabled={!isAdmin && Boolean(user.locationId)}
              loading={locationsLoading}
              onChange={(value) => { if (value !== deviceLocationFilter) { setDevicesLoading(true); setDeviceLocationFilter(value) } }}
              options={locations.map((loc) => ({
                label: loc.code ? `${loc.code} — ${loc.name}` : loc.name,
                value: loc.id,
              }))}
              placeholder="Tất cả chi nhánh"
              showSearch
              style={{ width: 220 }}
              value={deviceLocationFilter}
            />
            <Select
              allowClear
              onChange={(value) => { if (value !== deviceStatusFilter) { setDevicesLoading(true); setDeviceStatusFilter(value) } }}
              options={[
                { label: 'Chờ duyệt', value: 'PENDING' },
                { label: 'Đã duyệt', value: 'APPROVED' },
                { label: 'Bị thu hồi', value: 'REVOKED' },
              ]}
              placeholder="Tất cả trạng thái"
              style={{ width: 160 }}
              value={deviceStatusFilter}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => { setDevicesLoading(true); setDeviceRefreshKey((k) => k + 1) }}
            >
              Làm mới
            </Button>
          </section>

          <section className="management-card employee-table-card">
            <div className="table-heading">
              <div>
                <strong>Thiết bị chấm công của nhân viên</strong>
                <span>
                  {devices.length} thiết bị được ghi nhận · {pendingDevicesCount} thiết bị chờ phê duyệt
                </span>
              </div>
            </div>
            <Table
              columns={deviceColumns}
              dataSource={devices}
              loading={devicesLoading}
              locale={{ emptyText: 'Chưa có thiết bị nào phù hợp bộ lọc.' }}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              rowKey="id"
              scroll={{ x: 1300 }}
            />
          </section>
        </>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Quản lý nhân sự</span>
          <h1>Quản lý chấm công & Thiết bị</h1>
          <p>Theo dõi bảng công, phát hiện gian lận và phê duyệt thiết bị chấm công của nhân viên.</p>
        </div>
        <Button
          icon={<ReloadOutlined />}
          loading={loading || devicesLoading}
          onClick={refreshAll}
        >
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

      <Tabs
        activeKey={activeTab}
        items={tabItems}
        onChange={(key) => setSearchParams({ tab: key })}
        style={{ marginTop: 16 }}
      />

      <AttendanceApprovalModal
        attendance={editingAttendance}
        onClose={() => setEditingAttendance(null)}
        onSubmit={handleApproval}
        open={Boolean(editingAttendance)}
      />
    </>
  )
}
