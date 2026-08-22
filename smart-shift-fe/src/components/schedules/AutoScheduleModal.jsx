import {
  CalendarOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Modal,
  Progress,
  Table,
  Tag,
} from 'antd'
import { useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { generateAutomaticSchedule } from '../../services/autoScheduleService.js'

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function formatTime(value) {
  return value?.slice(0, 5) || '--:--'
}

function formatNumber(value, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits,
  }).format(Number(value || 0))
}

function formatPercentage(value) {
  return `${formatNumber(value)}%`
}

function ReasonsList({ reasons }) {
  if (!reasons?.length) return '—'
  return (
    <ul className="auto-schedule-reasons">
      {reasons.map((reason, index) => (
        <li key={`${index}-${reason}`}>{reason}</li>
      ))}
    </ul>
  )
}

export default function AutoScheduleModal({
  schedulePeriod,
  onClose,
  onGenerated,
  onManageShifts,
}) {
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  async function handleGenerate() {
    setGenerating(true)
    setError('')
    try {
      const generatedResult = await generateAutomaticSchedule(schedulePeriod.id)
      setResult(generatedResult)
      onGenerated(generatedResult)
    } catch (requestError) {
      setError(getApiErrorMessage(
        requestError,
        'Không thể tự động xếp lịch. Vui lòng kiểm tra lại dữ liệu ca làm.',
      ))
    } finally {
      setGenerating(false)
    }
  }

  function handleClose() {
    if (!generating) onClose()
  }

  function handleManageShifts() {
    onClose()
    onManageShifts(schedulePeriod.id)
  }

  const assignmentColumns = [
    {
      title: 'Ca làm',
      key: 'shift',
      width: 210,
      render: (_, assignment) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{formatDate(assignment.workDate)}</strong>
          <span>
            {formatTime(assignment.startTime)}–{formatTime(assignment.endTime)}
            {assignment.endsNextDay ? ' (hôm sau)' : ''}
          </span>
        </div>
      ),
    },
    {
      title: 'Nhân viên',
      key: 'employee',
      width: 185,
      render: (_, assignment) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{assignment.employeeName}</strong>
          <span>{assignment.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionName',
      width: 140,
    },
    {
      title: 'Điểm',
      dataIndex: 'score',
      width: 80,
      align: 'center',
      render: (score) => <Tag color="purple">{formatNumber(score)}</Tag>,
    },
    {
      title: 'Lý do lựa chọn',
      dataIndex: 'selectionReasons',
      render: (reasons) => <ReasonsList reasons={reasons} />,
    },
  ]

  const shortageColumns = [
    {
      title: 'Ca làm',
      key: 'shift',
      width: 220,
      render: (_, shortage) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{shortage.shiftName}</strong>
          <span>
            {formatDate(shortage.workDate)} · {formatTime(shortage.startTime)}–
            {formatTime(shortage.endTime)}
            {shortage.endsNextDay ? ' (hôm sau)' : ''}
          </span>
        </div>
      ),
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionName',
      width: 140,
    },
    {
      title: 'Đã xếp / Cần',
      key: 'staffing',
      width: 110,
      align: 'center',
      render: (_, shortage) => (
        <strong className="auto-schedule-staffing-gap">
          {shortage.assignedEmployees}/{shortage.minimumEmployees}
        </strong>
      ),
    },
    {
      title: 'Còn thiếu',
      dataIndex: 'missingEmployees',
      width: 90,
      align: 'center',
      render: (missingEmployees) => (
        <Tag color="error">{missingEmployees} người</Tag>
      ),
    },
    {
      title: 'Nguyên nhân',
      dataIndex: 'reasons',
      render: (reasons) => <ReasonsList reasons={reasons} />,
    },
  ]

  const resultHasWarnings = Boolean(
    result?.shortages?.length || result?.warnings?.length,
  )

  return (
    <Modal
      centered
      className="auto-schedule-modal"
      closable={!generating}
      maskClosable={!generating}
      onCancel={handleClose}
      open
      title={result
        ? `Kết quả xếp lịch · ${schedulePeriod.name}`
        : `Tự động xếp lịch · ${schedulePeriod.name}`}
      width={980}
      footer={result ? [
        <Button key="close" onClick={handleClose}>
          Đóng
        </Button>,
        <Button
          key="manage"
          icon={<CalendarOutlined />}
          type="primary"
          onClick={handleManageShifts}
        >
          Kiểm tra và điều chỉnh ca
        </Button>,
      ] : [
        <Button key="cancel" disabled={generating} onClick={handleClose}>
          Hủy
        </Button>,
        <Button
          key="generate"
          icon={<ThunderboltOutlined />}
          loading={generating}
          type="primary"
          onClick={handleGenerate}
        >
          {error ? 'Thử lại' : 'Bắt đầu xếp lịch'}
        </Button>,
      ]}
    >
      {!result ? (
        <div className="auto-schedule-confirmation">
          <Alert
            description="Hệ thống giữ nguyên các phân công hiện có và chỉ bổ sung nhân viên cho những vị trí chưa đủ số lượng tối thiểu. Các phân công mới sẽ được lưu ngay và có thể điều chỉnh thủ công trước khi công bố."
            message="Thao tác này sẽ tạo phân công AUTO"
            showIcon
            type="warning"
          />

          <div className="auto-schedule-period-card">
            <div>
              <span>Kỳ xếp lịch</span>
              <strong>{schedulePeriod.name}</strong>
            </div>
            <div>
              <span>Chi nhánh</span>
              <strong>{schedulePeriod.locationName}</strong>
            </div>
            <div>
              <span>Khoảng ngày</span>
              <strong>
                {formatDate(schedulePeriod.startDate)} – {formatDate(schedulePeriod.endDate)}
              </strong>
            </div>
          </div>

          <div className="auto-schedule-rule-note">
            <strong>Hệ thống sẽ kiểm tra</strong>
            <ul>
              <li>Lịch rảnh, thời gian nghỉ phép và ca bị trùng.</li>
              <li>Chi nhánh, vị trí, thời gian nghỉ giữa ca và giới hạn giờ làm.</li>
              <li>Ưu tiên nguyện vọng PREFERRED và cân bằng tổng giờ trong tuần.</li>
            </ul>
          </div>

          {error && <Alert message={error} showIcon type="error" />}
        </div>
      ) : (
        <div className="auto-schedule-result">
          <Alert
            description={resultHasWarnings
              ? 'Hãy xem các cảnh báo và ca còn thiếu người trước khi công bố lịch.'
              : 'Các phân công mới đã được lưu. Bạn có thể kiểm tra và điều chỉnh thủ công.'}
            message={result.assignmentsCreated > 0
              ? `Đã tạo ${result.assignmentsCreated} phân công tự động`
              : 'Không tạo thêm phân công'}
            showIcon
            type={resultHasWarnings ? 'warning' : 'success'}
          />

          <div className="auto-schedule-summary-grid">
            <div>
              <span>Phân công mới</span>
              <strong>{result.assignmentsCreated}</strong>
            </div>
            <div>
              <span>Đã xếp / Tối thiểu</span>
              <strong>
                {result.assignedEmployeesAfter}/{result.totalRequiredEmployees}
              </strong>
            </div>
            <div>
              <span>Ca đã đủ người</span>
              <strong>{result.fullyStaffedShifts}/{result.activeShifts}</strong>
            </div>
            <div>
              <span>Vị trí còn thiếu</span>
              <strong>{result.unfilledPositions}</strong>
            </div>
            <div className="auto-schedule-score-card">
              <span>Độ phủ nhân sự</span>
              <Progress
                percent={Number(result.coveragePercentage)}
                showInfo={false}
                size="small"
                status={result.coveragePercentage >= 100 ? 'success' : 'active'}
              />
              <strong>{formatPercentage(result.coveragePercentage)}</strong>
            </div>
            <div className="auto-schedule-score-card">
              <span>Điểm chất lượng</span>
              <Progress
                percent={Number(result.qualityScore)}
                showInfo={false}
                size="small"
                strokeColor="#7568d8"
              />
              <strong>{formatPercentage(result.qualityScore)}</strong>
            </div>
          </div>

          {result.warnings?.length > 0 && (
            <div className="auto-schedule-alert-list">
              {result.warnings.map((warning) => (
                <Alert key={warning} message={warning} showIcon type="warning" />
              ))}
            </div>
          )}

          <section className="auto-schedule-section">
            <div className="auto-schedule-section-heading">
              <div>
                <strong>Phân công vừa tạo</strong>
                <span>Danh sách này chỉ bao gồm kết quả của lần chạy hiện tại.</span>
              </div>
              <Tag color="success">{result.assignments.length} phân công</Tag>
            </div>
            <Table
              columns={assignmentColumns}
              dataSource={result.assignments}
              locale={{ emptyText: 'Không có phân công mới trong lần chạy này.' }}
              pagination={{ pageSize: 6, showSizeChanger: false }}
              rowKey="assignmentId"
              scroll={{ x: 800 }}
              size="small"
            />
          </section>

          {result.shortages?.length > 0 && (
            <section className="auto-schedule-section auto-schedule-section--shortage">
              <div className="auto-schedule-section-heading">
                <div>
                  <strong>Ca còn thiếu nhân sự</strong>
                  <span>Thuật toán không tạo phân công vi phạm chỉ để lấp đủ chỗ trống.</span>
                </div>
                <Tag color="error">{result.shortages.length} vị trí</Tag>
              </div>
              <Table
                columns={shortageColumns}
                dataSource={result.shortages}
                pagination={{ pageSize: 6, showSizeChanger: false }}
                rowKey={(shortage) => `${shortage.workShiftId}-${shortage.positionId}`}
                scroll={{ x: 820 }}
                size="small"
              />
            </section>
          )}
        </div>
      )}
    </Modal>
  )
}
