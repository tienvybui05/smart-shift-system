import {
  CheckOutlined,
  CloseCircleOutlined,
  CloseOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import ShiftSwapRequestModal from '../../components/shiftswaps/ShiftSwapRequestModal.jsx'
import {
  SHIFT_SWAP_STATUS_CONFIG,
  SHIFT_SWAP_TYPE_CONFIG,
  formatShiftSwapDate,
  formatShiftSwapDateTime,
  formatShiftSwapTime,
} from '../../components/shiftswaps/shiftSwapDisplay.js'
import useAuth from '../../hooks/useAuth.js'
import { getMyWorkSchedule } from '../../services/myScheduleService.js'
import {
  cancelShiftSwapRequest,
  createShiftSwapRequest,
  getAvailableShiftGiveaways,
  getMyShiftSwapRequests,
  respondToShiftSwapRequest,
} from '../../services/shiftSwapService.js'

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getScheduleRange() {
  const start = new Date()
  const end = new Date(start)
  end.setDate(start.getDate() + 92)
  return {
    startDate: toDateInputValue(start),
    endDate: toDateInputValue(end),
  }
}

function AssignmentCell({ assignment }) {
  if (!assignment) return <span className="empty-value">Không có ca đối ứng</span>
  return (
    <div className="shift-swap-assignment-cell">
      <span
        className="shift-color-dot"
        style={{ backgroundColor: assignment.colorCode || '#5B4CF0' }}
      />
      <div>
        <strong>{assignment.shiftName}</strong>
        <span>{formatShiftSwapDate(assignment.workDate)}</span>
        <small>
          {formatShiftSwapTime(assignment.startTime)}–{formatShiftSwapTime(assignment.endTime)}
          {assignment.overnight ? ' · Qua ngày' : ''}
        </small>
      </div>
    </div>
  )
}

function ResponseModal({ action, onClose, onSubmit, submitting }) {
  const [form] = Form.useForm()
  const accepting = action.status === 'ACCEPTED'
  const typeLabel = SHIFT_SWAP_TYPE_CONFIG[action.request.type]?.label || action.request.type

  return (
    <Modal
      centered
      destroyOnHidden
      footer={null}
      onCancel={onClose}
      open
      title={accepting ? `Đồng ý ${typeLabel.toLowerCase()}` : 'Từ chối đổi ca'}
      width={560}
    >
      <Alert
        className="shift-swap-modal-alert"
        description={accepting
          ? 'Sau khi bạn đồng ý, yêu cầu sẽ được chuyển đến quản lý. Lịch chỉ thay đổi khi quản lý duyệt.'
          : 'Người gửi sẽ nhận được thông báo rằng bạn đã từ chối yêu cầu.'}
        message={`${action.request.requesterFullName} · ${action.request.requesterAssignment.shiftName}`}
        showIcon
        type={accepting ? 'info' : 'warning'}
      />
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => onSubmit(values.responseNote?.trim() || null)}
      >
        <Form.Item
          label="Ghi chú phản hồi"
          name="responseNote"
          rules={[{ max: 1000, message: 'Ghi chú không được vượt quá 1000 ký tự.' }]}
        >
          <Input.TextArea
            maxLength={1000}
            placeholder={accepting
              ? 'Ví dụ: Tôi có thể nhận hoặc đổi ca này.'
              : 'Ví dụ: Tôi đã có kế hoạch cá nhân.'}
            rows={4}
            showCount
          />
        </Form.Item>
        <div className="modal-action-row">
          <Button disabled={submitting} onClick={onClose}>Hủy</Button>
          <Button
            danger={!accepting}
            htmlType="submit"
            icon={accepting ? <CheckOutlined /> : <CloseOutlined />}
            loading={submitting}
            type="primary"
          >
            {accepting ? 'Đồng ý và gửi quản lý' : 'Từ chối yêu cầu'}
          </Button>
        </div>
      </Form>
    </Modal>
  )
}

export default function MyShiftSwapsPage() {
  const { user } = useAuth()
  const [messageApi, messageContext] = message.useMessage()
  const [requests, setRequests] = useState([])
  const [availableGiveaways, setAvailableGiveaways] = useState([])
  const [assignments, setAssignments] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [responseAction, setResponseAction] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)

  useEffect(() => {
    let mounted = true
    const range = getScheduleRange()
    Promise.all([
      getMyShiftSwapRequests(),
      getAvailableShiftGiveaways(),
      getMyWorkSchedule(range),
    ])
      .then(([requestResult, giveawayResult, scheduleResult]) => {
        if (!mounted) return
        setRequests(requestResult)
        setAvailableGiveaways(giveawayResult)
        setAssignments(scheduleResult)
      })
      .catch((error) => {
        if (!mounted) return
        messageApi.error(getApiErrorMessage(
          error,
          'Không thể tải dữ liệu đổi và nhường ca.',
        ))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, refreshKey])

  const activeOutgoingAssignmentIds = useMemo(() => new Set(
    requests
      .filter((request) => request.requesterUserId === user.id
        && ['PENDING', 'ACCEPTED'].includes(request.status))
      .map((request) => request.requesterAssignment.assignmentId),
  ), [requests, user.id])

  const requestableAssignments = assignments.filter(
    (assignment) => assignment.schedulePeriodStatus === 'PUBLISHED'
      && !activeOutgoingAssignmentIds.has(assignment.assignmentId),
  )

  const actionableRequests = useMemo(() => {
    const result = new Map()
    requests.filter((request) => request.respondable).forEach(
      (request) => result.set(request.id, request),
    )
    availableGiveaways.forEach((request) => result.set(request.id, request))
    return [...result.values()]
  }, [availableGiveaways, requests])

  const summary = useMemo(() => ({
    outgoing: requests.filter((request) => request.requesterUserId === user.id
      && ['PENDING', 'ACCEPTED'].includes(request.status)).length,
    awaitingMe: actionableRequests.length,
    awaitingManager: requests.filter((request) => request.status === 'ACCEPTED').length,
    approved: requests.filter((request) => request.status === 'APPROVED').length,
  }), [actionableRequests.length, requests, user.id])

  function refresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleCreate(payload) {
    setSubmitting(true)
    try {
      await createShiftSwapRequest(payload)
      messageApi.success(payload.targetAssignmentId
        ? 'Đã gửi yêu cầu đổi ca đến đồng nghiệp.'
        : 'Đã đăng ca nhường công khai.')
      setCreateOpen(false)
      refresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể tạo yêu cầu.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRespond(responseNote) {
    setSubmitting(true)
    try {
      await respondToShiftSwapRequest(
        responseAction.request.id,
        responseAction.status,
        responseNote,
      )
      messageApi.success(responseAction.status === 'ACCEPTED'
        ? 'Đã đồng ý và chuyển yêu cầu đến quản lý.'
        : 'Đã từ chối yêu cầu đổi ca.')
      setResponseAction(null)
      refresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể phản hồi yêu cầu.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancel(request) {
    setCancellingId(request.id)
    try {
      await cancelShiftSwapRequest(request.id)
      messageApi.success('Đã hủy yêu cầu đổi/nhường ca.')
      refresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể hủy yêu cầu.'))
    } finally {
      setCancellingId(null)
    }
  }

  const offerColumns = [
    {
      title: 'Người gửi',
      key: 'requester',
      width: 180,
      render: (_, request) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{request.requesterFullName}</strong>
          <span>{request.requesterEmployeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Hình thức',
      dataIndex: 'type',
      width: 115,
      render: (type) => {
        const config = SHIFT_SWAP_TYPE_CONFIG[type]
        return <Tag color={config?.color}>{config?.label || type}</Tag>
      },
    },
    {
      title: 'Ca bạn sẽ nhận',
      dataIndex: 'requesterAssignment',
      width: 250,
      render: (assignment) => <AssignmentCell assignment={assignment} />,
    },
    {
      title: 'Ca của bạn đổi lại',
      dataIndex: 'targetAssignment',
      width: 250,
      render: (assignment) => <AssignmentCell assignment={assignment} />,
    },
    {
      title: 'Lý do',
      dataIndex: 'reason',
      ellipsis: true,
      render: (reason) => reason
        ? <Tooltip title={reason}>{reason}</Tooltip>
        : <span className="empty-value">Không có</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      align: 'center',
      width: 145,
      render: (_, request) => (
        <Space size={2}>
          <Tooltip title="Đồng ý nhận hoặc đổi ca">
            <Button
              className="time-off-approve-button"
              icon={<CheckOutlined />}
              onClick={() => setResponseAction({ request, status: 'ACCEPTED' })}
              type="text"
            />
          </Tooltip>
          {request.type === 'SWAP' && (
            <Tooltip title="Từ chối yêu cầu">
              <Button
                danger
                icon={<CloseOutlined />}
                onClick={() => setResponseAction({ request, status: 'DECLINED' })}
                type="text"
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  const historyColumns = [
    {
      title: 'Yêu cầu',
      key: 'request',
      fixed: 'left',
      width: 175,
      render: (_, request) => {
        const typeConfig = SHIFT_SWAP_TYPE_CONFIG[request.type]
        const mine = request.requesterUserId === user.id
        return (
          <div className="employee-cell employee-cell--normal">
            <span><Tag color={typeConfig?.color}>{typeConfig?.label || request.type}</Tag></span>
            <strong>{mine ? 'Bạn gửi' : request.requesterFullName}</strong>
          </div>
        )
      },
    },
    {
      title: 'Ca chuyển đi',
      dataIndex: 'requesterAssignment',
      width: 250,
      render: (assignment) => <AssignmentCell assignment={assignment} />,
    },
    {
      title: 'Người nhận / Ca đối ứng',
      key: 'target',
      width: 270,
      render: (_, request) => (
        <div className="shift-swap-target-cell">
          <strong>{request.targetFullName || 'Chưa có người nhận'}</strong>
          {request.targetAssignment
            ? <AssignmentCell assignment={request.targetAssignment} />
            : <span>{request.type === 'GIVEAWAY' ? 'Nhường ca công khai' : '—'}</span>}
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (status) => {
        const config = SHIFT_SWAP_STATUS_CONFIG[status] || { label: status }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Phản hồi',
      key: 'feedback',
      width: 210,
      render: (_, request) => (
        <div className="open-shift-review-cell">
          <span>{request.responseNote || request.reviewerNote || '—'}</span>
          {request.approvedByName && <small>{request.approvedByName}</small>}
        </div>
      ),
    },
    {
      title: 'Tạo lúc',
      dataIndex: 'createdAt',
      width: 155,
      responsive: ['xl'],
      render: formatShiftSwapDateTime,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      align: 'center',
      width: 115,
      render: (_, request) => request.cancellable ? (
        <Popconfirm
          cancelText="Không"
          description="Yêu cầu đang chờ sẽ được kết thúc và không làm thay đổi lịch."
          okButtonProps={{ danger: true }}
          okText="Hủy yêu cầu"
          onConfirm={() => handleCancel(request)}
          title="Hủy yêu cầu này?"
        >
          <Button
            danger
            icon={<CloseCircleOutlined />}
            loading={cancellingId === request.id}
            size="small"
            type="text"
          >
            Hủy
          </Button>
        </Popconfirm>
      ) : <span className="empty-value">—</span>,
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Điều chỉnh lịch cá nhân</span>
          <h1>Đổi và nhường ca</h1>
          <p>Gửi yêu cầu đổi ca với đồng nghiệp hoặc đăng ca cần nhường để người phù hợp nhận.</p>
        </div>
        <div className="page-heading-actions">
          <Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>
            Làm mới
          </Button>
          <Button
            disabled={requestableAssignments.length === 0}
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
            type="primary"
          >
            Tạo yêu cầu
          </Button>
        </div>
      </section>

      <Alert
        className="shift-swap-help-alert"
        description="Chỉ ca tương lai thuộc lịch đã công bố mới được sử dụng. Mọi yêu cầu phải được người nhận đồng ý và quản lý duyệt trước khi phân công thay đổi."
        message="Quy trình gồm hai bước xác nhận"
        showIcon
        type="info"
      />

      <section className="shift-swap-summary-grid">
        <article className="shift-swap-summary-card shift-swap-summary-card--outgoing">
          <span>Yêu cầu đang gửi</span><strong>{summary.outgoing}</strong><small>của bạn</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--incoming">
          <span>Chờ bạn phản hồi</span><strong>{summary.awaitingMe}</strong><small>yêu cầu phù hợp</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--accepted">
          <span>Chờ quản lý</span><strong>{summary.awaitingManager}</strong><small>đã có người nhận</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--approved">
          <span>Đã duyệt</span><strong>{summary.approved}</strong><small>yêu cầu liên quan</small>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Yêu cầu bạn có thể nhận</strong>
            <span>{actionableRequests.length} yêu cầu đang chờ phản hồi</span>
          </div>
          <SwapOutlined />
        </div>
        <Table
          columns={offerColumns}
          dataSource={actionableRequests}
          loading={loading}
          locale={{ emptyText: 'Hiện không có yêu cầu phù hợp cần bạn phản hồi.' }}
          pagination={{ pageSize: 6, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1150 }}
        />
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Lịch sử đổi và nhường ca</strong>
            <span>{requests.length} yêu cầu do bạn gửi hoặc nhận</span>
          </div>
        </div>
        <Table
          columns={historyColumns}
          dataSource={requests}
          loading={loading}
          locale={{ emptyText: 'Bạn chưa có yêu cầu đổi hoặc nhường ca nào.' }}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1350 }}
        />
      </section>

      {createOpen && (
        <ShiftSwapRequestModal
          assignments={requestableAssignments}
          onClose={() => setCreateOpen(false)}
          onSubmit={handleCreate}
          submitting={submitting}
        />
      )}
      {responseAction && (
        <ResponseModal
          action={responseAction}
          onClose={() => setResponseAction(null)}
          onSubmit={handleRespond}
          submitting={submitting}
        />
      )}
    </>
  )
}
