import {
  Alert,
  Button,
  Modal,
  Spin,
  Table,
  Tag,
  message,
} from 'antd'
import { CalendarOutlined, SendOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getSchedulePublicationCheck } from '../../services/schedulePeriodService.js'

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

export default function SchedulePublicationModal({
  open,
  schedulePeriod,
  onClose,
  onPublish,
  onManageShifts,
}) {
  const [messageApi, messageContext] = message.useMessage()
  const [publicationCheck, setPublicationCheck] = useState(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !schedulePeriod) return undefined
    let mounted = true
    getSchedulePublicationCheck(schedulePeriod.id)
      .then((result) => {
        if (mounted) setPublicationCheck(result)
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(
            error,
            'Không thể kiểm tra điều kiện công bố lịch.',
          ))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [messageApi, open, schedulePeriod])

  function handleClose() {
    if (submitting) return
    setPublicationCheck(null)
    setLoading(true)
    onClose()
  }

  async function handlePublish() {
    setSubmitting(true)
    try {
      await onPublish(schedulePeriod.id)
      setPublicationCheck(null)
      setLoading(true)
      onClose()
    } catch (error) {
      messageApi.error(getApiErrorMessage(
        error,
        'Không thể công bố lịch làm việc.',
      ))
    } finally {
      setSubmitting(false)
    }
  }

  function handleManageShifts() {
    setPublicationCheck(null)
    setLoading(true)
    onClose()
    onManageShifts(schedulePeriod.id)
  }

  const issueColumns = [
    {
      title: 'Ca làm',
      key: 'shift',
      width: 220,
      render: (_, issue) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{issue.shiftName}</strong>
          <span>
            {formatDate(issue.workDate)} · {formatTime(issue.startTime)}–
            {formatTime(issue.endTime)}
          </span>
        </div>
      ),
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionName',
      width: 150,
      render: (value) => value || <Tag>Chưa cấu hình</Tag>,
    },
    {
      title: 'Đã xếp / Cần',
      key: 'staffing',
      width: 115,
      align: 'center',
      render: (_, issue) => issue.issueCode === 'NO_REQUIREMENTS'
        ? '—'
        : (
          <strong className="publication-staffing-gap">
            {issue.assignedEmployees}/{issue.requiredEmployees}
          </strong>
        ),
    },
    {
      title: 'Vấn đề',
      dataIndex: 'message',
      render: (value) => <span className="publication-issue-message">{value}</span>,
    },
  ]

  return (
    <>
      {messageContext}
      <Modal
        centered
        className="schedule-publication-modal"
        closable={!submitting}
        maskClosable={!submitting}
        onCancel={handleClose}
        open={open}
        title={`Kiểm tra công bố · ${schedulePeriod?.name || ''}`}
        width={820}
        footer={[
          <Button key="close" disabled={submitting} onClick={handleClose}>
            Đóng
          </Button>,
          publicationCheck && !publicationCheck.canPublish && (
            <Button
              key="manage"
              icon={<CalendarOutlined />}
              onClick={handleManageShifts}
            >
              Xử lý ca làm
            </Button>
          ),
          <Button
            key="publish"
            disabled={!publicationCheck?.canPublish}
            icon={<SendOutlined />}
            loading={submitting}
            type="primary"
            onClick={handlePublish}
          >
            Công bố lịch
          </Button>,
        ].filter(Boolean)}
      >
        {loading ? (
          <div className="publication-loading"><Spin /></div>
        ) : publicationCheck ? (
          <div className="publication-content">
            <Alert
              message={publicationCheck.canPublish
                ? 'Lịch đã đáp ứng điều kiện công bố'
                : 'Lịch chưa thể công bố'}
              description={publicationCheck.canPublish
                ? 'Sau khi công bố, nhân viên sẽ thấy đây là lịch chính thức và Admin không thể sửa trực tiếp.'
                : 'Hãy xử lý toàn bộ ca thiếu nhu cầu, thiếu người hoặc có phân công vi phạm ràng buộc.'}
              showIcon
              type={publicationCheck.canPublish ? 'success' : 'warning'}
            />

            <div className="publication-summary-grid">
              <div><span>Ca hoạt động</span><strong>{publicationCheck.activeShifts}</strong></div>
              <div><span>Ca đã hủy</span><strong>{publicationCheck.cancelledShifts}</strong></div>
              <div><span>Nhân sự tối thiểu</span><strong>{publicationCheck.totalMinimumEmployees}</strong></div>
              <div><span>Đã phân công</span><strong>{publicationCheck.totalAssignedEmployees}</strong></div>
              <div><span>Phân công lỗi</span><strong>{publicationCheck.invalidAssignments}</strong></div>
            </div>

            {publicationCheck.blockers.map((blocker) => (
              <Alert key={blocker} message={blocker} showIcon type="error" />
            ))}

            {publicationCheck.shiftIssues.length > 0 && (
              <Table
                columns={issueColumns}
                dataSource={publicationCheck.shiftIssues}
                pagination={{ pageSize: 6, showSizeChanger: false }}
                rowKey={(issue) => `${issue.workShiftId}-${issue.positionId || 'none'}-${issue.issueCode}`}
                scroll={{ x: 700 }}
                size="small"
              />
            )}
          </div>
        ) : (
          <Alert message="Không có dữ liệu kiểm tra" showIcon type="error" />
        )}
      </Modal>
    </>
  )
}
