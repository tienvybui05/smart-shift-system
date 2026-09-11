import {
  CalculatorOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  EditOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Input,
  Popconfirm,
  Select,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import PayrollBonusModal from '../../components/payroll/PayrollBonusModal.jsx'
import useAuth from '../../hooks/useAuth.js'
import {
  calculatePayroll,
  confirmPayroll,
  getPayrollRecords,
  updatePayrollBonus,
} from '../../services/payrollService.js'
import { getLocations } from '../../services/referenceService.js'

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
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`))
}

function formatHours(minutes) {
  return `${new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(minutes || 0) / 60)} giờ`
}

function formatCurrency(value) {
  return `${new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 0,
  }).format(Number(value || 0))} đ`
}

export default function PayrollManagementPage() {
  const { user } = useAuth()
  const isAdmin = user.role === 'ROLE_ADMIN'
  const defaultRange = getCurrentMonthRange()
  const [messageApi, messageContext] = message.useMessage()
  const [records, setRecords] = useState([])
  const [locations, setLocations] = useState(() => (
    isAdmin || !user.locationId
      ? []
      : [{ id: user.locationId, name: user.locationName }]
  ))
  const [locationsLoading, setLocationsLoading] = useState(isAdmin)
  const [locationError, setLocationError] = useState('')
  const [filterInputs, setFilterInputs] = useState({
    ...defaultRange,
    locationId: isAdmin ? undefined : user.locationId,
  })
  const [filters, setFilters] = useState(filterInputs)
  const [loading, setLoading] = useState(true)
  const [calculating, setCalculating] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [editingRecord, setEditingRecord] = useState(null)

  useEffect(() => {
    if (!isAdmin) return undefined
    let mounted = true
    getLocations()
      .then((result) => {
        if (mounted) setLocations(result.filter((location) => location.active))
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
  }, [isAdmin])

  useEffect(() => {
    let mounted = true
    getPayrollRecords(filters)
      .then((result) => {
        if (mounted) setRecords(result)
      })
      .catch((error) => {
        if (!mounted) return
        setRecords([])
        messageApi.error(getApiErrorMessage(error, 'Không thể tải bảng lương.'))
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [filters, messageApi, refreshKey])

  const summary = useMemo(() => ({
    employees: records.length,
    workedMinutes: records.reduce((total, item) => total + Number(item.workedMinutes || 0), 0),
    bonusAmount: records.reduce((total, item) => total + Number(item.bonusAmount || 0), 0),
    totalAmount: records.reduce((total, item) => total + Number(item.totalAmount || 0), 0),
    confirmed: records.filter((item) => item.status === 'CONFIRMED').length,
  }), [records])

  function validateRange(requireLocation = false) {
    if (!filterInputs.startDate || !filterInputs.endDate) {
      messageApi.error('Vui lòng chọn đầy đủ khoảng ngày.')
      return false
    }
    if (filterInputs.endDate < filterInputs.startDate) {
      messageApi.error('Ngày kết thúc không được trước ngày bắt đầu.')
      return false
    }
    const days = (
      new Date(`${filterInputs.endDate}T00:00:00`)
      - new Date(`${filterInputs.startDate}T00:00:00`)
    ) / 86400000
    if (days >= 93) {
      messageApi.error('Chỉ được tính lương trong khoảng tối đa 93 ngày.')
      return false
    }
    if (requireLocation && !filterInputs.locationId) {
      messageApi.error('Vui lòng chọn chi nhánh cần tính lương.')
      return false
    }
    return true
  }

  function applyFilters() {
    if (!validateRange()) return
    setLoading(true)
    setFilters({ ...filterInputs })
  }

  async function handleCalculate() {
    if (!validateRange(true)) return
    setCalculating(true)
    try {
      const result = await calculatePayroll(filterInputs)
      setRecords(result)
      setFilters({ ...filterInputs })
      messageApi.success('Đã tính lại bảng lương từ các lượt chấm công đã duyệt.')
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể tính bảng lương.'))
    } finally {
      setCalculating(false)
    }
  }

  async function handleBonus(payload) {
    const updated = await updatePayrollBonus(editingRecord.id, payload)
    setRecords((current) => current.map((item) => item.id === updated.id ? updated : item))
    messageApi.success('Đã cập nhật tiền thưởng.')
  }

  async function handleConfirm(record) {
    try {
      const updated = await confirmPayroll(record.id)
      setRecords((current) => current.map((item) => item.id === updated.id ? updated : item))
      messageApi.success(`Đã xác nhận bảng lương của ${record.employeeName}.`)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể xác nhận bảng lương.'))
    }
  }

  const columns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 205,
      render: (_, record) => (
        <div className="employee-cell">
          <strong>{record.employeeName}</strong>
          <span>{record.employeeCode} · {record.positionName || 'Quản lý'}</span>
        </div>
      ),
    },
    {
      title: 'Chi nhánh',
      dataIndex: 'locationName',
      width: 180,
      responsive: ['lg'],
    },
    {
      title: 'Kỳ tính',
      key: 'period',
      width: 175,
      render: (_, record) => `${formatDate(record.periodStart)} – ${formatDate(record.periodEnd)}`,
    },
    {
      title: 'Giờ thực tế',
      dataIndex: 'workedMinutes',
      width: 115,
      align: 'right',
      render: (value, record) => <strong>
        {record.payBasis === 'MONTHLY' ? 'Không áp dụng' : formatHours(value)}
      </strong>,
    },
    {
      title: 'Đơn giá · hệ số',
      key: 'rate',
      width: 155,
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal">
          <strong>
            {formatCurrency(record.basePayAmount)}
            {record.payBasis === 'MONTHLY' ? '/tháng' : '/giờ'}
          </strong>
          <span>Hệ số {Number(record.salaryCoefficient)}</span>
        </div>
      ),
    },
    {
      title: 'Lương cơ sở',
      dataIndex: 'baseAmount',
      width: 135,
      align: 'right',
      render: formatCurrency,
    },
    {
      title: 'Thưởng',
      key: 'bonus',
      width: 145,
      align: 'right',
      render: (_, record) => (
        <div className="employee-cell employee-cell--normal payroll-number-cell">
          <strong>{formatCurrency(record.bonusAmount)}</strong>
          <Tooltip title={record.bonusNote || 'Không có ghi chú'}>
            <span>{record.bonusNote || 'Chưa có ghi chú'}</span>
          </Tooltip>
        </div>
      ),
    },
    {
      title: 'Tổng dự tính',
      dataIndex: 'totalAmount',
      width: 145,
      align: 'right',
      render: (value) => <strong className="payroll-total-amount">{formatCurrency(value)}</strong>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (status) => status === 'CONFIRMED'
        ? <Tag color="success">Đã xác nhận</Tag>
        : <Tag color="processing">Bản nháp</Tag>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 110,
      align: 'center',
      render: (_, record) => (
        <div className="payroll-actions">
          <Tooltip title={record.status === 'CONFIRMED' ? 'Bảng lương đã xác nhận' : 'Cập nhật thưởng'}>
            <Button
              disabled={record.status === 'CONFIRMED'}
              icon={<EditOutlined />}
              onClick={() => setEditingRecord(record)}
              type="text"
            />
          </Tooltip>
          <Popconfirm
            cancelText="Hủy"
            description="Sau khi xác nhận, bảng lương này không thể tính lại hoặc sửa thưởng."
            disabled={record.status === 'CONFIRMED'}
            okText="Xác nhận"
            onConfirm={() => handleConfirm(record)}
            title={`Xác nhận bảng lương của ${record.employeeName}?`}
          >
            <Tooltip title={record.status === 'CONFIRMED' ? 'Đã xác nhận' : 'Xác nhận bảng lương'}>
              <Button
                disabled={record.status === 'CONFIRMED'}
                icon={<CheckCircleOutlined />}
                type="text"
              />
            </Tooltip>
          </Popconfirm>
        </div>
      ),
    },
  ]

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">Tổng hợp chấm công</span>
          <h1>Tính công và lương dự tính</h1>
          <p>Tính theo giờ thực tế × đơn giá theo giờ × hệ số lương + tiền thưởng.</p>
        </div>
        <div className="page-heading-actions">
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
          <Button
            icon={<CalculatorOutlined />}
            loading={calculating}
            onClick={handleCalculate}
            type="primary"
          >
            Tính lại bảng lương
          </Button>
        </div>
      </section>

      {locationError && <Alert message={locationError} showIcon type="error" />}
      <Alert
        className="payroll-formula-alert"
        description="Employee tính theo giờ chấm công đã duyệt. Manager nhận lương cố định và chỉ được Admin đưa vào bảng lương khi chọn trọn một tháng. Manager chỉ quản lý lương Employee trong chi nhánh."
        message="Công thức tính lương dự tính"
        showIcon
        type="info"
      />

      <section className="management-card payroll-filter-panel">
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
        <Select
          allowClear={isAdmin}
          disabled={!isAdmin}
          loading={locationsLoading}
          onChange={(value) => setFilterInputs((current) => ({
            ...current,
            locationId: value,
          }))}
          options={locations.map((location) => ({
            label: location.code ? `${location.code} — ${location.name}` : location.name,
            value: location.id,
          }))}
          placeholder="Chọn chi nhánh"
          showSearch
          optionFilterProp="label"
          value={filterInputs.locationId}
        />
        <Button onClick={applyFilters}>Xem dữ liệu</Button>
      </section>

      <section className="attendance-management-summary-grid">
        <article className="management-card attendance-summary-card">
          <TeamOutlined />
          <div><span>Nhân viên</span><strong>{summary.employees}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <ClockCircleOutlined />
          <div><span>Tổng giờ thực tế</span><strong>{formatHours(summary.workedMinutes)}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <DollarOutlined />
          <div><span>Tổng thưởng</span><strong>{formatCurrency(summary.bonusAmount)}</strong></div>
        </article>
        <article className="management-card attendance-summary-card">
          <CheckCircleOutlined />
          <div>
            <span>Tổng lương · đã chốt {summary.confirmed}/{summary.employees}</span>
            <strong>{formatCurrency(summary.totalAmount)}</strong>
          </div>
        </article>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách bảng lương</strong>
            <span>{records.length} nhân viên trong kỳ đã chọn</span>
          </div>
        </div>
        <Table
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: 'Chưa có bảng lương. Hãy chọn chi nhánh và bấm “Tính lại bảng lương”.' }}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          rowKey="id"
          scroll={{ x: 1500 }}
        />
      </section>

      <PayrollBonusModal
        onClose={() => setEditingRecord(null)}
        onSubmit={handleBonus}
        open={Boolean(editingRecord)}
        record={editingRecord}
      />
    </>
  )
}
