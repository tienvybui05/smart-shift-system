import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  InfoCircleOutlined,
  LoginOutlined,
  LogoutOutlined,
  MobileOutlined,
  ReloadOutlined,
  WarningOutlined,
  WifiOutlined,
} from '@ant-design/icons'
import { Alert, Button, Empty, Input, Modal, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import {
  checkIn,
  checkOut,
  getMyAttendances,
} from '../../services/attendanceService.js'
import { getMyDevices } from '../../services/employeeDeviceService.js'
import AttendanceCorrectionsPanel from '../../components/attendance/AttendanceCorrectionsPanel.jsx'
import AttendanceHistoryButton from '../../components/attendance/AttendanceHistoryButton.jsx'
import { getMyWorkSchedule } from '../../services/myScheduleService.js'
import { getDeviceFingerprint, getDeviceLabel } from '../../utils/deviceFingerprint.js'

const ATTENDANCE_STATUS_CONFIG = {
  PRESENT: { label: 'Đúng giờ', color: 'success' },
  LATE: { label: 'Đi trễ', color: 'warning' },
  EARLY_LEAVE: { label: 'Về sớm', color: 'orange' },
  ABSENT: { label: 'Vắng mặt', color: 'error' },
}

const ACTIVE_ASSIGNMENT_STATUSES = ['ASSIGNED', 'CONFIRMED']
const ACTIVE_PERIOD_STATUSES = ['PUBLISHED', 'LOCKED']

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultHistoryRange() {
  const end = new Date()
  const start = new Date(end)
  start.setDate(end.getDate() - 29)
  return {
    startDate: toDateInputValue(start),
    endDate: toDateInputValue(end),
  }
}

function addDays(dateValue, days) {
  const date = new Date(`${dateValue}T12:00:00`)
  date.setDate(date.getDate() + days)
  return toDateInputValue(date)
}

function getRelativeDateLabel(value, today) {
  if (value === today) return 'Hôm nay'
  if (value === addDays(today, -1)) return 'Hôm qua'
  if (value === addDays(today, 1)) return 'Ngày mai'
  return formatDate(value)
}

function isEarlyCheckInWindowOpen(shift) {
  const startsAt = new Date(`${shift.workDate}T${shift.startTime}`)
  const now = new Date()
  return startsAt > now && startsAt.getTime() - now.getTime() <= 2 * 60 * 60 * 1000
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function formatLocalTime(value) {
  return value?.slice(0, 5) || '--:--'
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

function formatMinutes(value) {
  const minutes = Number(value || 0)
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  if (!hours) return `${remainingMinutes} phút`
  return remainingMinutes ? `${hours} giờ ${remainingMinutes} phút` : `${hours} giờ`
}

function formatDistance(value) {
  if (value === null || value === undefined) return '—'
  const distance = Number(value)
  return distance >= 1000
    ? `${(distance / 1000).toFixed(2)} km`
    : `${Math.round(distance)} m`
}

function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Trình duyệt này không hỗ trợ lấy vị trí GPS.'))
      return
    }

    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 0,
    })
  })
}

function getGeolocationErrorMessage(error) {
  if (error?.code === 1) {
    return 'Bạn cần cho phép trình duyệt truy cập vị trí để chấm công.'
  }
  if (error?.code === 2) {
    return 'Không thể xác định vị trí hiện tại. Hãy bật GPS và thử lại.'
  }
  if (error?.code === 3) {
    return 'Lấy vị trí quá thời gian. Hãy đứng ở nơi có tín hiệu GPS tốt hơn.'
  }
  return error?.message || 'Không thể lấy vị trí hiện tại.'
}

function AttendanceStatusTag({ status }) {
  const config = ATTENDANCE_STATUS_CONFIG[status] || {
    label: status || 'Chưa xác định',
    color: 'default',
  }
  return <Tag color={config.color}>{config.label}</Tag>
}

export default function MyAttendancePage() {
  const today = toDateInputValue(new Date())
  const actionStartDate = addDays(today, -1)
  const actionEndDate = addDays(today, 1)
  const defaultRange = getDefaultHistoryRange()
  const [messageApi, messageContext] = message.useMessage()
  const [actionShifts, setActionShifts] = useState([])
  const [actionAttendances, setActionAttendances] = useState([])
  const [attendances, setAttendances] = useState([])
  const [dateInputs, setDateInputs] = useState(defaultRange)
  const [startDate, setStartDate] = useState(defaultRange.startDate)
  const [endDate, setEndDate] = useState(defaultRange.endDate)
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [actionKey, setActionKey] = useState(null)
  const [deviceModalOpen, setDeviceModalOpen] = useState(false)
  const [myDevices, setMyDevices] = useState([])
  const [devicesLoading, setDevicesLoading] = useState(false)
  const [explanationModalOpen, setExplanationModalOpen] = useState(false)
  const [explanationShift, setExplanationShift] = useState(null)
  const [explanationAction, setExplanationAction] = useState('check-in')
  const [deviceExplanation, setDeviceExplanation] = useState('')
  const [inputWifiSsid, setInputWifiSsid] = useState(() => localStorage.getItem('smartshift_last_wifi') || '')
  const [lastPosition, setLastPosition] = useState(null)

  useEffect(() => {
    let mounted = true

    Promise.all([
      getMyWorkSchedule({ startDate: actionStartDate, endDate: actionEndDate }),
      getMyAttendances({ startDate: actionStartDate, endDate: actionEndDate }),
      getMyAttendances({ startDate, endDate }),
    ])
      .then(([scheduleResult, todayAttendanceResult, attendanceResult]) => {
        if (!mounted) return
        const actionAttendanceByAssignment = new Map(
          todayAttendanceResult.map((item) => [item.shiftAssignmentId, item]),
        )
        setActionShifts(scheduleResult.filter((shift) => {
          const attendance = actionAttendanceByAssignment.get(shift.assignmentId)
          return shift.workDate === today
            || Boolean(attendance?.checkInAt && !attendance?.checkOutAt)
            || (shift.workDate > today && isEarlyCheckInWindowOpen(shift))
        }))
        setActionAttendances(todayAttendanceResult)
        setAttendances(attendanceResult)
      })
      .catch((error) => {
        if (!mounted) return
        messageApi.error(getApiErrorMessage(
          error,
          'Không thể tải dữ liệu chấm công của bạn.',
        ))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [actionEndDate, actionStartDate, endDate, messageApi, refreshKey, startDate, today])

  const attendanceByAssignment = useMemo(
    () => new Map(actionAttendances.map((item) => [item.shiftAssignmentId, item])),
    [actionAttendances],
  )

  const summary = useMemo(() => ({
    total: attendances.length,
    onTime: attendances.filter((item) => item.status === 'PRESENT').length,
    exceptions: attendances.filter(
      (item) => ['LATE', 'EARLY_LEAVE', 'ABSENT'].includes(item.status),
    ).length,
    actualMinutes: attendances.reduce(
      (total, item) => total + Number(item.actualMinutes || 0),
      0,
    ),
  }), [attendances])

  function applyDateRange() {
    if (!dateInputs.startDate || !dateInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày tra cứu.')
      return
    }
    if (dateInputs.endDate < dateInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }

    const rangeDays = (
      new Date(`${dateInputs.endDate}T00:00:00`)
      - new Date(`${dateInputs.startDate}T00:00:00`)
    ) / 86400000
    if (rangeDays >= 93) {
      messageApi.error('Chỉ được tra cứu tối đa 93 ngày.')
      return
    }

    setStartDate(dateInputs.startDate)
    setEndDate(dateInputs.endDate)
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  function refreshData() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  function openMyDevicesModal() {
    setDeviceModalOpen(true)
    setDevicesLoading(true)
    getMyDevices()
      .then(setMyDevices)
      .catch((err) => {
        messageApi.error(getApiErrorMessage(err, 'Không thể tải danh sách thiết bị.'))
      })
      .finally(() => setDevicesLoading(false))
  }

  function startExplanationFlow(shift, action) {
    setExplanationShift(shift)
    setExplanationAction(action)
    setDeviceExplanation('')
    setExplanationModalOpen(true)
  }

  async function submitWithExplanation() {
    if (!explanationShift) return
    const shift = explanationShift
    const action = explanationAction
    const expl = deviceExplanation
    const wifi = inputWifiSsid.trim()
    if (wifi) {
      try { localStorage.setItem('smartshift_last_wifi', wifi) } catch { /* Storage can be unavailable; attendance still works. */ }
    }
    setExplanationModalOpen(false)
    await handleAttendanceAction(shift, action, expl, wifi)
  }

  async function handleAttendanceAction(shift, action, explanation = null, wifi = null) {
    const currentActionKey = `${shift.assignmentId}-${action}`
    setActionKey(currentActionKey)
    try {
      const position = await getCurrentPosition()
      const effectiveWifi = wifi || inputWifiSsid.trim() || undefined
      const positionPayload = {
        shiftAssignmentId: shift.assignmentId,
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMeters: position.coords.accuracy,
        deviceFingerprint: getDeviceFingerprint(),
        deviceLabel: getDeviceLabel(),
        deviceExplanation: explanation ? explanation.trim() : undefined,
        wifiSsid: effectiveWifi,
      }
      setLastPosition({
        accuracy: position.coords.accuracy,
        capturedAt: new Date(),
      })

      const result = action === 'check-in'
        ? await checkIn(positionPayload)
        : await checkOut(positionPayload)

      const replaceAttendance = (current) => {
        const remaining = current.filter(
          (item) => item.shiftAssignmentId !== result.shiftAssignmentId,
        )
        return [result, ...remaining]
      }
      setActionAttendances(replaceAttendance)
      if (result.workDate >= startDate && result.workDate <= endDate) {
        setAttendances(replaceAttendance)
      }

      if (result.unverifiedDevice && result.unverifiedWifi) {
        messageApi.warning(
          `Chấm công thành công! Cảnh báo: Thiết bị mới và Mạng Wi-Fi chưa được xác thực với chi nhánh. Quản lý sẽ đối soát bản công này.`,
          8,
        )
      } else if (result.unverifiedWifi) {
        messageApi.warning(
          `Chấm công thành công! Cảnh báo: Bạn đang không kết nối đúng Wi-Fi của chi nhánh. Quản lý sẽ đối soát bản công này.`,
          7,
        )
      } else if (result.unverifiedDevice) {
        messageApi.warning(
          action === 'check-in'
            ? `Check-in thành công lúc ${formatInstant(result.checkInAt, result.timezone)}. Thiết bị này đang chờ Quản lý duyệt.`
            : `Check-out thành công lúc ${formatInstant(result.checkOutAt, result.timezone)}. Thiết bị này đang chờ Quản lý duyệt.`,
          7,
        )
      } else {
        messageApi.success(action === 'check-in'
          ? `Check-in thành công lúc ${formatInstant(result.checkInAt, result.timezone)}.`
          : `Check-out thành công lúc ${formatInstant(result.checkOutAt, result.timezone)}.`)
      }
    } catch (error) {
      if (error?.response) {
        messageApi.error(getApiErrorMessage(error, 'Không thể thực hiện chấm công.'))
      } else {
        messageApi.error(getGeolocationErrorMessage(error))
      }
    } finally {
      setActionKey(null)
    }
  }

  const columns = [
    { title: 'Lịch sử', render: (_, row) => <AttendanceHistoryButton assignmentId={row.shiftAssignmentId} mine timezone={row.timezone} /> },
    {
      title: 'Ngày làm việc',
      dataIndex: 'workDate',
      width: 190,
      render: (value) => <strong className="my-schedule-date">{formatDate(value)}</strong>,
    },
    {
      title: 'Ca làm',
      key: 'shiftTime',
      width: 145,
      render: (_, item) => (
        <strong>
          {formatInstant(item.shiftStartAt, item.timezone)}–
          {formatInstant(item.shiftEndAt, item.timezone)}
        </strong>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 190,
      responsive: ['md'],
    },
    {
      title: 'Vào / Ra',
      key: 'attendanceTime',
      width: 150,
      render: (_, item) => (
        <div className="attendance-time-cell">
          <span><LoginOutlined /> {formatInstant(item.checkInAt, item.timezone)}</span>
          <span><LogoutOutlined /> {formatInstant(item.checkOutAt, item.timezone)}</span>
        </div>
      ),
    },
    {
      title: 'Giờ công',
      dataIndex: 'actualMinutes',
      width: 125,
      render: (value) => value === null ? '—' : <strong>{formatMinutes(value)}</strong>,
    },
    {
      title: 'Sai lệch',
      key: 'exceptions',
      width: 170,
      responsive: ['lg'],
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
      key: 'status',
      width: 140,
      render: (_, item) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <AttendanceStatusTag status={item.status} />
          {item.unverifiedDevice && (
            <Tooltip title="Chấm công từ thiết bị mới / đang chờ quản lý phê duyệt">
              <Tag color="warning" icon={<MobileOutlined />}>TB chờ duyệt</Tag>
            </Tooltip>
          )}
          {item.unverifiedWifi && (
            <Tooltip title={`Không khớp mạng Wi-Fi chi nhánh (IP: ${item.checkInIpAddress || '—'})`}>
              <Tag color="error" icon={<WifiOutlined />}>M?ng ch?a x?c minh</Tag>
            </Tooltip>
          )}
        </div>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Ghi nhận thời gian làm việc</span>
          <h1>Chấm công GPS</h1>
          <p>Check-in và check-out tại chi nhánh bằng vị trí hiện tại của thiết bị.</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button icon={<MobileOutlined />} onClick={openMyDevicesModal}>
            Thiết bị của tôi
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={refreshData}
          >
            Làm mới
          </Button>
        </div>
      </section>

      <Alert
        message="Vị trí chỉ được lấy khi bạn bấm chấm công"
        description="Hãy bật GPS, cho phép trình duyệt truy cập vị trí và đứng trong phạm vi chấm công của chi nhánh. Hệ thống sử dụng thời gian từ máy chủ."
        showIcon
        type="info"
      />

      <section className="attendance-today-section">
        <div className="attendance-section-heading">
          <div>
            <strong>Ca cần chấm công</strong>
            <span>Ca hôm nay và ca qua đêm chưa check-out</span>
          </div>
          {lastPosition && (
            <span className="attendance-gps-reading">
              <EnvironmentOutlined /> GPS ±{Math.round(lastPosition.accuracy)} m
              {' · '}{new Intl.DateTimeFormat('vi-VN', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
              }).format(lastPosition.capturedAt)}
            </span>
          )}
        </div>

        {!loading && actionShifts.length === 0 ? (
          <section className="management-card attendance-empty-card">
            <Empty description="Bạn không có ca cần chấm công lúc này." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </section>
        ) : (
          <div className="attendance-shift-list">
            {actionShifts.map((shift) => {
              const attendance = attendanceByAssignment.get(shift.assignmentId)
              const assignmentActive = ACTIVE_ASSIGNMENT_STATUSES.includes(shift.assignmentStatus)
              const periodActive = ACTIVE_PERIOD_STATUSES.includes(shift.schedulePeriodStatus)
              const canCheckIn = !attendance && assignmentActive && periodActive
              const canCheckOut = Boolean(attendance?.checkInAt && !attendance?.checkOutAt)
              const action = canCheckOut ? 'check-out' : 'check-in'
              const currentActionKey = `${shift.assignmentId}-${action}`

              return (
                <article className="management-card attendance-shift-card" key={shift.assignmentId}>
                  <div
                    className="attendance-shift-color"
                    style={{ backgroundColor: shift.colorCode || '#5B4CF0' }}
                  />
                  <div className="attendance-shift-main">
                    <span className="attendance-shift-date">
                      {getRelativeDateLabel(shift.workDate, today)}
                    </span>
                    <strong>{shift.shiftTemplateName}</strong>
                    <span>
                      <ClockCircleOutlined /> {formatLocalTime(shift.startTime)}–
                      {formatLocalTime(shift.endTime)}
                      {shift.overnight ? ' · Qua ngày' : ''}
                    </span>
                  </div>
                  <div className="attendance-shift-location">
                    <EnvironmentOutlined />
                    <div>
                      <strong>{shift.locationName}</strong>
                      <span>{shift.positionName}</span>
                    </div>
                  </div>
                  <div className="attendance-shift-status">
                    {attendance ? (
                      <>
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                          <AttendanceStatusTag status={attendance.status} />
                          {attendance.unverifiedDevice && (
                            <Tooltip title="Thiết bị này đang chờ Quản lý phê duyệt">
                              <Tag color="warning" icon={<MobileOutlined />}>Thiết bị chờ duyệt</Tag>
                            </Tooltip>
                          )}
                          {attendance.unverifiedWifi && (
                            <Tooltip title="Chấm công không khớp mạng Wi-Fi của chi nhánh">
                              <Tag color="error" icon={<WifiOutlined />}>M?ng ch?a x?c minh</Tag>
                            </Tooltip>
                          )}
                        </div>
                        <small>
                          Vào {formatInstant(attendance.checkInAt, attendance.timezone)}
                          {attendance.checkOutAt
                            ? ` · Ra ${formatInstant(attendance.checkOutAt, attendance.timezone)}`
                            : ' · Chưa check-out'}
                        </small>
                        <small>
                          Cách chi nhánh {formatDistance(
                            attendance.checkOutDistanceMeters
                              ?? attendance.checkInDistanceMeters,
                          )}
                        </small>
                      </>
                    ) : (
                      <>
                        <Tag>Chưa check-in</Tag>
                        {!periodActive && <small>Lịch chưa được công bố</small>}
                        {!assignmentActive && <small>Phân công không còn hiệu lực</small>}
                      </>
                    )}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end' }}>
                    <Button
                      danger={canCheckOut}
                      disabled={Boolean(actionKey) || (!canCheckIn && !canCheckOut)}
                      icon={canCheckOut ? <LogoutOutlined /> : <LoginOutlined />}
                      loading={actionKey === currentActionKey}
                      onClick={() => handleAttendanceAction(shift, action)}
                      type={canCheckIn ? 'primary' : 'default'}
                    >
                      {canCheckOut ? 'Check-out' : attendance ? 'Đã hoàn thành' : 'Check-in'}
                    </Button>
                    {(canCheckIn || canCheckOut) && (
                      <Button
                        size="small"
                        type="link"
                        style={{ padding: 0, fontSize: 12 }}
                        onClick={() => startExplanationFlow(shift, action)}
                      >
                        Dùng máy khác? Giải trình
                      </Button>
                    )}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>

      <section className="attendance-summary-grid">
        <article className="management-card attendance-summary-card">
          <CalendarOutlined />
          <div><span>Số lượt chấm công</span><strong>{summary.total}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <CheckCircleOutlined />
          <div><span>Đúng giờ</span><strong>{summary.onTime}</strong></div>
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

      <section className="management-card attendance-history-card">
        <div className="table-heading attendance-history-heading">
          <div>
            <strong>Lịch sử chấm công</strong>
            <span>{attendances.length} bản ghi trong khoảng đã chọn</span>
          </div>
          <div className="attendance-history-filter">
            <Input
              aria-label="Từ ngày"
              max={dateInputs.endDate}
              onChange={(event) => setDateInputs((current) => ({
                ...current,
                startDate: event.target.value,
              }))}
              type="date"
              value={dateInputs.startDate}
            />
            <Input
              aria-label="Đến ngày"
              min={dateInputs.startDate}
              onChange={(event) => setDateInputs((current) => ({
                ...current,
                endDate: event.target.value,
              }))}
              type="date"
              value={dateInputs.endDate}
            />
            <Button icon={<ReloadOutlined />} onClick={applyDateRange}>Tra cứu</Button>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={attendances}
          loading={loading}
          locale={{ emptyText: 'Chưa có dữ liệu chấm công trong khoảng này.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1050 }}
        />
      </section>

      <AttendanceCorrectionsPanel onChanged={() => setRefreshKey(value => value + 1)} />

      {/* Modal danh sách thiết bị của nhân viên */}
      <Modal
        footer={null}
        onCancel={() => setDeviceModalOpen(false)}
        open={deviceModalOpen}
        title={<span><MobileOutlined /> Thiết bị chấm công của tôi</span>}
        width={680}
      >
        <p style={{ color: '#6b7280', fontSize: 13, marginBottom: 16 }}>
          Hệ thống ghi nhận thiết bị bạn sử dụng để chấm công nhằm chống gian lận. Thiết bị hiện tại của bạn: <strong>{getDeviceLabel()}</strong>.
        </p>
        <Table
          dataSource={myDevices}
          loading={devicesLoading}
          locale={{ emptyText: 'Chưa có thiết bị nào được ghi nhận.' }}
          pagination={false}
          rowKey="id"
          size="small"
          columns={[
            {
              title: 'Tên thiết bị',
              dataIndex: 'deviceLabel',
              render: (label, item) => (
                <div>
                  <strong>{label || item.deviceFingerprint}</strong>
                  {item.deviceExplanation && (
                    <div style={{ color: '#6b7280', fontSize: 12 }}>
                      Lý do: {item.deviceExplanation}
                    </div>
                  )}
                </div>
              ),
            },
            {
              title: 'Trạng thái',
              dataIndex: 'status',
              width: 130,
              render: (status) => {
                if (status === 'APPROVED') return <Tag color="success">Đã duyệt</Tag>
                if (status === 'REVOKED') return <Tag color="error">Bị thu hồi</Tag>
                return <Tag color="warning">Chờ duyệt</Tag>
              },
            },
            {
              title: 'Lần cuối sử dụng',
              dataIndex: 'lastSeenAt',
              width: 170,
              render: (value) => value ? formatInstant(value, 'Asia/Ho_Chi_Minh') : '—',
            },
          ]}
        />
      </Modal>

      {/* Modal nhập giải trình khi đổi thiết bị */}
      <Modal
        cancelText="Hủy"
        okText="Xác nhận chấm công"
        onCancel={() => setExplanationModalOpen(false)}
        onOk={submitWithExplanation}
        open={explanationModalOpen}
        title={<span><InfoCircleOutlined /> Giải trình thiết bị chấm công</span>}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          <Alert
            description={`Thiết bị đang nhận diện: "${getDeviceLabel()}". Nếu đây là máy mới hoặc máy mượn, vui lòng nêu lý do để quản lý duyệt nhanh chóng.`}
            message="Chống gian lận chấm công"
            showIcon
            type="info"
          />
          <div>
            <label style={{ display: 'block', marginBottom: 6, fontWeight: 500 }}>
              <WifiOutlined /> Tên mạng Wi-Fi đang kết nối (nếu có):
            </label>
            <Input
              maxLength={100}
              onChange={(e) => setInputWifiSsid(e.target.value)}
              placeholder="Ví dụ: Highlands_Coffee_Q1"
              value={inputWifiSsid}
            />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: 6, fontWeight: 500 }}>
              Lý do sử dụng thiết bị này:
            </label>
            <Input.TextArea
              maxLength={255}
              onChange={(e) => setDeviceExplanation(e.target.value)}
              placeholder="Ví dụ: Đổi điện thoại mới, máy cũ hỏng, mượn máy đồng nghiệp,..."
              rows={3}
              showCount
              value={deviceExplanation}
            />
          </div>
        </div>
      </Modal>
    </>
  )
}
