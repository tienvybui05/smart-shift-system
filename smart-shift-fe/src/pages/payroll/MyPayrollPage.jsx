import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  GiftOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Alert, Button, Input, Table, Tag, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getMyPayrollRecords } from '../../services/payrollService.js'

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getCurrentMonthRange() {
  const now = new Date()
  return {
    startDate: toDateInputValue(new Date(now.getFullYear(), now.getMonth(), 1)),
    endDate: toDateInputValue(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
  }
}

function formatDate(value) {
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`))
}

function formatHours(minutes) {
  return `${new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 2,
  }).format(Number(minutes || 0) / 60)} giờ`
}

function formatCurrency(value) {
  return `${new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 0,
  }).format(Number(value || 0))} đ`
}

export default function MyPayrollPage() {
  const defaultRange = getCurrentMonthRange()
  const [messageApi, messageContext] = message.useMessage()
  const [filterInputs, setFilterInputs] = useState(defaultRange)
  const [filters, setFilters] = useState(defaultRange)
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let mounted = true
    getMyPayrollRecords(filters)
      .then((result) => {
        if (mounted) setRecords(result)
      })
      .catch((error) => {
        if (!mounted) return
        setRecords([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải lương dự tính.'))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [filters, messageApi, refreshKey])

  const summary = useMemo(() => ({
    workedMinutes: records.reduce((total, item) => total + Number(item.workedMinutes || 0), 0),
    baseAmount: records.reduce((total, item) => total + Number(item.baseAmount || 0), 0),
    bonusAmount: records.reduce((total, item) => total + Number(item.bonusAmount || 0), 0),
    totalAmount: records.reduce((total, item) => total + Number(item.totalAmount || 0), 0),
  }), [records])

  function applyFilters() {
    if (!filterInputs.startDate || !filterInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày.')
      return
    }
    if (filterInputs.endDate < filterInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return
    }
    const days = (
      new Date(`${filterInputs.endDate}T00:00:00`)
      - new Date(`${filterInputs.startDate}T00:00:00`)
    ) / 86400000
    if (days >= 93) {
      messageApi.error('Chỉ được xem trong khoảng tối đa 93 ngày.')
      return
    }
    setLoading(true)
    setFilters({ ...filterInputs })
  }

  const columns = [
    {
      title: 'Kỳ tính lương',
      key: 'period',
      width: 210,
      render: (_, record) => (
        <strong>{formatDate(record.periodStart)} – {formatDate(record.periodEnd)}</strong>
      ),
    },
    {
      title: 'Giờ thực tế',
      dataIndex: 'workedMinutes',
      width: 120,
      render: (value) => formatHours(value),
    },
    {
      title: 'Đơn giá',
      dataIndex: 'hourlyRate',
      width: 135,
      render: (value) => `${formatCurrency(value)}/giờ`,
    },
    {
      title: 'Hệ số',
      dataIndex: 'salaryCoefficient',
      width: 85,
      align: 'center',
      render: (value) => Number(value),
    },
    {
      title: 'Lương giờ',
      dataIndex: 'baseAmount',
      width: 140,
      align: 'right',
      render: formatCurrency,
    },
    {
      title: 'Thưởng',
      key: 'bonus',
      width: 180,
      align: 'right',
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal payroll-number-cell">
          <strong>{formatCurrency(record.bonusAmount)}</strong>
          <span>{record.bonusNote || 'Không có ghi chú'}</span>
        </div>
      ),
    },
    {
      title: 'Tổng dự tính',
      dataIndex: 'totalAmount',
      width: 150,
      align: 'right',
      render: (value) => <strong className="payroll-total-amount">{formatCurrency(value)}</strong>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (status) => status === 'CONFIRMED'
        ? <Tag color="success">Đã xác nhận</Tag>
        : <Tag color="processing">Dự tính</Tag>,
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Thu nhập của tôi</span>
          <h1>Công và lương dự tính</h1>
          <p>Theo dõi tổng giờ thực tế, đơn giá, hệ số và tiền thưởng trong kỳ.</p>
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
      </section>

      <Alert
        className="payroll-formula-alert"
        description="Số liệu do Manager tổng hợp từ các lượt chấm công đã duyệt. Bảng ở trạng thái dự tính có thể thay đổi cho đến khi được xác nhận."
        message="Giờ thực tế × đơn giá × hệ số + thưởng"
        showIcon
        type="info"
      />

      <section className="management-card payroll-filter-panel payroll-filter-panel--employee">
        <Input
          aria-label="Từ ngày"
          max={filterInputs.endDate}
          onChange={(event) => setFilterInputs((current) => ({
            ...current,
            startDate: event.target.value,
          }))}
          type="date"
          value={filterInputs.startDate}
        />
        <Input
          aria-label="Đến ngày"
          min={filterInputs.startDate}
          onChange={(event) => setFilterInputs((current) => ({
            ...current,
            endDate: event.target.value,
          }))}
          type="date"
          value={filterInputs.endDate}
        />
        <Button onClick={applyFilters} type="primary">Xem bảng lương</Button>
      </section>

      <section className="attendance-management-summary-grid">
        <article className="management-card attendance-summary-card">
          <ClockCircleOutlined />
          <div><span>Tổng giờ</span><strong>{formatHours(summary.workedMinutes)}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <DollarOutlined />
          <div><span>Lương theo giờ</span><strong>{formatCurrency(summary.baseAmount)}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <GiftOutlined />
          <div><span>Tiền thưởng</span><strong>{formatCurrency(summary.bonusAmount)}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <CheckCircleOutlined />
          <div><span>Tổng lương dự tính</span><strong>{formatCurrency(summary.totalAmount)}</strong></div>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Lịch sử bảng lương</strong>
            <span>{records.length} bảng lương trong khoảng đã chọn</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: 'Chưa có bảng lương trong khoảng đã chọn.' }}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1100 }}
        />
      </section>
    </>
  )
}
