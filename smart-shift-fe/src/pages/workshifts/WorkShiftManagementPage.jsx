import {
  EditOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { Alert, Button, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/apiError.js'
import ShiftAssignmentDrawer from '../../components/assignments/ShiftAssignmentDrawer.jsx'
import ShiftRequirementDrawer from '../../components/requirements/ShiftRequirementDrawer.jsx'
import GenerateWorkShiftsModal from '../../components/workshifts/GenerateWorkShiftsModal.jsx'
import WorkShiftFormDrawer from '../../components/workshifts/WorkShiftFormDrawer.jsx'
import { getPositions } from '../../services/referenceService.js'
import { getSchedulePeriods } from '../../services/schedulePeriodService.js'
import { saveShiftRequirements } from '../../services/shiftRequirementService.js'
import { getShiftTemplates } from '../../services/shiftTemplateService.js'
import {
  createWorkShift,
  generateWorkShifts,
  getWorkShifts,
  updateWorkShift,
  updateWorkShiftStatus,
} from '../../services/workShiftService.js'

const STATUS_CONFIG = {
  OPEN: { label: 'Đang mở', color: 'processing' },
  FILLED: { label: 'Đã đủ người', color: 'success' },
  CANCELLED: { label: 'Đã hủy', color: 'error' },
  COMPLETED: { label: 'Hoàn thành', color: 'default' },
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

function formatTime(value) {
  return value?.slice(0, 5) || '--:--'
}

function formatDuration(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (!hours) return `${minutes} phút`
  return minutes ? `${hours} giờ ${minutes} phút` : `${hours} giờ`
}

export default function WorkShiftManagementPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialPeriodId = Number(searchParams.get('periodId')) || undefined
  const [messageApi, messageContext] = message.useMessage()
  const [schedulePeriods, setSchedulePeriods] = useState([])
  const [positions, setPositions] = useState([])
  const [positionsLoading, setPositionsLoading] = useState(true)
  const [periodsLoading, setPeriodsLoading] = useState(true)
  const [selectedPeriodId, setSelectedPeriodId] = useState(initialPeriodId)
  const [shiftTemplates, setShiftTemplates] = useState([])
  const [templatesLoading, setTemplatesLoading] = useState(Boolean(initialPeriodId))
  const [workShifts, setWorkShifts] = useState([])
  const [loading, setLoading] = useState(Boolean(initialPeriodId))
  const [statusFilter, setStatusFilter] = useState(undefined)
  const [refreshKey, setRefreshKey] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [generateModalOpen, setGenerateModalOpen] = useState(false)
  const [editingWorkShift, setEditingWorkShift] = useState(null)
  const [requirementWorkShift, setRequirementWorkShift] = useState(null)
  const [assignmentWorkShift, setAssignmentWorkShift] = useState(null)
  const [statusChangingId, setStatusChangingId] = useState(null)

  const selectedPeriod = schedulePeriods.find(
    (period) => period.id === selectedPeriodId,
  )
  const selectedLocationId = selectedPeriod?.locationId
  const periodEditable = selectedPeriod?.status === 'DRAFT'

  useEffect(() => {
    let mounted = true
    getSchedulePeriods()
      .then((result) => {
        if (mounted) setSchedulePeriods(result)
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách kỳ xếp lịch.'))
        }
      })
      .finally(() => {
        if (mounted) setPeriodsLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi])

  useEffect(() => {
    let mounted = true
    getPositions()
      .then((result) => {
        if (mounted) setPositions(result)
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh mục vị trí.'))
        }
      })
      .finally(() => {
        if (mounted) setPositionsLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi])

  useEffect(() => {
    if (!selectedLocationId) return undefined
    let mounted = true
    getShiftTemplates({ locationId: selectedLocationId, active: true })
      .then((result) => {
        if (mounted) setShiftTemplates(result)
      })
      .catch((error) => {
        if (mounted) {
          setShiftTemplates([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải mẫu ca của chi nhánh.'))
        }
      })
      .finally(() => {
        if (mounted) setTemplatesLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, selectedLocationId])

  useEffect(() => {
    if (!selectedPeriodId) return undefined
    let mounted = true
    getWorkShifts({ schedulePeriodId: selectedPeriodId, status: statusFilter })
      .then((result) => {
        if (mounted) setWorkShifts(result)
      })
      .catch((error) => {
        if (mounted) {
          setWorkShifts([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách ca làm.'))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, refreshKey, selectedPeriodId, statusFilter])

  function handlePeriodChange(periodId) {
    setSelectedPeriodId(periodId)
    setStatusFilter(undefined)
    setWorkShifts([])
    setShiftTemplates([])
    setLoading(Boolean(periodId))
    setTemplatesLoading(Boolean(periodId))
    if (periodId) {
      setSearchParams({ periodId: String(periodId) })
    } else {
      setSearchParams({})
    }
  }

  function openCreateDrawer() {
    setEditingWorkShift(null)
    setDrawerOpen(true)
  }

  function openEditDrawer(workShift) {
    setEditingWorkShift(workShift)
    setDrawerOpen(true)
  }

  async function handleSave(payload) {
    if (editingWorkShift) {
      await updateWorkShift(editingWorkShift.id, payload)
      messageApi.success('Đã cập nhật ca làm.')
    } else {
      await createWorkShift(payload)
      messageApi.success('Đã tạo ca làm mới.')
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleGenerate(payload) {
    const result = await generateWorkShifts(payload)
    if (result.skippedCount > 0) {
      messageApi.success(
        `Đã tạo ${result.createdCount} ca, bỏ qua ${result.skippedCount} ca bị trùng.`,
      )
    } else {
      messageApi.success(`Đã tạo ${result.createdCount} ca làm.`)
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleChangeStatus(workShift) {
    const targetStatus = workShift.status === 'CANCELLED' ? 'OPEN' : 'CANCELLED'
    setStatusChangingId(workShift.id)
    try {
      await updateWorkShiftStatus(workShift.id, targetStatus)
      messageApi.success(targetStatus === 'OPEN' ? 'Đã mở lại ca làm.' : 'Đã hủy ca làm.')
      setLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể thay đổi trạng thái ca làm.'))
    } finally {
      setStatusChangingId(null)
    }
  }

  async function handleSaveRequirements(payload) {
    const result = await saveShiftRequirements(requirementWorkShift.id, payload)
    if (result.affectedShiftCount > 1) {
      messageApi.success(
        `Đã lưu nhu cầu cho ${result.affectedShiftCount} ca cùng mẫu.`,
      )
    } else {
      messageApi.success('Đã lưu nhu cầu nhân sự cho ca.')
    }
  }

  const columns = [
    {
      title: 'Ngày làm việc',
      dataIndex: 'workDate',
      width: 175,
      render: (workDate) => <strong className="work-shift-date">{formatDate(workDate)}</strong>,
    },
    {
      title: 'Ca làm',
      key: 'shift',
      width: 180,
      render: (_, workShift) => (
        <div className="shift-template-cell">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: workShift.colorCode || '#5B4CF0' }}
          />
          <strong>{workShift.shiftTemplateName}</strong>
        </div>
      ),
    },
    {
      title: 'Khung giờ',
      key: 'time',
      width: 190,
      render: (_, workShift) => (
        <div className="shift-time-cell">
          <strong>{formatTime(workShift.startTime)} – {formatTime(workShift.endTime)}</strong>
          {workShift.overnight && <Tag color="geekblue">Qua ngày</Tag>}
        </div>
      ),
    },
    {
      title: 'Thời lượng',
      key: 'duration',
      width: 165,
      responsive: ['md'],
      render: (_, workShift) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{formatDuration(workShift.durationMinutes)}</strong>
          <span>Nghỉ {workShift.breakMinutes} phút</span>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (status) => {
        const config = STATUS_CONFIG[status] || { label: status, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 185,
      align: 'center',
      render: (_, workShift) => {
        const canChangeStatus = periodEditable
          && ['OPEN', 'CANCELLED'].includes(workShift.status)
        return (
          <Space size={2}>
            <Tooltip title="Phân công nhân viên">
              <span>
                <Button
                  disabled={!['OPEN', 'FILLED'].includes(workShift.status)}
                  type="text"
                  icon={<UserAddOutlined />}
                  onClick={() => setAssignmentWorkShift(workShift)}
                />
              </span>
            </Tooltip>
            <Tooltip title="Nhu cầu nhân sự">
              <span>
                <Button
                  disabled={positionsLoading}
                  type="text"
                  icon={<TeamOutlined />}
                  onClick={() => setRequirementWorkShift(workShift)}
                />
              </span>
            </Tooltip>
            <Tooltip title={workShift.status === 'OPEN' && periodEditable
              ? 'Cập nhật'
              : 'Chỉ có thể sửa ca đang mở trong kỳ Nháp'}>
              <span>
                <Button
                  disabled={!periodEditable || workShift.status !== 'OPEN'}
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => openEditDrawer(workShift)}
                />
              </span>
            </Tooltip>
            <Popconfirm
              cancelText="Hủy"
              disabled={!canChangeStatus}
              okButtonProps={{ danger: workShift.status === 'OPEN' }}
              okText={workShift.status === 'CANCELLED' ? 'Mở lại' : 'Hủy ca'}
              onConfirm={() => handleChangeStatus(workShift)}
              title={workShift.status === 'CANCELLED' ? 'Mở lại ca làm này?' : 'Hủy ca làm này?'}
            >
              <Tooltip title={workShift.status === 'CANCELLED' ? 'Mở lại' : 'Hủy ca'}>
                <span>
                  <Button
                    danger={workShift.status === 'OPEN'}
                    disabled={!canChangeStatus}
                    loading={statusChangingId === workShift.id}
                    type="text"
                    icon={workShift.status === 'CANCELLED'
                      ? <PlayCircleOutlined />
                      : <PauseCircleOutlined />}
                  />
                </span>
              </Tooltip>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Quản trị lịch làm việc</span>
          <h1>Ca làm thực tế</h1>
          <p>Sinh và điều chỉnh các ca làm cụ thể theo từng ngày trong kỳ xếp lịch.</p>
        </div>
        <div className="page-heading-actions">
          <Button
            icon={<PlusOutlined />}
            disabled={!periodEditable || templatesLoading || shiftTemplates.length === 0}
            onClick={openCreateDrawer}
          >
            Thêm một ca
          </Button>
          <Button
            type="primary"
            icon={<ThunderboltOutlined />}
            disabled={!periodEditable || templatesLoading || shiftTemplates.length === 0}
            onClick={() => setGenerateModalOpen(true)}
          >
            Sinh ca từ mẫu
          </Button>
        </div>
      </section>

      <section className="management-card work-shift-filter-panel">
        <Select
          allowClear
          loading={periodsLoading}
          onChange={handlePeriodChange}
          options={schedulePeriods.map((period) => ({
            label: `${period.name} — ${period.locationName}`,
            value: period.id,
          }))}
          placeholder="Chọn kỳ xếp lịch"
          showSearch
          optionFilterProp="label"
          value={selectedPeriodId}
        />
        <Select
          allowClear
          disabled={!selectedPeriodId}
          onChange={(value) => {
            setLoading(true)
            setStatusFilter(value)
          }}
          options={Object.entries(STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái ca"
          value={statusFilter}
        />
        <Button
          icon={<ReloadOutlined />}
          disabled={!selectedPeriodId}
          onClick={() => {
            setLoading(true)
            setRefreshKey((current) => current + 1)
          }}
        >
          Làm mới
        </Button>
      </section>

      {!selectedPeriod && !periodsLoading && (
        <Alert
          action={<Button onClick={() => navigate('/admin/schedule-periods')}>Quản lý kỳ lịch</Button>}
          message="Hãy chọn hoặc tạo một kỳ xếp lịch trước"
          description="Ca làm thực tế luôn thuộc về một kỳ xếp lịch và một chi nhánh cụ thể."
          showIcon
          type="info"
        />
      )}

      {selectedPeriod && (
        <Alert
          message={`${selectedPeriod.name} · ${selectedPeriod.locationName}`}
          description={`${formatDate(selectedPeriod.startDate)} – ${formatDate(selectedPeriod.endDate)} · Trạng thái: ${selectedPeriod.status}`}
          showIcon
          type={periodEditable ? 'info' : 'warning'}
        />
      )}

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách ca làm</strong>
            <span>{workShifts.length} ca phù hợp bộ lọc</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={workShifts}
          loading={loading}
          locale={{ emptyText: selectedPeriodId ? 'Kỳ này chưa có ca làm.' : 'Chưa chọn kỳ xếp lịch.' }}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1000 }}
        />
      </section>

      <WorkShiftFormDrawer
        open={drawerOpen}
        workShift={editingWorkShift}
        schedulePeriod={selectedPeriod}
        shiftTemplates={shiftTemplates}
        onClose={() => setDrawerOpen(false)}
        onSubmit={handleSave}
      />

      <ShiftRequirementDrawer
        open={Boolean(requirementWorkShift)}
        workShift={requirementWorkShift}
        positions={positions}
        editable={Boolean(
          periodEditable && requirementWorkShift?.status === 'OPEN'
        )}
        onClose={() => setRequirementWorkShift(null)}
        onSubmit={handleSaveRequirements}
      />

      <ShiftAssignmentDrawer
        open={Boolean(assignmentWorkShift)}
        workShift={assignmentWorkShift}
        editable={Boolean(
          periodEditable
            && ['OPEN', 'FILLED'].includes(assignmentWorkShift?.status)
        )}
        onClose={() => setAssignmentWorkShift(null)}
        onChanged={() => {
          setLoading(true)
          setRefreshKey((current) => current + 1)
        }}
      />

      <GenerateWorkShiftsModal
        open={generateModalOpen}
        schedulePeriod={selectedPeriod}
        shiftTemplates={shiftTemplates}
        onClose={() => setGenerateModalOpen(false)}
        onSubmit={handleGenerate}
      />
    </>
  )
}
