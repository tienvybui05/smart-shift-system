import {
  CheckCircleOutlined,
  EditOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, Button, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import ShiftTemplateFormDrawer from '../../components/shifts/ShiftTemplateFormDrawer.jsx'
import useAuth from '../../hooks/useAuth.js'
import { getLocations } from '../../services/referenceService.js'
import {
  createShiftTemplate,
  getShiftTemplates,
  updateShiftTemplate,
  updateShiftTemplateStatus,
} from '../../services/shiftTemplateService.js'

function formatTime(value) {
  return value?.slice(0, 5) || '--:--'
}

function formatDuration(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (!hours) return `${minutes} phút`
  return minutes ? `${hours} giờ ${minutes} phút` : `${hours} giờ`
}

function getManagerLocation(user) {
  return {
    id: user.locationId,
    code: '',
    name: user.locationName,
    active: true,
  }
}

export default function ShiftTemplateManagementPage() {
  const { user } = useAuth()
  const managerScoped = user.role === 'ROLE_MANAGER'
  const [messageApi, messageContext] = message.useMessage()
  const [shiftTemplates, setShiftTemplates] = useState([])
  const [locations, setLocations] = useState(() => (
    managerScoped ? [getManagerLocation(user)] : []
  ))
  const [locationsLoading, setLocationsLoading] = useState(!managerScoped)
  const [locationError, setLocationError] = useState('')
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState({
    locationId: managerScoped ? user.locationId : undefined,
    active: undefined,
  })
  const [refreshKey, setRefreshKey] = useState(0)
  const [locationRefreshKey, setLocationRefreshKey] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingShiftTemplate, setEditingShiftTemplate] = useState(null)
  const [statusChangingId, setStatusChangingId] = useState(null)

  useEffect(() => {
    if (managerScoped) return undefined

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
  }, [locationRefreshKey, managerScoped])

  useEffect(() => {
    let mounted = true
    getShiftTemplates(filters)
      .then((result) => {
        if (mounted) setShiftTemplates(result)
      })
      .catch((error) => {
        if (mounted) {
          setShiftTemplates([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách mẫu ca.'))
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
    setFilters({
      locationId: managerScoped ? user.locationId : undefined,
      active: undefined,
    })
  }

  function openCreateDrawer() {
    setEditingShiftTemplate(null)
    setDrawerOpen(true)
  }

  function openEditDrawer(shiftTemplate) {
    setEditingShiftTemplate(shiftTemplate)
    setDrawerOpen(true)
  }

  async function handleSave(payload) {
    if (editingShiftTemplate) {
      await updateShiftTemplate(editingShiftTemplate.id, payload)
      messageApi.success('Đã cập nhật mẫu ca.')
    } else {
      await createShiftTemplate(payload)
      messageApi.success('Đã tạo mẫu ca mới.')
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleChangeStatus(shiftTemplate) {
    setStatusChangingId(shiftTemplate.id)
    try {
      await updateShiftTemplateStatus(shiftTemplate.id, !shiftTemplate.active)
      messageApi.success(
        shiftTemplate.active ? 'Đã ngừng sử dụng mẫu ca.' : 'Đã kích hoạt mẫu ca.',
      )
      setLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể thay đổi trạng thái mẫu ca.'))
    } finally {
      setStatusChangingId(null)
    }
  }

  function retryLocations() {
    setLocationsLoading(true)
    setLocationError('')
    setLocationRefreshKey((current) => current + 1)
  }

  const columns = [
    {
      title: 'Mẫu ca',
      key: 'shiftTemplate',
      width: 190,
      render: (_, shiftTemplate) => (
        <div className="shift-template-cell">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: shiftTemplate.colorCode || '#5B4CF0' }}
          />
          <strong>{shiftTemplate.name}</strong>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      key: 'location',
      width: 210,
      render: (_, shiftTemplate) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{shiftTemplate.locationName}</strong>
          <span>{shiftTemplate.locationCode}</span>
        </div>
      ),
    },
    {
      title: 'Khung giờ',
      key: 'time',
      width: 190,
      render: (_, shiftTemplate) => (
        <div className="shift-time-cell">
          <strong>
            {formatTime(shiftTemplate.startTime)} – {formatTime(shiftTemplate.endTime)}
          </strong>
          {shiftTemplate.overnight && <Tag color="geekblue">Qua ngày</Tag>}
        </div>
      ),
    },
    {
      title: 'Thời lượng',
      key: 'duration',
      width: 170,
      responsive: ['md'],
      render: (_, shiftTemplate) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{formatDuration(shiftTemplate.durationMinutes)}</strong>
          <span>Nghỉ {shiftTemplate.breakMinutes} phút</span>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 125,
      render: (active) => (
        <Tag color={active ? 'success' : 'default'}>
          {active ? 'Hoạt động' : 'Ngừng dùng'}
        </Tag>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 110,
      align: 'center',
      render: (_, shiftTemplate) => (
        <Space size={2}>
          <Tooltip title="Cập nhật">
            <Button
              type="text"
              icon={<EditOutlined />}
              onClick={() => openEditDrawer(shiftTemplate)}
            />
          </Tooltip>
          <Popconfirm
            cancelText="Hủy"
            description={shiftTemplate.active
              ? 'Mẫu ca sẽ không còn được chọn khi tạo lịch mới.'
              : 'Mẫu ca có thể được dùng lại khi tạo lịch.'}
            okButtonProps={{ danger: shiftTemplate.active }}
            okText={shiftTemplate.active ? 'Ngừng dùng' : 'Kích hoạt'}
            onConfirm={() => handleChangeStatus(shiftTemplate)}
            title={shiftTemplate.active ? 'Ngừng sử dụng mẫu ca?' : 'Kích hoạt mẫu ca?'}
          >
            <Tooltip title={shiftTemplate.active ? 'Ngừng sử dụng' : 'Kích hoạt'}>
              <Button
                danger={shiftTemplate.active}
                loading={statusChangingId === shiftTemplate.id}
                type="text"
                icon={shiftTemplate.active
                  ? <PauseCircleOutlined />
                  : <CheckCircleOutlined />}
              />
            </Tooltip>
          </Popconfirm>
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
          <h1>Quản lý mẫu ca</h1>
          <p>Khai báo ca sáng, ca chiều, ca tối và các khung giờ áp dụng riêng cho từng chi nhánh.</p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={openCreateDrawer}
          disabled={locationsLoading || Boolean(locationError)}
        >
          Thêm mẫu ca
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
          allowClear={!managerScoped}
          disabled={managerScoped}
          loading={locationsLoading}
          onChange={(value) => applyFilter('locationId', value)}
          options={locations.map((location) => ({
            label: location.code
              ? `${location.code} — ${location.name}`
              : location.name,
            value: location.id,
          }))}
          placeholder="Tất cả chi nhánh"
          showSearch
          optionFilterProp="label"
          value={filters.locationId}
        />
        <Select
          allowClear
          onChange={(value) => applyFilter('active', value)}
          options={[
            { label: 'Đang hoạt động', value: true },
            { label: 'Ngừng sử dụng', value: false },
          ]}
          placeholder="Tất cả trạng thái"
          value={filters.active}
        />
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách mẫu ca</strong>
            <span>{shiftTemplates.length} mẫu ca phù hợp bộ lọc</span>
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
          dataSource={shiftTemplates}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1000 }}
        />
      </section>

      <ShiftTemplateFormDrawer
        open={drawerOpen}
        shiftTemplate={editingShiftTemplate}
        locations={locations}
        locationLocked={managerScoped}
        onClose={() => setDrawerOpen(false)}
        onSubmit={handleSave}
      />
    </>
  )
}
