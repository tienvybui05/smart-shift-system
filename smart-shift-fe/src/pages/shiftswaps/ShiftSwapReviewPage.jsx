import {
  CheckOutlined,
  CloseOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import {
  SHIFT_SWAP_STATUS_CONFIG,
  SHIFT_SWAP_TYPE_CONFIG,
  formatShiftSwapDate,
  formatShiftSwapDateTime,
  formatShiftSwapTime,
} from '../../components/shiftswaps/shiftSwapDisplay.js'
import useAuth from '../../hooks/useAuth.js'
import { getLocations } from '../../services/referenceService.js'
import {
  getShiftSwapRequests,
  reviewShiftSwapRequest,
} from '../../services/shiftSwapService.js'

function AssignmentCell({ assignment }) {
  if (!assignment) return <span className="empty-value">Nhường ca, không đổi lại</span>
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

function ReviewModal({ action, onClose, onSubmit, submitting }) {
  const [form] = Form.useForm()
  const approving = action.status === 'APPROVED'
  const typeLabel = SHIFT_SWAP_TYPE_CONFIG[action.request.type]?.label || action.request.type

  return (
    <Modal
      centered
      destroyOnHidden
      footer={null}
      onCancel={onClose}
      open
      title={approving ? `Duyệt ${typeLabel.toLowerCase()}` : `Từ chối ${typeLabel.toLowerCase()}`}
      width={620}
    >
      <Alert
        className="shift-swap-modal-alert"
        description={approving
          ? 'Hệ thống sẽ xác thực lại lịch rảnh, đơn nghỉ, ca trùng, thời gian nghỉ và giới hạn giờ của cả hai nhân viên trước khi chuyển phân công.'
          : 'Lịch làm việc hiện tại sẽ được giữ nguyên và hai nhân viên sẽ nhận thông báo.'}
        message={`${action.request.requesterFullName} → ${action.request.targetFullName}`}
        showIcon
        type={approving ? 'info' : 'warning'}
      />
      <div className="shift-swap-review-preview">
        <section>
          <span>Ca chuyển đi</span>
          <AssignmentCell assignment={action.request.requesterAssignment} />
        </section>
        <section>
          <span>{action.request.type === 'SWAP' ? 'Ca đổi lại' : 'Hình thức'}</span>
          <AssignmentCell assignment={action.request.targetAssignment} />
        </section>
      </div>
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => onSubmit(values.reviewerNote?.trim() || null)}
      >
        <Form.Item
          label="Ghi chú xét duyệt"
          name="reviewerNote"
          rules={[{ max: 1000, message: 'Ghi chú không được vượt quá 1000 ký tự.' }]}
        >
          <Input.TextArea
            maxLength={1000}
            placeholder={approving
              ? 'Ví dụ: Đã xác nhận đủ điều kiện chuyển phân công.'
              : 'Ví dụ: Không thể điều chỉnh lịch trong thời điểm này.'}
            rows={4}
            showCount
          />
        </Form.Item>
        <div className="modal-action-row">
          <Button disabled={submitting} onClick={onClose}>Hủy</Button>
          <Button
            danger={!approving}
            htmlType="submit"
            icon={approving ? <CheckOutlined /> : <CloseOutlined />}
            loading={submitting}
            type="primary"
          >
            {approving ? 'Duyệt và chuyển phân công' : 'Từ chối yêu cầu'}
          </Button>
        </div>
      </Form>
    </Modal>
  )
}

export default function ShiftSwapReviewPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const [messageApi, messageContext] = message.useMessage()
  const [requests, setRequests] = useState([])
  const [locations, setLocations] = useState(() => (
    isAdmin || !user.locationId
      ? []
      : [{ id: user.locationId, name: user.locationName }]
  ))
  const [locationsLoading, setLocationsLoading] = useState(isAdmin)
  const [locationError, setLocationError] = useState('')
  const [locationRefreshKey, setLocationRefreshKey] = useState(0)
  const [filters, setFilters] = useState({
    status: undefined,
    locationId: isAdmin ? undefined : user.locationId,
  })
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [reviewAction, setReviewAction] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isAdmin) return undefined
    let mounted = true
    getLocations()
      .then((result) => {
        if (mounted) setLocations(result)
      })
      .catch((error) => {
        if (mounted) setLocationError(getApiErrorMessage(
          error,
          'Không thể tải danh sách chi nhánh.',
        ))
      })
      .finally(() => {
        if (mounted) setLocationsLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [isAdmin, locationRefreshKey])

  useEffect(() => {
    let mounted = true
    getShiftSwapRequests(filters)
      .then((result) => {
        if (mounted) setRequests(result)
      })
      .catch((error) => {
        if (!mounted) return
        setRequests([])
        messageApi.error(getApiErrorMessage(
          error,
          'Không thể tải danh sách yêu cầu đổi/nhường ca.',
        ))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [filters, messageApi, refreshKey])

  const summary = useMemo(() => ({
    pending: requests.filter((request) => request.status === 'PENDING').length,
    accepted: requests.filter((request) => request.status === 'ACCEPTED').length,
    approved: requests.filter((request) => request.status === 'APPROVED').length,
    rejected: requests.filter((request) => ['REJECTED', 'DECLINED'].includes(request.status)).length,
  }), [requests])

  function applyFilter(name, value) {
    setLoading(true)
    setFilters((current) => ({ ...current, [name]: value }))
  }

  function resetFilters() {
    setLoading(true)
    setFilters({
      status: undefined,
      locationId: isAdmin ? undefined : user.locationId,
    })
  }

  function refresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleReview(reviewerNote) {
    setSubmitting(true)
    try {
      await reviewShiftSwapRequest(
        reviewAction.request.id,
        reviewAction.status,
        reviewerNote,
      )
      messageApi.success(reviewAction.status === 'APPROVED'
        ? 'Đã duyệt và cập nhật phân công của nhân viên.'
        : 'Đã từ chối yêu cầu, lịch làm được giữ nguyên.')
      setReviewAction(null)
      refresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể xét duyệt yêu cầu.'))
    } finally {
      setSubmitting(false)
    }
  }

  const columns = [
    {
      title: 'Hình thức',
      dataIndex: 'type',
      fixed: 'left',
      width: 115,
      render: (type) => {
        const config = SHIFT_SWAP_TYPE_CONFIG[type]
        return <Tag color={config?.color}>{config?.label || type}</Tag>
      },
    },
    {
      title: 'Người gửi',
      key: 'requester',
      width: 190,
      render: (_, request) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{request.requesterFullName}</strong>
          <span>{request.requesterEmployeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Ca chuyển đi',
      dataIndex: 'requesterAssignment',
      width: 250,
      render: (assignment) => <AssignmentCell assignment={assignment} />,
    },
    {
      title: 'Người nhận',
      key: 'target',
      width: 190,
      render: (_, request) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{request.targetFullName || 'Chưa có người nhận'}</strong>
          <span>{request.targetEmployeeCode || 'Nhường công khai'}</span>
        </div>
      ),
    },
    {
      title: 'Ca đổi lại',
      dataIndex: 'targetAssignment',
      width: 250,
      responsive: ['lg'],
      render: (assignment) => <AssignmentCell assignment={assignment} />,
    },
    {
      title: 'Lý do và phản hồi',
      key: 'notes',
      width: 230,
      render: (_, request) => (
        <div className="open-shift-review-cell">
          <Tooltip title={request.reason || 'Không có lý do'}>
            <span>{request.reason || 'Không có lý do'}</span>
          </Tooltip>
          {request.responseNote && (
            <Tooltip title={request.responseNote}>
              <small>Người nhận: {request.responseNote}</small>
            </Tooltip>
          )}
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
      render: (_, request) => request.reviewable ? (
        <Space size={2}>
          <Tooltip title="Duyệt và chuyển phân công">
            <Button
              className="time-off-approve-button"
              icon={<CheckOutlined />}
              onClick={() => setReviewAction({ request, status: 'APPROVED' })}
              type="text"
            />
          </Tooltip>
          <Tooltip title="Từ chối yêu cầu">
            <Button
              danger
              icon={<CloseOutlined />}
              onClick={() => setReviewAction({ request, status: 'REJECTED' })}
              type="text"
            />
          </Tooltip>
        </Space>
      ) : <span className="empty-value">Đã xử lý</span>,
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Điều phối nhân sự</span>
          <h1>Duyệt đổi và nhường ca</h1>
          <p>Xem yêu cầu đã được người nhận đồng ý và xác nhận chuyển phân công an toàn.</p>
        </div>
      </section>

      <Alert
        className="shift-swap-help-alert"
        description="Nút duyệt chỉ xuất hiện sau khi có người nhận đồng ý. Các ràng buộc được xác thực lại ngay trong giao dịch trước khi lịch thay đổi."
        message="Không chuyển ca nếu dữ liệu đã thay đổi hoặc không còn hợp lệ"
        showIcon
        type="info"
      />

      {locationError && (
        <Alert
          action={<Button onClick={() => {
            setLocationsLoading(true)
            setLocationError('')
            setLocationRefreshKey((current) => current + 1)
          }} size="small">Thử lại</Button>}
          message={locationError}
          showIcon
          type="error"
        />
      )}

      <section className="management-card shift-swap-review-filters">
        <Select
          allowClear={isAdmin}
          disabled={!isAdmin}
          loading={locationsLoading}
          onChange={(value) => applyFilter('locationId', value)}
          optionFilterProp="label"
          options={locations.map((location) => ({
            label: location.code ? `${location.code} — ${location.name}` : location.name,
            value: location.id,
          }))}
          placeholder="Tất cả chi nhánh"
          showSearch
          value={filters.locationId}
        />
        <Select
          allowClear
          onChange={(value) => applyFilter('status', value)}
          options={Object.entries(SHIFT_SWAP_STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái"
          value={filters.status}
        />
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="shift-swap-summary-grid">
        <article className="shift-swap-summary-card shift-swap-summary-card--outgoing">
          <span>Chờ người nhận</span><strong>{summary.pending}</strong><small>chưa thể duyệt</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--accepted">
          <span>Chờ quản lý</span><strong>{summary.accepted}</strong><small>cần xử lý</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--approved">
          <span>Đã duyệt</span><strong>{summary.approved}</strong><small>đã chuyển lịch</small>
        </article>
        <article className="shift-swap-summary-card shift-swap-summary-card--rejected">
          <span>Đã từ chối</span><strong>{summary.rejected}</strong><small>không đổi lịch</small>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách yêu cầu đổi/nhường ca</strong>
            <span>{requests.length} yêu cầu phù hợp bộ lọc</span>
          </div>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>
            Làm mới
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={requests}
          loading={loading}
          locale={{ emptyText: 'Không có yêu cầu đổi/nhường ca phù hợp.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1650 }}
        />
      </section>

      {reviewAction && (
        <ReviewModal
          action={reviewAction}
          onClose={() => setReviewAction(null)}
          onSubmit={handleReview}
          submitting={submitting}
        />
      )}
    </>
  )
}
