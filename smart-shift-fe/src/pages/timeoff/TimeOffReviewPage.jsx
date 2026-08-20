import {
  CheckOutlined,
  CloseOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Popconfirm,
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
  LEAVE_TYPE_CONFIG,
  TIME_OFF_STATUS_CONFIG,
  formatDateTime,
  formatTimeOffPeriod,
} from '../../components/timeoff/timeOffDisplay.js'
import useAuth from '../../hooks/useAuth.js'
import { getLocations } from '../../services/referenceService.js'
import {
  getTimeOffRequests,
  reviewTimeOffRequest,
} from '../../services/timeOffRequestService.js'

export default function TimeOffReviewPage() {
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
  const [reviewing, setReviewing] = useState(null)

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
    getTimeOffRequests({
      status: filters.status,
      locationId: filters.locationId,
    })
      .then((result) => {
        if (mounted) setRequests(result)
      })
      .catch((error) => {
        if (mounted) {
          setRequests([])
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách đơn xin nghỉ.'))
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
      status: undefined,
      locationId: isAdmin ? undefined : user.locationId,
    })
  }

  function retryLocations() {
    setLocationsLoading(true)
    setLocationError('')
    setLocationRefreshKey((current) => current + 1)
  }

  function handleRefresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleReview(request, status) {
    setReviewing({ id: request.id, status })
    try {
      const updatedRequest = await reviewTimeOffRequest(request.id, status)
      setRequests((current) => {
        if (filters.status && filters.status !== updatedRequest.status) {
          return current.filter((item) => item.id !== updatedRequest.id)
        }
        return current.map((item) => (
          item.id === updatedRequest.id ? updatedRequest : item
        ))
      })
      messageApi.success(status === 'APPROVED'
        ? 'Đã duyệt đơn xin nghỉ.'
        : 'Đã từ chối đơn xin nghỉ.')
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể xử lý đơn xin nghỉ.'))
    } finally {
      setReviewing(null)
    }
  }

  const statusCounts = requests.reduce((counts, request) => ({
    ...counts,
    [request.status]: (counts[request.status] || 0) + 1,
  }), {})

  const columns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 205,
      render: (_, request) => (
        <div className="employee-cell">
          <strong>{request.userFullName}</strong>
          <span>{request.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 165,
      responsive: ['lg'],
    },
    {
      title: 'Thời gian nghỉ',
      key: 'period',
      width: 275,
      render: (_, request) => (
        <strong className="time-off-period">
          {formatTimeOffPeriod(request.startAt, request.endAt)}
        </strong>
      ),
    },
    {
      title: 'Loại nghỉ',
      dataIndex: 'leaveType',
      width: 145,
      render: (leaveType) => {
        const config = LEAVE_TYPE_CONFIG[leaveType] || { label: leaveType, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
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
      render: (status, request) => {
        const config = TIME_OFF_STATUS_CONFIG[status] || { label: status, color: 'default' }
        return (
          <div className="employee-cell employee-cell--normal">
            <span><Tag color={config.color}>{config.label}</Tag></span>
            {request.approvedByName && <small>{request.approvedByName}</small>}
          </div>
        )
      },
    },
    {
      title: 'Gửi lúc',
      dataIndex: 'createdAt',
      width: 145,
      responsive: ['xl'],
      render: formatDateTime,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 115,
      align: 'center',
      render: (_, request) => {
        if (request.status !== 'PENDING') return <span className="empty-value">Đã xử lý</span>

        const ownRequest = request.userId === user.id
        const approving = reviewing?.id === request.id && reviewing.status === 'APPROVED'
        const rejecting = reviewing?.id === request.id && reviewing.status === 'REJECTED'
        return (
          <Space size={2}>
            <Popconfirm
              cancelText="Hủy"
              disabled={ownRequest}
              okText="Duyệt"
              onConfirm={() => handleReview(request, 'APPROVED')}
              title="Duyệt đơn xin nghỉ này?"
            >
              <Tooltip title={ownRequest ? 'Không thể tự duyệt đơn của mình' : 'Duyệt đơn'}>
                <span>
                  <Button
                    className="time-off-approve-button"
                    disabled={ownRequest || Boolean(reviewing)}
                    icon={<CheckOutlined />}
                    loading={approving}
                    type="text"
                  />
                </span>
              </Tooltip>
            </Popconfirm>
            <Popconfirm
              cancelText="Hủy"
              description="Nhân viên sẽ thấy trạng thái đơn là bị từ chối."
              disabled={ownRequest}
              okButtonProps={{ danger: true }}
              okText="Từ chối"
              onConfirm={() => handleReview(request, 'REJECTED')}
              title="Từ chối đơn xin nghỉ này?"
            >
              <Tooltip title={ownRequest ? 'Không thể tự xử lý đơn của mình' : 'Từ chối đơn'}>
                <span>
                  <Button
                    danger
                    disabled={ownRequest || Boolean(reviewing)}
                    icon={<CloseOutlined />}
                    loading={rejecting}
                    type="text"
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
          <span className="eyebrow">Quản lý nhân sự</span>
          <h1>Duyệt đơn xin nghỉ</h1>
          <p>Xem xét yêu cầu của nhân viên theo trạng thái và phạm vi chi nhánh phụ trách.</p>
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
          options={locations.map((location) => ({
            label: location.code ? `${location.code} — ${location.name}` : location.name,
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
          options={Object.entries(TIME_OFF_STATUS_CONFIG).map(([value, config]) => ({
            label: config.label,
            value,
          }))}
          placeholder="Tất cả trạng thái"
          value={filters.status}
        />
        <Button icon={<ReloadOutlined />} onClick={resetFilters}>Đặt lại</Button>
      </section>

      <section className="time-off-summary-grid">
        {Object.entries(TIME_OFF_STATUS_CONFIG).map(([status, config]) => (
          <article className={`time-off-summary-card time-off-summary-card--${status.toLowerCase()}`} key={status}>
            <span>{config.label}</span>
            <strong>{statusCounts[status] || 0}</strong>
            <small>đơn trong kết quả</small>
          </article>
        ))}
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách đơn xin nghỉ</strong>
            <span>{requests.length} đơn phù hợp bộ lọc</span>
          </div>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={handleRefresh}>
            Làm mới
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={requests}
          loading={loading}
          locale={{ emptyText: 'Không có đơn xin nghỉ phù hợp.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1250 }}
        />
      </section>
    </>
  )
}
