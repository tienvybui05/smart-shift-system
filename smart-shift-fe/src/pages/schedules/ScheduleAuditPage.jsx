import {
  AuditOutlined,
  CalendarOutlined,
  EyeOutlined,
  HistoryOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Button, Input, Modal, Select, Table, Tag, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import useAuth from '../../hooks/useAuth.js'
import { getLocations } from '../../services/referenceService.js'
import { getScheduleAuditLogs } from '../../services/scheduleAuditService.js'
import { getSchedulePeriods } from '../../services/schedulePeriodService.js'

const ACTIONS = {
  CREATED: { label: 'Tạo mới', color: 'green' },
  UPDATED: { label: 'Cập nhật', color: 'blue' },
  STATUS_CHANGED: { label: 'Đổi trạng thái', color: 'orange' },
  GENERATED: { label: 'Sinh ca hàng loạt', color: 'cyan' },
  REQUIREMENTS_CHANGED: { label: 'Sửa nhu cầu', color: 'purple' },
  ASSIGNED: { label: 'Phân công', color: 'geekblue' },
  UNASSIGNED: { label: 'Gỡ phân công', color: 'red' },
  AUTO_SCHEDULED: { label: 'Xếp lịch tự động', color: 'gold' },
  PUBLISHED: { label: 'Công bố lịch', color: 'success' },
  LOCKED: { label: 'Khóa lịch', color: 'default' },
}

const TARGET_LABELS = {
  SCHEDULE_PERIOD: 'Kỳ xếp lịch',
  WORK_SHIFT: 'Ca làm',
  SHIFT_REQUIREMENT: 'Nhu cầu nhân sự',
  SHIFT_ASSIGNMENT: 'Phân công',
}

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultRange() {
  const endDate = new Date()
  const startDate = new Date(endDate)
  startDate.setDate(endDate.getDate() - 29)
  return {
    startDate: toDateInputValue(startDate),
    endDate: toDateInputValue(endDate),
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function prettySnapshot(value) {
  if (!value) return 'Không có dữ liệu'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export default function ScheduleAuditPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const defaultRange = getDefaultRange()
  const [messageApi, messageContext] = message.useMessage()
  const [records, setRecords] = useState([])
  const [locations, setLocations] = useState(() => (
    isAdmin ? [] : [{ id: user.locationId, name: user.locationName }]
  ))
  const [periods, setPeriods] = useState([])
  const [loading, setLoading] = useState(true)
  const [selectedRecord, setSelectedRecord] = useState(null)
  const [filterInputs, setFilterInputs] = useState({
    ...defaultRange,
    locationId: isAdmin ? undefined : user.locationId,
    schedulePeriodId: undefined,
    action: undefined,
  })
  const [filters, setFilters] = useState(filterInputs)

  useEffect(() => {
    if (!isAdmin) return undefined
    let mounted = true
    getLocations()
      .then((result) => {
        if (mounted) setLocations(result.filter((location) => location.active))
      })
      .catch((error) => {
        if (mounted) messageApi.error(getApiErrorMessage(error, 'Không thể tải chi nhánh.'))
      })
    return () => {
      mounted = false
    }
  }, [isAdmin, messageApi])

  useEffect(() => {
    let mounted = true
    getSchedulePeriods({ locationId: filterInputs.locationId })
      .then((result) => {
        if (mounted) setPeriods(result)
      })
      .catch(() => {
        if (mounted) setPeriods([])
      })
    return () => {
      mounted = false
    }
  }, [filterInputs.locationId])

  useEffect(() => {
    let mounted = true
    getScheduleAuditLogs(filters)
      .then((result) => {
        if (mounted) setRecords(result)
      })
      .catch((error) => {
        if (!mounted) return
        setRecords([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải lịch sử thay đổi.'))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [filters, messageApi])

  const summary = useMemo(() => ({
    total: records.length,
    actors: new Set(records.map((item) => item.actorId)).size,
    periods: new Set(records.map((item) => item.schedulePeriodId)).size,
    assignments: records.filter((item) => (
      item.action === 'ASSIGNED' || item.action === 'UNASSIGNED'
    )).length,
  }), [records])

  function applyFilters() {
    if (!filterInputs.startDate || !filterInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày.')
      return
    }
    if (filterInputs.endDate < filterInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }
    const days = (
      new Date(`${filterInputs.endDate}T00:00:00`)
      - new Date(`${filterInputs.startDate}T00:00:00`)
    ) / 86400000
    if (days >= 93) {
      messageApi.error('Chỉ được xem lịch sử trong khoảng tối đa 93 ngày.')
      return
    }
    setLoading(true)
    setFilters({ ...filterInputs })
  }

  const columns = [
    { title: 'Thời gian', dataIndex: 'createdAt', width: 175, render: formatDateTime },
    {
      title: 'Người thao tác',
      key: 'actor',
      width: 190,
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{record.actorName}</strong>
          <span>ID tài khoản #{record.actorId}</span>
        </div>
      ),
    },
    {
      title: 'Thao tác',
      dataIndex: 'action',
      width: 155,
      render: (action) => {
        const config = ACTIONS[action] || { label: action, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Đối tượng',
      key: 'target',
      width: 155,
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{TARGET_LABELS[record.targetType] || record.targetType}</strong>
          <span>#{record.targetId}</span>
        </div>
      ),
    },
    {
      title: 'Kỳ xếp lịch',
      key: 'period',
      width: 230,
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{record.schedulePeriodName}</strong>
          <span>{record.locationName}</span>
        </div>
      ),
    },
    { title: 'Lý do', dataIndex: 'reason', width: 260, ellipsis: true },
    {
      title: 'Chi tiết',
      key: 'details',
      fixed: 'right',
      width: 90,
      align: 'center',
      render: (_, record) => (
        <Button
          aria-label="Xem dữ liệu trước và sau"
          icon={<EyeOutlined />}
          onClick={() => setSelectedRecord(record)}
          type="text"
        />
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Theo dõi thay đổi</span>
          <h1>Lịch sử thay đổi lịch</h1>
          <p>Tra cứu ai đã thay đổi nội dung gì, vào lúc nào và vì lý do nào.</p>
        </div>
      </section>

      <Alert
        className="schedule-audit-alert"
        description="Nhật ký được tạo tự động cùng giao dịch thay đổi lịch và không thể chỉnh sửa từ giao diện."
        message="Dữ liệu audit bất biến"
        showIcon
        type="info"
      />

      <section className="management-card schedule-audit-filters">
        <Input aria-label="Từ ngày" max={filterInputs.endDate} type="date" value={filterInputs.startDate} onChange={(event) => setFilterInputs((current) => ({ ...current, startDate: event.target.value }))} />
        <Input aria-label="Đến ngày" min={filterInputs.startDate} type="date" value={filterInputs.endDate} onChange={(event) => setFilterInputs((current) => ({ ...current, endDate: event.target.value }))} />
        <Select
          allowClear={isAdmin}
          disabled={!isAdmin}
          onChange={(value) => setFilterInputs((current) => ({ ...current, locationId: value, schedulePeriodId: undefined }))}
          optionFilterProp="label"
          options={locations.map((location) => ({ label: location.code ? `${location.code} — ${location.name}` : location.name, value: location.id }))}
          placeholder="Tất cả chi nhánh"
          showSearch
          value={filterInputs.locationId}
        />
        <Select
          allowClear
          onChange={(value) => setFilterInputs((current) => ({ ...current, schedulePeriodId: value }))}
          optionFilterProp="label"
          options={periods.map((period) => ({ label: period.name, value: period.id }))}
          placeholder="Tất cả kỳ lịch"
          showSearch
          value={filterInputs.schedulePeriodId}
        />
        <Select
          allowClear
          onChange={(value) => setFilterInputs((current) => ({ ...current, action: value }))}
          options={Object.entries(ACTIONS).map(([value, config]) => ({ label: config.label, value }))}
          placeholder="Tất cả thao tác"
          value={filterInputs.action}
        />
        <Button onClick={applyFilters} type="primary">Tra cứu</Button>
      </section>

      <section className="attendance-management-summary-grid">
        <article className="management-card attendance-summary-card"><HistoryOutlined /><div><span>Tổng thay đổi</span><strong>{summary.total}</strong></div></article>
        <article className="management-card attendance-summary-card"><TeamOutlined /><div><span>Người thao tác</span><strong>{summary.actors}</strong></div></article>
        <article className="management-card attendance-summary-card"><CalendarOutlined /><div><span>Kỳ lịch liên quan</span><strong>{summary.periods}</strong></div></article>
        <article className="management-card attendance-summary-card"><AuditOutlined /><div><span>Đổi phân công</span><strong>{summary.assignments}</strong></div></article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading"><div><strong>Nhật ký thay đổi</strong><span>{records.length} bản ghi phù hợp bộ lọc</span></div></div>
        <Table columns={columns} dataSource={records} loading={loading} locale={{ emptyText: 'Chưa có thay đổi lịch trong khoảng đã chọn.' }} pagination={{ pageSize: 12, showSizeChanger: false }} rowKey="id" scroll={{ x: 1300 }} />
      </section>

      <Modal footer={null} onCancel={() => setSelectedRecord(null)} open={Boolean(selectedRecord)} title="Chi tiết dữ liệu trước và sau" width={920}>
        {selectedRecord && (
          <>
            <div className="schedule-audit-detail-heading">
              <Tag color={ACTIONS[selectedRecord.action]?.color}>{ACTIONS[selectedRecord.action]?.label || selectedRecord.action}</Tag>
              <strong>{selectedRecord.reason}</strong>
              <span>{selectedRecord.actorName} · {formatDateTime(selectedRecord.createdAt)}</span>
            </div>
            <div className="schedule-audit-diff-grid">
              <section><strong>Trước thay đổi</strong><pre>{prettySnapshot(selectedRecord.beforeData)}</pre></section>
              <section><strong>Sau thay đổi</strong><pre>{prettySnapshot(selectedRecord.afterData)}</pre></section>
            </div>
          </>
        )}
      </Modal>
    </>
  )
}
