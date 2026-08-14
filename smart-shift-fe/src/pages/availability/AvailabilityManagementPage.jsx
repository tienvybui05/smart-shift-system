import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Input,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import AvailabilityFormDrawer from '../../components/availability/AvailabilityFormDrawer.jsx'
import useAuth from '../../hooks/useAuth.js'
import {
  createMyAvailability,
  deleteMyAvailability,
  getMyAvailabilities,
  getUserAvailabilities,
  updateMyAvailability,
} from '../../services/availabilityService.js'
import { getUsers } from '../../services/userService.js'

const TYPE_CONFIG = {
  AVAILABLE: { label: 'Có thể làm', color: 'blue' },
  PREFERRED: { label: 'Ưu tiên muốn làm', color: 'success' },
  UNAVAILABLE: { label: 'Không thể làm', color: 'error' },
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
  end.setDate(start.getDate() + 13)
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

export default function AvailabilityManagementPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const [messageApi, messageContext] = message.useMessage()
  const defaultRange = getDefaultRange()
  const [viewMode, setViewMode] = useState('mine')
  const [startDate, setStartDate] = useState(defaultRange.startDate)
  const [endDate, setEndDate] = useState(defaultRange.endDate)
  const [dateInputs, setDateInputs] = useState(defaultRange)
  const [selectedUserId, setSelectedUserId] = useState(undefined)
  const [employees, setEmployees] = useState([])
  const [employeesLoading, setEmployeesLoading] = useState(isAdmin)
  const [availabilities, setAvailabilities] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingAvailability, setEditingAvailability] = useState(null)
  const [deletingId, setDeletingId] = useState(null)

  useEffect(() => {
    if (!isAdmin) return undefined
    let mounted = true
    getUsers({ page: 0, size: 100, active: true })
      .then((result) => {
        if (mounted) setEmployees(result.content)
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách nhân viên.'))
        }
      })
      .finally(() => {
        if (mounted) setEmployeesLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [isAdmin, messageApi])

  useEffect(() => {
    if (viewMode === 'employee' && !selectedUserId) return undefined
    let mounted = true
    const params = { startDate, endDate }
    const request = viewMode === 'mine'
      ? getMyAvailabilities(params)
      : getUserAvailabilities(selectedUserId, params)

    request
      .then((result) => {
        if (mounted) setAvailabilities(result)
      })
      .catch((error) => {
        if (mounted) {
          setAvailabilities([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải lịch rảnh.'))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [endDate, messageApi, refreshKey, selectedUserId, startDate, viewMode])

  function changeViewMode(mode) {
    setViewMode(mode)
    setAvailabilities([])
    setLoading(mode === 'mine' || Boolean(selectedUserId))
  }

  function openCreateDrawer() {
    setEditingAvailability(null)
    setDrawerOpen(true)
  }

  function openEditDrawer(availability) {
    setEditingAvailability(availability)
    setDrawerOpen(true)
  }

  async function handleSave(payload) {
    if (editingAvailability) {
      await updateMyAvailability(editingAvailability.id, payload)
      messageApi.success('Đã cập nhật lịch rảnh.')
    } else {
      await createMyAvailability(payload)
      messageApi.success('Đã đăng ký lịch rảnh.')
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleDelete(availability) {
    setDeletingId(availability.id)
    try {
      await deleteMyAvailability(availability.id)
      messageApi.success('Đã xóa khung giờ đăng ký.')
      setLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể xóa lịch rảnh.'))
    } finally {
      setDeletingId(null)
    }
  }

  function applyDateRange() {
    if (dateInputs.endDate < dateInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }
    setStartDate(dateInputs.startDate)
    setEndDate(dateInputs.endDate)
    setLoading(viewMode === 'mine' || Boolean(selectedUserId))
    setRefreshKey((current) => current + 1)
  }

  const typeCounts = availabilities.reduce((counts, availability) => ({
    ...counts,
    [availability.availabilityType]: (counts[availability.availabilityType] || 0) + 1,
  }), {})

  const columns = [
    {
      title: 'Ngày đăng ký',
      dataIndex: 'availableDate',
      width: 210,
      render: (value) => <strong className="availability-date">{formatDate(value)}</strong>,
    },
    ...(viewMode === 'employee' ? [{
      title: 'Nhân viên',
      key: 'employee',
      width: 190,
      render: (_, availability) => (
        <div className="employee-cell">
          <strong>{availability.fullName}</strong>
          <span>{availability.employeeCode} · {availability.positionName}</span>
        </div>
      ),
    }] : []),
    {
      title: 'Khung giờ',
      key: 'time',
      width: 150,
      render: (_, availability) => (
        <strong>{formatTime(availability.startTime)} – {formatTime(availability.endTime)}</strong>
      ),
    },
    {
      title: 'Khả năng làm việc',
      dataIndex: 'availabilityType',
      width: 165,
      render: (type) => {
        const config = TYPE_CONFIG[type] || { label: type, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      ellipsis: true,
      responsive: ['md'],
      render: (note) => note || <span className="empty-value">Không có</span>,
    },
    ...(viewMode === 'mine' ? [{
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 105,
      align: 'center',
      render: (_, availability) => (
        <Space size={2}>
          <Tooltip title={availability.editable ? 'Cập nhật' : 'Không thể sửa lịch quá khứ'}>
            <span>
              <Button
                disabled={!availability.editable}
                type="text"
                icon={<EditOutlined />}
                onClick={() => openEditDrawer(availability)}
              />
            </span>
          </Tooltip>
          <Popconfirm
            cancelText="Hủy"
            disabled={!availability.editable}
            okButtonProps={{ danger: true }}
            okText="Xóa"
            onConfirm={() => handleDelete(availability)}
            title="Xóa khung giờ đăng ký này?"
          >
            <Tooltip title="Xóa">
              <span>
                <Button
                  danger
                  disabled={!availability.editable}
                  loading={deletingId === availability.id}
                  type="text"
                  icon={<DeleteOutlined />}
                />
              </span>
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    }] : []),
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Chuẩn bị xếp lịch</span>
          <h1>Lịch rảnh</h1>
          <p>Đăng ký thời gian có thể làm, mong muốn được làm hoặc không thể nhận ca.</p>
        </div>
        {viewMode === 'mine' && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>
            Đăng ký khung giờ
          </Button>
        )}
      </section>

      {isAdmin && (
        <Segmented
          className="availability-view-switch"
          block
          onChange={changeViewMode}
          options={[
            { label: 'Lịch của tôi', value: 'mine' },
            { label: 'Xem theo nhân viên', value: 'employee' },
          ]}
          value={viewMode}
        />
      )}

      <section className="management-card availability-filter-panel">
        {viewMode === 'employee' && (
          <Select
            allowClear
            loading={employeesLoading}
            onChange={(value) => {
              setSelectedUserId(value)
              setAvailabilities([])
              setLoading(Boolean(value))
            }}
            options={employees.map((employee) => ({
              label: `${employee.employeeCode} — ${employee.fullName} (${employee.positionName})`,
              value: employee.id,
            }))}
            placeholder="Chọn nhân viên"
            showSearch
            optionFilterProp="label"
            value={selectedUserId}
          />
        )}
        <Input
          type="date"
          value={dateInputs.startDate}
          onChange={(event) => setDateInputs((current) => ({
            ...current,
            startDate: event.target.value,
          }))}
        />
        <Input
          type="date"
          value={dateInputs.endDate}
          onChange={(event) => setDateInputs((current) => ({
            ...current,
            endDate: event.target.value,
          }))}
        />
        <Button icon={<ReloadOutlined />} onClick={applyDateRange}>Xem lịch</Button>
      </section>

      {viewMode === 'employee' && !selectedUserId && (
        <Alert
          message="Chọn một nhân viên để xem lịch rảnh"
          description="Admin chỉ xem dữ liệu đăng ký; nhân viên tự chịu trách nhiệm cập nhật lịch của mình."
          showIcon
          type="info"
        />
      )}

      <section className="availability-summary-grid">
        {Object.entries(TYPE_CONFIG).map(([type, config]) => (
          <div className={`availability-summary-card availability-summary-card--${type.toLowerCase()}`} key={type}>
            <span>{config.label}</span>
            <strong>{typeCounts[type] || 0}</strong>
            <small>khung giờ</small>
          </div>
        ))}
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách khung giờ</strong>
            <span>{availabilities.length} đăng ký trong khoảng đã chọn</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={availabilities}
          loading={loading}
          locale={{ emptyText: viewMode === 'employee' && !selectedUserId
            ? 'Chưa chọn nhân viên.'
            : 'Chưa có lịch rảnh trong khoảng này.' }}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 850 }}
        />
      </section>

      <AvailabilityFormDrawer
        open={drawerOpen}
        availability={editingAvailability}
        onClose={() => setDrawerOpen(false)}
        onSubmit={handleSave}
      />
    </>
  )
}
