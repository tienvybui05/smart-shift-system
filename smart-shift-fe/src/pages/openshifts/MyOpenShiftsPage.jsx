import {
  CloseCircleOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import OpenShiftClaimModal from '../../components/openshifts/OpenShiftClaimModal.jsx'
import {
  AVAILABILITY_CONFIG,
  OPEN_SHIFT_CLAIM_STATUS_CONFIG,
  formatOpenShiftDate,
  formatOpenShiftDateTime,
  formatOpenShiftHours,
  formatOpenShiftTime,
} from '../../components/openshifts/openShiftDisplay.js'
import {
  cancelOpenShiftClaim,
  createOpenShiftClaim,
  getAvailableOpenShifts,
  getMyOpenShiftClaims,
} from '../../services/openShiftClaimService.js'

function ShiftTime({ shift }) {
  return (
    <div className="open-shift-time-cell">
      <strong>{formatOpenShiftDate(shift.workDate)}</strong>
      <span>
        {formatOpenShiftTime(shift.startTime)}–{formatOpenShiftTime(shift.endTime)}
        {shift.endsNextDay ? ' (hôm sau)' : ''}
      </span>
    </div>
  )
}

export default function MyOpenShiftsPage() {
  const [messageApi, messageContext] = message.useMessage()
  const [availableShifts, setAvailableShifts] = useState([])
  const [claims, setClaims] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)
  const [selectedShift, setSelectedShift] = useState(null)
  const [cancellingId, setCancellingId] = useState(null)

  useEffect(() => {
    let mounted = true
    Promise.all([
      getAvailableOpenShifts(),
      getMyOpenShiftClaims(),
    ])
      .then(([availableResult, claimResult]) => {
        if (!mounted) return
        setAvailableShifts(availableResult)
        setClaims(claimResult)
      })
      .catch((error) => {
        if (!mounted) return
        setAvailableShifts([])
        setClaims([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách ca trống.'))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, refreshKey])

  function handleRefresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleClaim(payload) {
    try {
      await createOpenShiftClaim(payload)
      messageApi.success('Đã gửi yêu cầu nhận ca. Vui lòng chờ quản lý xét duyệt.')
      handleRefresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể gửi yêu cầu nhận ca.'))
      throw error
    }
  }

  async function handleCancel(claim) {
    setCancellingId(claim.id)
    try {
      const updated = await cancelOpenShiftClaim(claim.id)
      setClaims((current) => current.map((item) => (
        item.id === updated.id ? updated : item
      )))
      messageApi.success('Đã hủy yêu cầu nhận ca.')
      handleRefresh()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể hủy yêu cầu nhận ca.'))
    } finally {
      setCancellingId(null)
    }
  }

  const pendingCount = claims.filter((claim) => claim.status === 'PENDING').length
  const approvedCount = claims.filter((claim) => claim.status === 'APPROVED').length
  const missingCount = availableShifts.reduce(
    (total, shift) => total + shift.missingEmployees,
    0,
  )

  const availableColumns = [
    {
      title: 'Ca làm',
      key: 'shift',
      fixed: 'left',
      width: 225,
      render: (_, shift) => (
        <div className="open-shift-name-cell">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: shift.colorCode || '#3988df' }}
          />
          <div>
            <strong>{shift.shiftName}</strong>
            <small>{shift.schedulePeriodName}</small>
          </div>
        </div>
      ),
    },
    {
      title: 'Thời gian',
      key: 'time',
      width: 230,
      render: (_, shift) => <ShiftTime shift={shift} />,
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionName',
      width: 150,
    },
    {
      title: 'Nhân sự',
      key: 'staffing',
      width: 165,
      render: (_, shift) => (
        <div className="open-shift-staffing-cell">
          <strong>{shift.assignedEmployees}/{shift.minimumEmployees}</strong>
          <span>Còn thiếu {shift.missingEmployees}</span>
        </div>
      ),
    },
    {
      title: 'Phù hợp',
      dataIndex: 'availabilityType',
      width: 120,
      render: (availabilityType) => {
        const config = AVAILABILITY_CONFIG[availabilityType] || {
          label: availabilityType,
          color: 'default',
        }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Giờ dự kiến',
      key: 'hours',
      width: 185,
      responsive: ['xl'],
      render: (_, shift) => (
        <div className="open-shift-hours-cell">
          <span>Ca: {formatOpenShiftHours(shift.workMinutes)}</span>
          <span>Tuần: {shift.projectedWeeklyHours}/{shift.maximumWeeklyHours} giờ</span>
        </div>
      ),
    },
    {
      title: 'Đơn chờ',
      dataIndex: 'pendingClaims',
      align: 'center',
      width: 95,
      responsive: ['lg'],
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      align: 'center',
      width: 135,
      render: (_, shift) => shift.myPendingClaimId ? (
        <Tag color="gold">Đã đăng ký</Tag>
      ) : (
        <Button
          icon={<SendOutlined />}
          onClick={() => setSelectedShift(shift)}
          size="small"
          type="primary"
        >
          Nhận ca
        </Button>
      ),
    },
  ]

  const claimColumns = [
    {
      title: 'Ca làm',
      key: 'shift',
      fixed: 'left',
      width: 210,
      render: (_, claim) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{claim.shiftName}</strong>
          <span>{claim.schedulePeriodName}</span>
        </div>
      ),
    },
    {
      title: 'Thời gian',
      key: 'time',
      width: 230,
      render: (_, claim) => <ShiftTime shift={claim} />,
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
      render: (status) => {
        const config = OPEN_SHIFT_CLAIM_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        }
        return <Tag color={config.color}>{config.label}</Tag>
      },
    },
    {
      title: 'Phản hồi',
      key: 'review',
      width: 210,
      responsive: ['lg'],
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
      width: 155,
      responsive: ['xl'],
      render: formatOpenShiftDateTime,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      align: 'center',
      width: 100,
      render: (_, claim) => claim.cancellable ? (
        <Popconfirm
          cancelText="Không"
          description="Yêu cầu đã hủy sẽ không thể gửi lại trên cùng bản ghi."
          okButtonProps={{ danger: true }}
          okText="Hủy yêu cầu"
          onConfirm={() => handleCancel(claim)}
          title="Hủy yêu cầu nhận ca này?"
        >
          <Button
            danger
            icon={<CloseCircleOutlined />}
            loading={cancellingId === claim.id}
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
          <span className="eyebrow">Lịch làm việc</span>
          <h1>Ca trống có thể đăng ký</h1>
          <p>Chọn ca còn thiếu nhân sự phù hợp với vị trí, lịch rảnh và giới hạn giờ làm của bạn.</p>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={handleRefresh}>
          Làm mới
        </Button>
      </section>

      <Alert
        className="open-shift-help-alert"
        description="Ca chỉ xuất hiện khi lịch còn ở bản nháp và bạn vượt qua các điều kiện phân công. Quản lý sẽ kiểm tra lại một lần nữa trước khi duyệt."
        message="Đăng ký không đồng nghĩa với được phân công ngay"
        showIcon
        type="info"
      />

      <section className="open-shift-summary-grid">
        <article className="open-shift-summary-card open-shift-summary-card--available">
          <span>Ca phù hợp</span><strong>{availableShifts.length}</strong><small>ca đang mở</small>
        </article>
        <article className="open-shift-summary-card open-shift-summary-card--missing">
          <span>Đang thiếu</span><strong>{missingCount}</strong><small>vị trí tối thiểu</small>
        </article>
        <article className="open-shift-summary-card open-shift-summary-card--pending">
          <span>Chờ duyệt</span><strong>{pendingCount}</strong><small>yêu cầu của bạn</small>
        </article>
        <article className="open-shift-summary-card open-shift-summary-card--approved">
          <span>Đã duyệt</span><strong>{approvedCount}</strong><small>yêu cầu của bạn</small>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách ca trống</strong>
            <span>{availableShifts.length} ca phù hợp với bạn</span>
          </div>
        </div>
        <Table
          columns={availableColumns}
          dataSource={availableShifts}
          loading={loading}
          locale={{ emptyText: 'Hiện chưa có ca trống phù hợp.' }}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          rowKey="workShiftId"
          scroll={{ x: 1250 }}
        />
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Lịch sử đăng ký</strong>
            <span>{claims.length} yêu cầu đã gửi</span>
          </div>
          <Space><Tag color="gold">{pendingCount} chờ duyệt</Tag></Space>
        </div>
        <Table
          columns={claimColumns}
          dataSource={claims}
          loading={loading}
          locale={{ emptyText: 'Bạn chưa gửi yêu cầu nhận ca nào.' }}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1150 }}
        />
      </section>

      {selectedShift && (
        <OpenShiftClaimModal
          onClose={() => setSelectedShift(null)}
          onSubmit={handleClaim}
          openShift={selectedShift}
        />
      )}
    </>
  )
}
