import {
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LinkOutlined,
  RedoOutlined,
  ReloadOutlined,
  SendOutlined,
  SyncOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Button, Select, Space, Table, Tag, Tooltip, message } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import {
  getLarkDeliveries,
  getLarkStatus,
  getLarkUsers,
  retryLarkDelivery,
  sendLarkTest,
  sendPersonalLarkTest,
  syncAllLarkUsers,
  syncLarkUser,
} from '../../services/larkIntegrationService.js'

const DELIVERY_PAGE_SIZE = 15
const USER_PAGE_SIZE = 10

const STATUS_META = {
  PENDING: { color: 'processing', label: 'Chờ gửi' },
  SENT: { color: 'success', label: 'Đã gửi' },
  FAILED: { color: 'error', label: 'Thất bại' },
}

const CHANNEL_META = {
  GROUP_WEBHOOK: { color: 'blue', label: 'Nhóm vận hành' },
  PERSONAL_APP: { color: 'purple', label: 'Tin cá nhân' },
}

const EVENT_LABELS = {
  GROUP_CONNECTION_TEST: 'Kiểm tra webhook nhóm',
  PERSONAL_CONNECTION_TEST: 'Kiểm tra App Bot cá nhân',
  SCHEDULE_PUBLISHED: 'Công bố lịch',
  TIME_OFF_REQUEST_CREATED: 'Có đơn xin nghỉ',
  TIME_OFF_REQUEST_APPROVED: 'Duyệt đơn xin nghỉ',
  TIME_OFF_REQUEST_REJECTED: 'Từ chối đơn xin nghỉ',
  OPEN_SHIFT_CLAIM_CREATED: 'Có yêu cầu nhận ca',
  OPEN_SHIFT_CLAIM_APPROVED: 'Duyệt nhận ca',
  OPEN_SHIFT_CLAIM_REJECTED: 'Từ chối nhận ca',
  SHIFT_SWAP_REQUEST_CREATED: 'Có yêu cầu đổi/nhường ca',
  SHIFT_SWAP_REQUEST_ACCEPTED: 'Đã nhận đổi/nhường ca',
  SHIFT_SWAP_REQUEST_DECLINED: 'Từ chối đổi ca',
  SHIFT_SWAP_REQUEST_APPROVED: 'Duyệt đổi/nhường ca',
  SHIFT_SWAP_REQUEST_REJECTED: 'Từ chối đổi/nhường ca',
  SHIFT_SWAP_REQUEST_CANCELLED: 'Hủy đổi/nhường ca',
  PAYROLL_CONFIRMED: 'Xác nhận lương dự tính',
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function StatusCard({ icon, label, value, note, tone = 'neutral' }) {
  return (
    <article className={`lark-status-card lark-status-card--${tone}`}>
      <span className="lark-status-card__icon">{icon}</span>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <small>{note}</small>
      </div>
    </article>
  )
}

function ChannelAlert({ ready, enabled, title, readyText, setupText }) {
  const type = ready ? 'success' : enabled ? 'warning' : 'info'
  const description = ready
    ? readyText
    : enabled
      ? setupText
      : 'Kênh này đang tắt trong file .env.'
  return (
    <Alert
      showIcon
      type={type}
      message={`${title}: ${ready ? 'Sẵn sàng' : enabled ? 'Thiếu cấu hình' : 'Đang tắt'}`}
      description={description}
    />
  )
}

function userLinkStatus(user) {
  if (user.syncError) {
    return {
      color: user.linked ? 'warning' : 'error',
      label: user.linked ? 'Cần kiểm tra' : 'Đồng bộ lỗi',
    }
  }
  if (user.linked) return { color: 'success', label: 'Đã liên kết' }
  return { color: 'default', label: 'Chưa liên kết' }
}

export default function LarkIntegrationPage() {
  const [messageApi, messageContext] = message.useMessage()
  const [status, setStatus] = useState(null)
  const [deliveries, setDeliveries] = useState([])
  const [deliveryStatus, setDeliveryStatus] = useState(undefined)
  const [deliveryPage, setDeliveryPage] = useState(0)
  const [deliveryTotal, setDeliveryTotal] = useState(0)
  const [users, setUsers] = useState([])
  const [userPage, setUserPage] = useState(0)
  const [userTotal, setUserTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [groupTesting, setGroupTesting] = useState(false)
  const [syncingAll, setSyncingAll] = useState(false)
  const [syncingUserId, setSyncingUserId] = useState(null)
  const [testingUserId, setTestingUserId] = useState(null)
  const [retryingId, setRetryingId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [statusResult, deliveryResult, userResult] = await Promise.all([
        getLarkStatus(),
        getLarkDeliveries({
          page: deliveryPage,
          size: DELIVERY_PAGE_SIZE,
          ...(deliveryStatus ? { status: deliveryStatus } : {}),
        }),
        getLarkUsers({ page: userPage, size: USER_PAGE_SIZE }),
      ])
      setStatus(statusResult)
      setDeliveries(deliveryResult.content)
      setDeliveryTotal(deliveryResult.totalElements)
      setUsers(userResult.content)
      setUserTotal(userResult.totalElements)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể tải trạng thái Lark.'))
    } finally {
      setLoading(false)
    }
  }, [deliveryPage, deliveryStatus, messageApi, userPage])

  useEffect(() => {
    const loadId = window.setTimeout(loadData, 0)
    return () => window.clearTimeout(loadId)
  }, [loadData])

  async function handleGroupTest() {
    setGroupTesting(true)
    try {
      await sendLarkTest()
      messageApi.success('Đã đưa tin nhắn thử nhóm vào hàng đợi.')
      setDeliveryPage(0)
      await loadData()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể tạo tin nhắn thử nhóm.'))
    } finally {
      setGroupTesting(false)
    }
  }

  async function handleSyncUser(user) {
    setSyncingUserId(user.userId)
    try {
      const result = await syncLarkUser(user.userId)
      if (result.linked && !result.syncError) {
        messageApi.success(`Đã liên kết tài khoản Lark của ${user.fullName}.`)
      } else {
        messageApi.warning(result.syncError || 'Không tìm thấy tài khoản Lark phù hợp.')
      }
      await loadData()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể đồng bộ tài khoản Lark.'))
    } finally {
      setSyncingUserId(null)
    }
  }

  async function handleSyncAll() {
    setSyncingAll(true)
    try {
      const result = await syncAllLarkUsers()
      messageApi.success(
        `Đã xử lý ${result.totalUsers} tài khoản: ${result.linkedUsers} thành công, ${result.failedUsers} lỗi.`,
      )
      setUserPage(0)
      await loadData()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể đồng bộ danh sách nhân viên.'))
    } finally {
      setSyncingAll(false)
    }
  }

  async function handlePersonalTest(user) {
    setTestingUserId(user.userId)
    try {
      await sendPersonalLarkTest(user.userId)
      messageApi.success(`Đã đưa tin nhắn thử cho ${user.fullName} vào hàng đợi.`)
      setDeliveryPage(0)
      await loadData()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể gửi thử cho nhân viên.'))
    } finally {
      setTestingUserId(null)
    }
  }

  async function handleRetry(id) {
    setRetryingId(id)
    try {
      await retryLarkDelivery(id)
      messageApi.success('Đã đưa thông báo vào hàng đợi gửi lại.')
      await loadData()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể gửi lại thông báo.'))
    } finally {
      setRetryingId(null)
    }
  }

  const userColumns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 210,
      render: (_, user) => (
        <div className="employee-cell">
          <strong>{user.fullName}</strong>
          <span>{user.employeeCode} · {user.roleName.replace('ROLE_', '')}</span>
        </div>
      ),
    },
    {
      title: 'Thông tin đối chiếu',
      key: 'contact',
      width: 250,
      render: (_, user) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{user.email || 'Chưa có email'}</strong>
          <span>{user.phoneNumber || 'Chưa có số điện thoại'}</span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 190,
    },
    {
      title: 'Liên kết Lark',
      key: 'linkStatus',
      width: 145,
      render: (_, user) => {
        const meta = userLinkStatus(user)
        return (
          <Tooltip title={user.syncError || undefined}>
            <Tag color={meta.color}>{meta.label}</Tag>
          </Tooltip>
        )
      },
    },
    {
      title: 'Lần đồng bộ',
      dataIndex: 'syncedAt',
      width: 165,
      render: formatDateTime,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 110,
      align: 'center',
      render: (_, user) => (
        <Space size={2}>
          <Tooltip title="Đồng bộ bằng email/số điện thoại">
            <Button
              aria-label="Đồng bộ tài khoản Lark"
              disabled={!status?.personalReady || !user.active}
              icon={<SyncOutlined />}
              loading={syncingUserId === user.userId}
              onClick={() => handleSyncUser(user)}
              type="text"
            />
          </Tooltip>
          <Tooltip title={user.linked ? 'Gửi thử tin nhắn cá nhân' : 'Cần liên kết trước'}>
            <Button
              aria-label="Gửi thử tin nhắn cá nhân"
              disabled={!status?.personalReady || !user.linked}
              icon={<SendOutlined />}
              loading={testingUserId === user.userId}
              onClick={() => handlePersonalTest(user)}
              type="text"
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  const deliveryColumns = [
    {
      title: 'Thời gian',
      dataIndex: 'createdAt',
      width: 165,
      render: formatDateTime,
    },
    {
      title: 'Kênh nhận',
      key: 'channel',
      width: 175,
      render: (_, record) => {
        const meta = CHANNEL_META[record.channel] || { color: 'default', label: record.channel }
        return (
          <div className="lark-channel-cell">
            <Tag color={meta.color}>{meta.label}</Tag>
            <span>{record.recipientName || 'Nhóm Lark chung'}</span>
          </div>
        )
      },
    },
    {
      title: 'Sự kiện',
      key: 'event',
      width: 320,
      render: (_, record) => (
        <div className="lark-event-cell">
          <strong>{record.title}</strong>
          <span>{EVENT_LABELS[record.notificationType] || record.notificationType}</span>
          <p>{record.content}</p>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 115,
      render: (value) => {
        const meta = STATUS_META[value] || { color: 'default', label: value }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: 'Số lần gửi',
      key: 'attempts',
      width: 105,
      align: 'center',
      render: (_, record) => `${record.attemptCount}/${status?.maxAttempts || 3}`,
    },
    {
      title: 'Kết quả gần nhất',
      key: 'result',
      width: 220,
      render: (_, record) => (
        <div className="lark-result-cell">
          <span>{formatDateTime(record.sentAt || record.lastAttemptAt)}</span>
          {record.lastError && <small title={record.lastError}>{record.lastError}</small>}
          {!record.lastError && record.status === 'SENT' && <small>Gửi thành công</small>}
          {!record.lastError && record.status === 'PENDING' && <small>Đang chờ xử lý</small>}
        </div>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 82,
      align: 'center',
      render: (_, record) => record.status === 'FAILED' ? (
        <Tooltip title="Gửi lại">
          <Button
            aria-label="Gửi lại"
            icon={<RedoOutlined />}
            loading={retryingId === record.id}
            onClick={() => handleRetry(record.id)}
            type="text"
          />
        </Tooltip>
      ) : '—',
    },
  ]

  return (
    <div className="page-stack lark-integration-page">
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">THÔNG BÁO NGOÀI HỆ THỐNG</span>
          <h1>Tích hợp Lark</h1>
          <p>App Bot gửi riêng cho từng người; webhook nhóm nhận cảnh báo vận hành.</p>
        </div>
        <div className="page-heading-actions">
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>
            Làm mới
          </Button>
          <Button
            disabled={!status?.groupReady}
            icon={<SendOutlined />}
            loading={groupTesting}
            onClick={handleGroupTest}
            type="primary"
          >
            Gửi thử nhóm
          </Button>
        </div>
      </section>

      <section className="lark-channel-alerts">
        <ChannelAlert
          enabled={Boolean(status?.personalEnabled)}
          ready={Boolean(status?.personalReady)}
          title="App Bot cá nhân"
          readyText="Có thể đồng bộ nhân viên và gửi lịch, kết quả duyệt hoặc nhắc việc riêng tư."
          setupText="Bổ sung LARK_APP_ID và LARK_APP_SECRET trong file .env."
        />
        <ChannelAlert
          enabled={Boolean(status?.groupEnabled)}
          ready={Boolean(status?.groupReady)}
          title="Webhook nhóm"
          readyText={`Đã sẵn sàng gửi cảnh báo nhóm${status?.signatureEnabled ? ' với xác thực chữ ký' : ''}.`}
          setupText="Bổ sung LARK_WEBHOOK_URL trong file .env."
        />
      </section>

      <section className="lark-status-grid">
        <StatusCard
          icon={status?.personalReady ? <CheckCircleOutlined /> : <ApiOutlined />}
          label="App Bot cá nhân"
          value={status?.personalReady ? 'Sẵn sàng' : 'Chưa cấu hình'}
          note="Lịch và kết quả riêng tư"
          tone={status?.personalReady ? 'green' : 'neutral'}
        />
        <StatusCard
          icon={<LinkOutlined />}
          label="Đã liên kết Lark"
          value={`${status?.linkedUsers ?? 0}/${status?.eligibleUsers ?? 0}`}
          note={status?.userSyncErrors ? `${status.userSyncErrors} tài khoản cần kiểm tra` : 'Nhân viên đang hoạt động'}
          tone={status?.userSyncErrors ? 'red' : 'blue'}
        />
        <StatusCard
          icon={<ClockCircleOutlined />}
          label="Chờ gửi"
          value={status?.pendingDeliveries ?? 0}
          note="Xử lý nền tự động"
          tone="blue"
        />
        <StatusCard
          icon={status?.failedDeliveries ? <CloseCircleOutlined /> : <CheckCircleOutlined />}
          label="Gửi thất bại"
          value={status?.failedDeliveries ?? 0}
          note={`${status?.sentDeliveries ?? 0} thông báo đã gửi`}
          tone={status?.failedDeliveries ? 'red' : 'green'}
        />
      </section>

      {status?.latestError && (
        <Alert showIcon type="error" message="Lỗi gửi gần nhất" description={status.latestError} />
      )}

      <section className="management-card employee-table-card lark-user-card">
        <div className="table-heading">
          <div>
            <strong>Liên kết tài khoản nhân viên</strong>
            <span>Đối chiếu bằng email hoặc số điện thoại đang dùng trong Lark Workspace</span>
          </div>
          <Button
            disabled={!status?.personalReady}
            icon={<TeamOutlined />}
            loading={syncingAll}
            onClick={handleSyncAll}
          >
            Đồng bộ tất cả
          </Button>
        </div>
        <Table
          columns={userColumns}
          dataSource={users}
          loading={loading}
          pagination={{
            current: userPage + 1,
            pageSize: USER_PAGE_SIZE,
            showSizeChanger: false,
            total: userTotal,
            onChange: (nextPage) => setUserPage(nextPage - 1),
          }}
          rowKey="userId"
          scroll={{ x: 1050 }}
        />
      </section>

      <section className="management-card employee-table-card lark-delivery-card">
        <div className="table-heading">
          <div>
            <strong>Lịch sử gửi Lark</strong>
            <span>{deliveryTotal} lần gửi cá nhân và nhóm</span>
          </div>
          <Select
            allowClear
            onChange={(value) => {
              setDeliveryStatus(value)
              setDeliveryPage(0)
            }}
            options={Object.entries(STATUS_META).map(([value, meta]) => ({
              value,
              label: meta.label,
            }))}
            placeholder="Tất cả trạng thái"
            value={deliveryStatus}
            style={{ width: 175 }}
          />
        </div>
        <Table
          columns={deliveryColumns}
          dataSource={deliveries}
          loading={loading}
          pagination={{
            current: deliveryPage + 1,
            pageSize: DELIVERY_PAGE_SIZE,
            showSizeChanger: false,
            total: deliveryTotal,
            onChange: (nextPage) => setDeliveryPage(nextPage - 1),
          }}
          rowKey="id"
          scroll={{ x: 1180 }}
        />
      </section>
    </div>
  )
}
