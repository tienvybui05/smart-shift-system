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
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import {
  OPEN_SHIFT_CLAIM_STATUS_CONFIG,
  formatOpenShiftDate,
  formatOpenShiftDateTime,
  formatOpenShiftTime,
} from '../../components/openshifts/openShiftDisplay.js'
import useAuth from '../../hooks/useAuth.js'
import {
  getOpenShiftClaims,
  reviewOpenShiftClaim,
} from '../../services/openShiftClaimService.js'
import { getLocations } from '../../services/referenceService.js'

function ReviewModal({ action, onClose, onSubmit, submitting }) {
  const [form] = Form.useForm()
  const approving = action.status === 'APPROVED'

  function handleFinish(values) {
    onSubmit(values.reviewerNote?.trim() || null)
  }

  return (
    <Modal
      centered
      destroyOnHidden
      footer={null}
      onCancel={onClose}
      open
      title={approving ? 'Duyệt yêu cầu nhận ca' : 'Từ chối yêu cầu nhận ca'}
      width={540}
    >
      <Alert
        className="open-shift-review-alert"
        description={approving
          ? 'Hệ thống sẽ kiểm tra lại lịch rảnh, giới hạn giờ làm và nhu cầu nhân sự trước khi tạo phân công.'
          : 'Nhân viên sẽ thấy trạng thái từ chối cùng ghi chú của bạn.'}
        message={`${action.claim.userFullName} — ${action.claim.shiftName}`}
        showIcon
        type={approving ? 'info' : 'warning'}
      />
      <Form form={form} layout="vertical" onFinish={handleFinish}>
        <Form.Item
          label="Ghi chú phản hồi"
          name="reviewerNote"
          rules={[{
            max: 1000,
            message: 'Ghi chú không được vượt quá 1000 ký tự.',
          }]}
        >
          <Input.TextArea
            maxLength={1000}
            placeholder={approving
              ? 'Ví dụ: Đã kiểm tra và đồng ý phân công.'
              : 'Ví dụ: Ca đã có phương án nhân sự khác.'}
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
            {approving ? 'Duyệt và phân công' : 'Từ chối yêu cầu'}
          </Button>
        </div>
      </Form>
    </Modal>
  )
}

export default function OpenShiftClaimReviewPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const [messageApi, messageContext] = message.useMessage()
  const [claims, setClaims] = useState([])
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
  }, [isAdmin, locationRefreshKey])

  useEffect(() => {
    let mounted = true
    getOpenShiftClaims({
      status: filters.status,
      locationId: filters.locationId,
    })
      .then((result) => {
        if (mounted) setClaims(result)
      })
      .catch((error) => {
        if (!mounted) return
        setClaims([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách yêu cầu nhận ca.'))
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
      status: undefined,
      locationId: isAdmin ? undefined : user.locationId,
    })
  }

  function handleRefresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleReview(reviewerNote) {
    setSubmitting(true)
    try {
      await reviewOpenShiftClaim(
        reviewAction.claim.id,
        reviewAction.status,
        reviewerNote,
      )
      messageApi.success(reviewAction.status === 'APPROVED'
        ? 'Đã duyệt và tạo phân công cho nhân viên.'
        : 'Đã từ chối yêu cầu nhận ca.')
      setReviewAction(null)
      handleRefresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể xét duyệt yêu cầu nhận ca.'))
    } finally {
      setSubmitting(false)
    }
  }

  function retryLocations() {
    setLocationsLoading(true)
    setLocationError('')
    setLocationRefreshKey((current) => current + 1)
  }

  const statusCounts = claims.reduce((counts, claim) => ({
    ...counts,
    [claim.status]: (counts[claim.status] || 0) + 1,
  }), {})

  const columns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 205,
      render: (_, claim) => (
        <div className="employee-cell">
          <strong>{claim.userFullName}</strong>
          <span>{claim.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 160,
      responsive: ['lg'],
    },
    {
      title: 'Ca đăng ký',
      key: 'shift',
      width: 245,
      render: (_, claim) => (
        <div className="open-shift-time-cell">
          <strong>{claim.shiftName} · {claim.positionName}</strong>
          <span>
            {formatOpenShiftDate(claim.workDate)}, {' '}
            {formatOpenShiftTime(claim.startTime)}–{formatOpenShiftTime(claim.endTime)}
            {claim.endsNextDay ? ' (hôm sau)' : ''}
          </span>
        </div>
      ),
    },
    {
      title: 'Lý do',
      dataIndex: 'reason',
      ellipsis: true,
      render: (reason) => reason ? (
        <Tooltip title={reason}><span>{reason}</span></Tooltip>
      ) : <span className="empty-value">Không có</span>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 125,
      render: (status, claim) => {
        const config = OPEN_SHIFT_CLAIM_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        }
        return (
          <div className="employee-cell employee-cell--normal">
            <span><Tag color={config.color}>{config.label}</Tag></span>
            {claim.assignmentId && <small>Phân công #{claim.assignmentId}</small>}
          </div>
        )
      },
    },
    {
      title: 'Phản hồi',
      key: 'review',
      width: 200,
      responsive: ['xl'],
      render: (_, claim) => (
        <div className="open-shift-review-cell">
          <span>{claim.reviewedByName || '—'}</span>
          {claim.reviewerNote && (
            <Tooltip title={claim.reviewerNote}>
              <small>{claim.reviewerNote}</small>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      title: 'Gửi lúc',
      dataIndex: 'createdAt',
      width: 150,
      responsive: ['xl'],
      render: formatOpenShiftDateTime,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      align: 'center',
      width: 115,
      render: (_, claim) => claim.status === 'PENDING' ? (
        <Space size={2}>
          <Tooltip title="Duyệt và phân công">
            <Button
              className="time-off-approve-button"
              disabled={Boolean(reviewAction)}
              icon={<CheckOutlined />}
              onClick={() => setReviewAction({ claim, status: 'APPROVED' })}
              type="text"
            />
          </Tooltip>
          <Tooltip title="Từ chối yêu cầu">
            <Button
              danger
              disabled={Boolean(reviewAction)}
              icon={<CloseOutlined />}
              onClick={() => setReviewAction({ claim, status: 'REJECTED' })}
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
          <h1>Duyệt yêu cầu nhận ca</h1>
          <p>Kiểm tra yêu cầu tự đăng ký và tạo phân công cho các ca còn thiếu nhân sự.</p>
        </div>
      </section>

      {locationError && (
        <Alert
          action={<Button onClick={retryLocations} size="small">Thử lại</Button>}
          message={locationError}
          showIcon
          type="error"
        />
      )}

      <section className="management-card time-off-review-filter-panel">
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
          options={Object.entries(OPEN_SHIFT_CLAIM_STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái"
          value={filters.status}
        />
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="open-shift-summary-grid">
        {Object.entries(OPEN_SHIFT_CLAIM_STATUS_CONFIG).map(([status, config]) => (
          <article
            className={`open-shift-summary-card open-shift-summary-card--${status.toLowerCase()}`}
            key={status}
          >
            <span>{config.label}</span>
            <strong>{statusCounts[status] || 0}</strong>
            <small>yêu cầu trong kết quả</small>
          </article>
        ))}
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách yêu cầu nhận ca</strong>
            <span>{claims.length} yêu cầu phù hợp bộ lọc</span>
          </div>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={handleRefresh}>
            Làm mới
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={claims}
          loading={loading}
          locale={{ emptyText: 'Không có yêu cầu nhận ca phù hợp.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1250 }}
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
