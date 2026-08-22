import {
  CalendarOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Alert, Button, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/apiError.js'
import SchedulePeriodFormDrawer from '../../components/schedules/SchedulePeriodFormDrawer.jsx'
import AutoScheduleModal from '../../components/schedules/AutoScheduleModal.jsx'
import SchedulePublicationModal from '../../components/schedules/SchedulePublicationModal.jsx'
import { getLocations } from '../../services/referenceService.js'
import {
  createSchedulePeriod,
  getSchedulePeriods,
  lockSchedulePeriod,
  publishSchedulePeriod,
  updateSchedulePeriod,
} from '../../services/schedulePeriodService.js'

const STATUS_CONFIG = {
  DRAFT: { label: 'Nháp', color: 'default' },
  GENERATING: { label: 'Đang xếp lịch', color: 'processing' },
  PUBLISHED: { label: 'Đã công bố', color: 'success' },
  LOCKED: { label: 'Đã khóa', color: 'purple' },
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

export default function SchedulePeriodManagementPage() {
  const navigate = useNavigate()
  const [messageApi, messageContext] = message.useMessage()
  const [schedulePeriods, setSchedulePeriods] = useState([])
  const [locations, setLocations] = useState([])
  const [locationsLoading, setLocationsLoading] = useState(true)
  const [locationError, setLocationError] = useState('')
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState({ locationId: undefined, status: undefined })
  const [refreshKey, setRefreshKey] = useState(0)
  const [locationRefreshKey, setLocationRefreshKey] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingSchedulePeriod, setEditingSchedulePeriod] = useState(null)
  const [autoSchedulePeriod, setAutoSchedulePeriod] = useState(null)
  const [publishingSchedulePeriod, setPublishingSchedulePeriod] = useState(null)
  const [lockingId, setLockingId] = useState(null)

  useEffect(() => {
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
  }, [locationRefreshKey])

  useEffect(() => {
    let mounted = true
    getSchedulePeriods(filters)
      .then((result) => {
        if (mounted) setSchedulePeriods(result)
      })
      .catch((error) => {
        if (mounted) {
          setSchedulePeriods([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách kỳ xếp lịch.'))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [filters, messageApi, refreshKey])

  function applyFilter(name, value) {
    setLoading(true)
    setFilters((current) => ({ ...current, [name]: value }))
  }

  function resetFilters() {
    setLoading(true)
    setFilters({ locationId: undefined, status: undefined })
  }

  function openCreateDrawer() {
    setEditingSchedulePeriod(null)
    setDrawerOpen(true)
  }

  function openEditDrawer(schedulePeriod) {
    setEditingSchedulePeriod(schedulePeriod)
    setDrawerOpen(true)
  }

  async function handleSave(payload) {
    if (editingSchedulePeriod) {
      await updateSchedulePeriod(editingSchedulePeriod.id, payload)
      messageApi.success('Đã cập nhật kỳ xếp lịch.')
    } else {
      await createSchedulePeriod(payload)
      messageApi.success('Đã tạo kỳ xếp lịch mới.')
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handlePublish(id) {
    await publishSchedulePeriod(id)
    messageApi.success('Đã công bố lịch làm việc chính thức.')
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  function handleAutoScheduleGenerated(result) {
    const created = result.assignmentsCreated
    messageApi.success(created > 0
      ? `Đã lưu ${created} phân công tự động.`
      : 'Lần chạy hoàn tất, không có phân công mới.')
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleLock(schedulePeriod) {
    setLockingId(schedulePeriod.id)
    try {
      await lockSchedulePeriod(schedulePeriod.id)
      messageApi.success('Đã khóa kỳ xếp lịch.')
      setLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể khóa kỳ xếp lịch.'))
    } finally {
      setLockingId(null)
    }
  }

  function retryLocations() {
    setLocationsLoading(true)
    setLocationError('')
    setLocationRefreshKey((current) => current + 1)
  }

  const columns = [
    {
      title: 'Kỳ xếp lịch',
      key: 'period',
      width: 250,
      render: (_, schedulePeriod) => (
        <div className="employee-cell">
          <strong>{schedulePeriod.name}</strong>
          <span>Tạo bởi {schedulePeriod.createdByName}</span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      key: 'location',
      width: 210,
      render: (_, schedulePeriod) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{schedulePeriod.locationName}</strong>
          <span>{schedulePeriod.locationCode}</span>
        </div>
      ),
    },
    {
      title: 'Khoảng ngày',
      key: 'dateRange',
      width: 225,
      render: (_, schedulePeriod) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{formatDate(schedulePeriod.startDate)} – {formatDate(schedulePeriod.endDate)}</strong>
          <span>{schedulePeriod.totalDays} ngày lập lịch</span>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 145,
      render: (status, schedulePeriod) => {
        const config = STATUS_CONFIG[status] || { label: status, color: 'default' }
        return (
          <div className="employee-cell employee-cell--normal">
            <span><Tag color={config.color}>{config.label}</Tag></span>
            {schedulePeriod.publishedByName && (
              <small>Công bố bởi {schedulePeriod.publishedByName}</small>
            )}
          </div>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 220,
      align: 'center',
      render: (_, schedulePeriod) => (
        <Space size={2}>
          <Tooltip title="Quản lý ca làm">
            <Button
              type="text"
              icon={<CalendarOutlined />}
              onClick={() => navigate(`/admin/work-shifts?periodId=${schedulePeriod.id}`)}
            />
          </Tooltip>
          <Tooltip title={schedulePeriod.editable ? 'Cập nhật' : 'Chỉ kỳ Nháp mới được cập nhật'}>
            <span>
              <Button
                disabled={!schedulePeriod.editable}
                type="text"
                icon={<EditOutlined />}
                onClick={() => openEditDrawer(schedulePeriod)}
              />
            </span>
          </Tooltip>
          {schedulePeriod.status === 'DRAFT' && (
            <>
              <Tooltip title="Tự động xếp lịch">
                <Button
                  type="text"
                  icon={<ThunderboltOutlined />}
                  onClick={() => setAutoSchedulePeriod(schedulePeriod)}
                />
              </Tooltip>
              <Tooltip title="Kiểm tra và công bố lịch">
                <Button
                  type="text"
                  icon={<SendOutlined />}
                  onClick={() => setPublishingSchedulePeriod(schedulePeriod)}
                />
              </Tooltip>
            </>
          )}
          {schedulePeriod.status === 'PUBLISHED' && (
            <Popconfirm
              cancelText="Hủy"
              okText="Khóa lịch"
              onConfirm={() => handleLock(schedulePeriod)}
              title="Khóa kỳ xếp lịch này?"
              description="Kỳ đã khóa được xem là dữ liệu cuối cùng để chấm công và tính lương."
            >
              <Tooltip title="Khóa kỳ lịch">
                <Button
                  type="text"
                  icon={<LockOutlined />}
                  loading={lockingId === schedulePeriod.id}
                />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Quản trị lịch làm việc</span>
          <h1>Kỳ xếp lịch</h1>
          <p>Tạo khoảng thời gian lập lịch theo từng chi nhánh trước khi sinh các ca làm cụ thể.</p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={openCreateDrawer}
          disabled={locationsLoading || Boolean(locationError)}
        >
          Tạo kỳ xếp lịch
        </Button>
      </section>

      {locationError && (
        <Alert
          action={<Button size="small" onClick={retryLocations}>Thử lại</Button>}
          message={locationError}
          showIcon
          type="error"
        />
      )}

      <section className="management-card shift-filter-panel">
        <Select
          allowClear
          loading={locationsLoading}
          onChange={(value) => applyFilter('locationId', value)}
          options={locations.map((location) => ({
            label: `${location.code} — ${location.name}`,
            value: location.id,
          }))}
          placeholder="Tất cả chi nhánh"
          showSearch
          optionFilterProp="label"
          value={filters.locationId}
        />
        <Select
          allowClear
          onChange={(value) => applyFilter('status', value)}
          options={Object.entries(STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái"
          value={filters.status}
        />
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách kỳ xếp lịch</strong>
            <span>{schedulePeriods.length} kỳ phù hợp bộ lọc</span>
          </div>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => {
              setLoading(true)
              setRefreshKey((current) => current + 1)
            }}
          >
            Làm mới
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={schedulePeriods}
          loading={loading}
          locale={{ emptyText: 'Chưa có kỳ xếp lịch. Hãy tạo kỳ đầu tiên.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 900 }}
        />
      </section>

      <SchedulePeriodFormDrawer
        open={drawerOpen}
        schedulePeriod={editingSchedulePeriod}
        locations={locations}
        onClose={() => setDrawerOpen(false)}
        onSubmit={handleSave}
      />

      {autoSchedulePeriod && (
        <AutoScheduleModal
          schedulePeriod={autoSchedulePeriod}
          onClose={() => setAutoSchedulePeriod(null)}
          onGenerated={handleAutoScheduleGenerated}
          onManageShifts={(id) => navigate(`/admin/work-shifts?periodId=${id}`)}
        />
      )}

      <SchedulePublicationModal
        open={Boolean(publishingSchedulePeriod)}
        schedulePeriod={publishingSchedulePeriod}
        onClose={() => setPublishingSchedulePeriod(null)}
        onManageShifts={(id) => navigate(`/admin/work-shifts?periodId=${id}`)}
        onPublish={handlePublish}
      />
    </>
  )
}
