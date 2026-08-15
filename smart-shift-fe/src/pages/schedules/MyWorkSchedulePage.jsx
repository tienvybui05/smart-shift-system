import {
  CalendarOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import { Alert, Button, Input, Table, Tag, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getMyWorkSchedule } from '../../services/myScheduleService.js'

const PERIOD_STATUS_CONFIG = {
  DRAFT: { label: 'Lịch nháp', color: 'default' },
  GENERATING: { label: 'Đang xếp lịch', color: 'processing' },
  PUBLISHED: { label: 'Đã công bố', color: 'blue' },
  LOCKED: { label: 'Đã khóa', color: 'purple' },
}

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultRange() {
  const start = new Date()
  const end = new Date(start)
  end.setDate(start.getDate() + 30)
  return {
    startDate: toDateInputValue(start),
    endDate: toDateInputValue(end),
  }
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

function formatTime(value) {
  return value?.slice(0, 5) || '--:--'
}

function formatWorkHours(workMinutes) {
  const hours = Number(workMinutes || 0) / 60
  return Number.isInteger(hours) ? `${hours} giờ` : `${hours.toFixed(1)} giờ`
}

export default function MyWorkSchedulePage() {
  const defaultRange = getDefaultRange()
  const [messageApi, messageContext] = message.useMessage()
  const [dateInputs, setDateInputs] = useState(defaultRange)
  const [startDate, setStartDate] = useState(defaultRange.startDate)
  const [endDate, setEndDate] = useState(defaultRange.endDate)
  const [schedule, setSchedule] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let mounted = true
    getMyWorkSchedule({ startDate, endDate })
      .then((result) => {
        if (mounted) setSchedule(result)
      })
      .catch((error) => {
        if (mounted) {
          setSchedule([])
          messageApi.error(getApiErrorMessage(
            error,
            'Không thể tải lịch làm của bạn.',
          ))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [endDate, messageApi, refreshKey, startDate])

  const summary = useMemo(() => {
    const totalMinutes = schedule.reduce(
      (total, item) => total + Number(item.workMinutes || 0),
      0,
    )
    return {
      totalShifts: schedule.length,
      totalMinutes,
      publishedShifts: schedule.filter(
        (item) => ['PUBLISHED', 'LOCKED'].includes(item.schedulePeriodStatus),
      ).length,
    }
  }, [schedule])

  function applyDateRange() {
    if (dateInputs.endDate < dateInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }
    setStartDate(dateInputs.startDate)
    setEndDate(dateInputs.endDate)
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  const columns = [
    {
      title: 'Ngày làm việc',
      dataIndex: 'workDate',
      width: 205,
      render: (value) => <strong className="my-schedule-date">{formatDate(value)}</strong>,
    },
    {
      title: 'Ca làm',
      key: 'shift',
      width: 190,
      render: (_, item) => (
        <div className="shift-template-cell">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: item.colorCode || '#5B4CF0' }}
          />
          <div className="employee-cell employee-cell--normal">
            <strong>{item.shiftTemplateName}</strong>
            <span>
              {formatTime(item.startTime)}–{formatTime(item.endTime)}
              {item.overnight ? ' · Qua ngày' : ''}
            </span>
          </div>
        </div>
      ),
    },
    {
      title: 'Nơi làm việc',
      key: 'location',
      width: 210,
      responsive: ['md'],
      render: (_, item) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{item.locationName}</strong>
          <span>{item.positionName}</span>
        </div>
      ),
    },
    {
      title: 'Giờ công',
      dataIndex: 'workMinutes',
      width: 105,
      align: 'center',
      render: (value) => <strong>{formatWorkHours(value)}</strong>,
    },
    {
      title: 'Trạng thái lịch',
      dataIndex: 'schedulePeriodStatus',
      width: 135,
      render: (status) => {
        const config = PERIOD_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
  ]

  const containsDraft = schedule.some(
    (item) => item.schedulePeriodStatus === 'DRAFT',
  )

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Lịch cá nhân</span>
          <h1>Lịch làm của tôi</h1>
          <p>Xem ca được phân công, địa điểm làm việc và tổng giờ trong khoảng thời gian đã chọn.</p>
        </div>
      </section>

      {containsDraft && (
        <Alert
          className="my-schedule-draft-alert"
          message="Bạn đang có ca thuộc lịch nháp"
          description="Lịch nháp vẫn có thể được quản lý điều chỉnh trước khi công bố chính thức."
          showIcon
          type="info"
        />
      )}

      <section className="management-card my-schedule-filter-panel">
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
        <Button icon={<ReloadOutlined />} onClick={applyDateRange}>
          Xem lịch
        </Button>
      </section>

      <section className="my-schedule-summary-grid">
        <article className="management-card my-schedule-summary-card">
          <CalendarOutlined />
          <div><span>Tổng số ca</span><strong>{summary.totalShifts}</strong></div>
        </article>
        <article className="management-card my-schedule-summary-card">
          <ClockCircleOutlined />
          <div><span>Tổng giờ dự kiến</span><strong>{formatWorkHours(summary.totalMinutes)}</strong></div>
        </article>
        <article className="management-card my-schedule-summary-card">
          <ShopOutlined />
          <div><span>Ca đã công bố</span><strong>{summary.publishedShifts}</strong></div>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Chi tiết lịch làm</strong>
            <span>{schedule.length} ca từ {formatDate(startDate)} đến {formatDate(endDate)}</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={schedule}
          loading={loading}
          locale={{ emptyText: 'Bạn chưa được phân công ca nào trong khoảng này.' }}
          pagination={{ pageSize: 12, showSizeChanger: false }}
          rowKey="assignmentId"
          scroll={{ x: 850 }}
        />
      </section>
    </>
  )
}
