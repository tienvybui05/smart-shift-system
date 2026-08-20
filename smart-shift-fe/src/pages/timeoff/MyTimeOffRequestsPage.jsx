import {
  CloseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Button, Popconfirm, Space, Table, Tag, Tooltip, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import TimeOffRequestFormDrawer from '../../components/timeoff/TimeOffRequestFormDrawer.jsx'
import {
  LEAVE_TYPE_CONFIG,
  TIME_OFF_STATUS_CONFIG,
  formatDateTime,
  formatTimeOffPeriod,
} from '../../components/timeoff/timeOffDisplay.js'
import {
  cancelMyTimeOffRequest,
  createMyTimeOffRequest,
  getMyTimeOffRequests,
} from '../../services/timeOffRequestService.js'

export default function MyTimeOffRequestsPage() {
  const [messageApi, messageContext] = message.useMessage()
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)

  useEffect(() => {
    let mounted = true

    getMyTimeOffRequests()
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
  }, [messageApi, refreshKey])

  async function handleCreate(payload) {
    await createMyTimeOffRequest(payload)
    messageApi.success('Đã gửi đơn xin nghỉ để quản lý xem xét.')
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleCancel(request) {
    setCancellingId(request.id)
    try {
      const updatedRequest = await cancelMyTimeOffRequest(request.id)
      setRequests((current) => current.map((item) => (
        item.id === updatedRequest.id ? updatedRequest : item
      )))
      messageApi.success('Đã hủy đơn xin nghỉ.')
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể hủy đơn xin nghỉ.'))
    } finally {
      setCancellingId(null)
    }
  }

  function handleRefresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  const statusCounts = requests.reduce((counts, request) => ({
    ...counts,
    [request.status]: (counts[request.status] || 0) + 1,
  }), {})

  const columns = [
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
      width: 150,
      render: (leaveType) => {
        const config = LEAVE_TYPE_CONFIG[leaveType] || { label: leaveType, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Lý do',
      dataIndex: 'reason',
      ellipsis: true,
      render: (reason) => reason || <span className="empty-value">Không có</span>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 125,
      render: (status) => {
        const config = TIME_OFF_STATUS_CONFIG[status] || { label: status, color: 'default' }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Gửi lúc',
      dataIndex: 'createdAt',
      width: 145,
      responsive: ['lg'],
      render: formatDateTime,
    },
    {
      title: 'Người duyệt',
      key: 'reviewer',
      width: 165,
      responsive: ['xl'],
      render: (_, request) => request.approvedByName ? (
        <div className="employee-cell employee-cell--normal">
          <strong>{request.approvedByName}</strong>
          <span>{formatDateTime(request.approvedAt)}</span>
        </div>
      ) : <span className="empty-value">Chưa xử lý</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 90,
      align: 'center',
      render: (_, request) => request.status === 'PENDING' ? (
        <Popconfirm
          cancelText="Giữ đơn"
          description="Đơn đã hủy không thể gửi lại hoặc chỉnh sửa."
          okButtonProps={{ danger: true }}
          okText="Hủy đơn"
          onConfirm={() => handleCancel(request)}
          title="Hủy đơn xin nghỉ này?"
        >
          <Tooltip title="Hủy đơn">
            <Button
              danger
              icon={<CloseCircleOutlined />}
              loading={cancellingId === request.id}
              type="text"
            />
          </Tooltip>
        </Popconfirm>
      ) : <span className="empty-value">—</span>,
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Quản lý thời gian cá nhân</span>
          <h1>Đơn xin nghỉ của tôi</h1>
          <p>Gửi yêu cầu nghỉ theo giờ hoặc nhiều ngày và theo dõi kết quả phê duyệt.</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={() => setFormOpen(true)} type="primary">
          Tạo đơn xin nghỉ
        </Button>
      </section>

      <section className="time-off-summary-grid">
        {Object.entries(TIME_OFF_STATUS_CONFIG).map(([status, config]) => (
          <article className={`time-off-summary-card time-off-summary-card--${status.toLowerCase()}`} key={status}>
            <span>{config.label}</span>
            <strong>{statusCounts[status] || 0}</strong>
            <small>đơn</small>
          </article>
        ))}
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Lịch sử đơn xin nghỉ</strong>
            <span>{requests.length} đơn đã gửi</span>
          </div>
          <Space>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={handleRefresh}>
              Làm mới
            </Button>
          </Space>
        </div>
        <Table
          columns={columns}
          dataSource={requests}
          loading={loading}
          locale={{ emptyText: 'Bạn chưa gửi đơn xin nghỉ nào.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1050 }}
        />
      </section>

      <TimeOffRequestFormDrawer
        onClose={() => setFormOpen(false)}
        onSubmit={handleCreate}
        open={formOpen}
      />
    </>
  )
}
