import {
  Alert,
  Button,
  Drawer,
  Empty,
  Popconfirm,
  Progress,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import {
  assignEmployee,
  getAssignmentCandidates,
  getShiftAssignmentSummary,
  removeShiftAssignment,
} from '../../services/shiftAssignmentService.js'

const AVAILABILITY_CONFIG = {
  PREFERRED: { label: 'Ưu tiên làm', color: 'purple' },
  AVAILABLE: { label: 'Có thể làm', color: 'green' },
}

const SOURCE_LABELS = {
  MANUAL: 'Thủ công',
  AUTO: 'Tự động',
  SWAP: 'Đổi ca',
}

function formatDate(value) {
  if (!value) return ''
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

function formatHours(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2)
}

export default function ShiftAssignmentDrawer({
  open,
  workShift,
  editable,
  onClose,
  onChanged,
}) {
  const [messageApi, messageContext] = message.useMessage()
  const [summary, setSummary] = useState(null)
  const [selectedPositionId, setSelectedPositionId] = useState(null)
  const [candidates, setCandidates] = useState([])
  const [loading, setLoading] = useState(true)
  const [candidatesLoading, setCandidatesLoading] = useState(false)
  const [assigningUserId, setAssigningUserId] = useState(null)
  const [removingAssignmentId, setRemovingAssignmentId] = useState(null)

  useEffect(() => {
    if (!open || !workShift) return undefined
    let mounted = true
    getShiftAssignmentSummary(workShift.id)
      .then((result) => {
        if (!mounted) return
        setSummary(result)
        setCandidatesLoading(result.requirements.length > 0)
        setSelectedPositionId((current) => {
          const stillExists = result.requirements.some(
            (requirement) => requirement.positionId === current,
          )
          return stillExists ? current : result.requirements[0]?.positionId || null
        })
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(
            error,
            'Không thể tải thông tin phân công của ca.',
          ))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, open, workShift])

  useEffect(() => {
    if (!open || !workShift || !selectedPositionId) {
      return undefined
    }
    let mounted = true
    getAssignmentCandidates(workShift.id, selectedPositionId)
      .then((result) => {
        if (mounted) setCandidates(result)
      })
      .catch((error) => {
        if (mounted) {
          setCandidates([])
          messageApi.error(getApiErrorMessage(
            error,
            'Không thể tải danh sách nhân viên phù hợp.',
          ))
        }
      })
      .finally(() => {
        if (mounted) setCandidatesLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, open, selectedPositionId, workShift])

  const selectedRequirement = useMemo(
    () => summary?.requirements.find(
      (requirement) => requirement.positionId === selectedPositionId,
    ),
    [selectedPositionId, summary],
  )

  async function reloadCandidates() {
    if (!workShift || !selectedPositionId) return
    setCandidatesLoading(true)
    try {
      setCandidates(await getAssignmentCandidates(
        workShift.id,
        selectedPositionId,
      ))
    } catch (error) {
      messageApi.error(getApiErrorMessage(
        error,
        'Không thể làm mới danh sách nhân viên.',
      ))
    } finally {
      setCandidatesLoading(false)
    }
  }

  async function handleAssign(candidate) {
    setAssigningUserId(candidate.userId)
    try {
      const result = await assignEmployee(workShift.id, candidate.userId)
      setSummary(result)
      messageApi.success(`Đã phân công ${candidate.fullName} vào ca.`)
      await reloadCandidates()
      onChanged?.(result)
    } catch (error) {
      messageApi.error(getApiErrorMessage(
        error,
        'Không thể phân công nhân viên.',
      ))
    } finally {
      setAssigningUserId(null)
    }
  }

  async function handleRemove(assignment) {
    setRemovingAssignmentId(assignment.id)
    try {
      const result = await removeShiftAssignment(assignment.id)
      setSummary(result)
      messageApi.success(`Đã gỡ ${assignment.fullName} khỏi ca.`)
      await reloadCandidates()
      onChanged?.(result)
    } catch (error) {
      messageApi.error(getApiErrorMessage(
        error,
        'Không thể gỡ phân công.',
      ))
    } finally {
      setRemovingAssignmentId(null)
    }
  }

  function handleClose() {
    if (assigningUserId || removingAssignmentId) return
    setSummary(null)
    setSelectedPositionId(null)
    setCandidates([])
    setLoading(true)
    onClose()
  }

  const canEdit = Boolean(editable && summary?.editable)
  const maximumReached = Boolean(selectedRequirement?.maximumReached)

  const candidateColumns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      width: 190,
      render: (_, candidate) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{candidate.fullName}</strong>
          <span>{candidate.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Lịch rảnh',
      dataIndex: 'availabilityType',
      width: 120,
      render: (type) => {
        const config = AVAILABILITY_CONFIG[type]
        return config
          ? <Tag color={config.color}>{config.label}</Tag>
          : <Tag>Chưa phù hợp</Tag>
      },
    },
    {
      title: 'Giờ dự kiến',
      key: 'hours',
      width: 150,
      responsive: ['md'],
      render: (_, candidate) => (
        <div className="assignment-hours-cell">
          <span>
            Ngày: {formatHours(candidate.projectedDailyHours)}/
            {formatHours(candidate.maxDailyHours)}h
          </span>
          <span>
            Tuần: {formatHours(candidate.projectedWeeklyHours)}/
            {formatHours(candidate.maxWeeklyHours)}h
          </span>
        </div>
      ),
    },
    {
      title: 'Đánh giá',
      key: 'eligibility',
      width: 130,
      render: (_, candidate) => {
        if (candidate.assigned) return <Tag color="blue">Đã xếp</Tag>
        if (candidate.eligible) {
          return <Tag color="success" icon={<CheckCircleOutlined />}>Phù hợp</Tag>
        }
        return (
          <Tooltip title={candidate.ineligibilityReasons.join('; ')}>
            <Tag color="warning">Không phù hợp</Tag>
          </Tooltip>
        )
      },
    },
    {
      title: '',
      key: 'action',
      width: 90,
      align: 'right',
      render: (_, candidate) => (
        <Tooltip
          title={maximumReached
            ? 'Vị trí đã đạt số nhân viên tối đa'
            : candidate.ineligibilityReasons.join('; ')}
        >
          <span>
            <Button
              disabled={
                !canEdit
                || !candidate.eligible
                || candidate.assigned
                || maximumReached
              }
              icon={<UserAddOutlined />}
              loading={assigningUserId === candidate.userId}
              size="small"
              type="primary"
              onClick={() => handleAssign(candidate)}
            >
              Xếp
            </Button>
          </span>
        </Tooltip>
      ),
    },
  ]

  const assignmentColumns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      render: (_, assignment) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{assignment.fullName}</strong>
          <span>{assignment.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionName',
      responsive: ['md'],
    },
    {
      title: 'Nguồn',
      dataIndex: 'assignmentSource',
      width: 100,
      render: (source) => <Tag>{SOURCE_LABELS[source] || source}</Tag>,
    },
    {
      title: '',
      key: 'action',
      width: 55,
      align: 'right',
      render: (_, assignment) => (
        <Popconfirm
          cancelText="Hủy"
          disabled={!canEdit}
          okButtonProps={{ danger: true }}
          okText="Gỡ"
          onConfirm={() => handleRemove(assignment)}
          title={`Gỡ ${assignment.fullName} khỏi ca?`}
        >
          <Tooltip title="Gỡ khỏi ca">
            <span>
              <Button
                danger
                disabled={!canEdit}
                icon={<DeleteOutlined />}
                loading={removingAssignmentId === assignment.id}
                size="small"
                type="text"
              />
            </span>
          </Tooltip>
        </Popconfirm>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <Drawer
        className="shift-assignment-drawer"
        destroyOnHidden
        maskClosable={!assigningUserId && !removingAssignmentId}
        onClose={handleClose}
        open={open}
        size="large"
        title="Phân công nhân viên"
        extra={<Button onClick={handleClose}>Đóng</Button>}
      >
        <div className="requirement-shift-summary">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: workShift?.colorCode || '#5B4CF0' }}
          />
          <div>
            <strong>{workShift?.shiftTemplateName}</strong>
            <span>
              {formatDate(workShift?.workDate)} · {formatTime(workShift?.startTime)}–
              {formatTime(workShift?.endTime)}
            </span>
          </div>
        </div>

        {loading ? (
          <div className="requirement-loading"><Spin /></div>
        ) : (
          <Space direction="vertical" size={20} style={{ width: '100%' }}>
            {!canEdit && (
              <Alert
                message="Chế độ chỉ xem"
                description="Chỉ có thể thay đổi phân công của ca chưa bắt đầu trong kỳ xếp lịch Nháp."
                showIcon
                type="warning"
              />
            )}

            {summary?.requirements.length === 0 ? (
              <Alert
                message="Ca chưa có nhu cầu nhân sự"
                description="Hãy cấu hình nhu cầu theo vị trí trước khi phân công nhân viên."
                showIcon
                type="info"
              />
            ) : (
              <>
                <div className="assignment-overview">
                  <div>
                    <span>Đã phân công</span>
                    <strong>{summary.totalAssignedEmployees} người</strong>
                  </div>
                  <div>
                    <span>Mức yêu cầu</span>
                    <strong>
                      {summary.totalMinEmployees}–{summary.totalMaxEmployees} người
                    </strong>
                  </div>
                  <Tag color={summary.minimumStaffed ? 'success' : 'processing'}>
                    {summary.minimumStaffed ? 'Đã đủ tối thiểu' : 'Đang thiếu người'}
                  </Tag>
                </div>

                <div className="assignment-requirement-grid">
                  {summary.requirements.map((requirement) => {
                    const percent = Math.min(
                      100,
                      Math.round(
                        (requirement.assignedEmployees
                          / Math.max(requirement.minEmployees, 1)) * 100,
                      ),
                    )
                    return (
                      <button
                        className={`assignment-requirement-card${
                          requirement.positionId === selectedPositionId
                            ? ' assignment-requirement-card--selected'
                            : ''
                        }`}
                        key={requirement.requirementId}
                        onClick={() => {
                          setCandidatesLoading(true)
                          setSelectedPositionId(requirement.positionId)
                        }}
                        type="button"
                      >
                        <span>{requirement.positionName}</span>
                        <strong>
                          {requirement.assignedEmployees}/{requirement.minEmployees}
                          <small> · tối đa {requirement.maxEmployees}</small>
                        </strong>
                        <Progress
                          percent={percent}
                          showInfo={false}
                          size="small"
                          status={requirement.minimumMet ? 'success' : 'active'}
                        />
                      </button>
                    )
                  })}
                </div>

                <section className="assignment-section">
                  <div className="table-heading">
                    <div>
                      <strong>Ứng viên · {selectedRequirement?.positionName}</strong>
                      <span>
                        Chỉ nhân viên đúng chi nhánh, vị trí và đáp ứng ràng buộc mới được xếp.
                      </span>
                    </div>
                  </div>
                  <Table
                    columns={candidateColumns}
                    dataSource={candidates}
                    loading={candidatesLoading}
                    locale={{
                      emptyText: (
                        <Empty
                          description="Không có nhân viên thuộc vị trí này"
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                      ),
                    }}
                    pagination={{ pageSize: 6, showSizeChanger: false }}
                    rowKey="userId"
                    scroll={{ x: 760 }}
                    size="small"
                  />
                </section>
              </>
            )}

            <section className="assignment-section">
              <div className="table-heading">
                <div>
                  <strong>Nhân viên đã phân công</strong>
                  <span>{summary?.assignments.length || 0} nhân viên trong ca</span>
                </div>
              </div>
              <Table
                columns={assignmentColumns}
                dataSource={summary?.assignments || []}
                locale={{ emptyText: 'Chưa có nhân viên nào được phân công.' }}
                pagination={false}
                rowKey="id"
                size="small"
              />
            </section>
          </Space>
        )}
      </Drawer>
    </>
  )
}
