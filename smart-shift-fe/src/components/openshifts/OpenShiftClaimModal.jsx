import { SendOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input, Modal, Tag } from 'antd'
import { useState } from 'react'
import {
  AVAILABILITY_CONFIG,
  formatOpenShiftDate,
  formatOpenShiftHours,
  formatOpenShiftTime,
} from './openShiftDisplay.js'

export default function OpenShiftClaimModal({ openShift, onClose, onSubmit }) {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const availability = AVAILABILITY_CONFIG[openShift.availabilityType] || {
    label: openShift.availabilityType,
    color: 'default',
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit({
        workShiftId: openShift.workShiftId,
        reason: values.reason?.trim() || null,
      })
      form.resetFields()
      onClose()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      centered
      className="open-shift-claim-modal"
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose}>Hủy</Button>,
        <Button
          key="submit"
          form="open-shift-claim-form"
          htmlType="submit"
          icon={<SendOutlined />}
          loading={submitting}
          type="primary"
        >
          Gửi yêu cầu
        </Button>,
      ]}
      onCancel={onClose}
      open
      title="Đăng ký nhận ca trống"
      width={620}
    >
      <div className="open-shift-claim-content">
        <Alert
          description="Quản lý sẽ kiểm tra lại điều kiện tại thời điểm xét duyệt. Yêu cầu được duyệt sẽ trở thành một phân công chính thức trong lịch của bạn."
          message="Ca thuộc kỳ lịch đang chuẩn bị"
          showIcon
          type="info"
        />

        <div className="open-shift-detail-grid">
          <div>
            <span>Ca làm</span>
            <strong>{openShift.shiftName}</strong>
          </div>
          <div>
            <span>Ngày làm</span>
            <strong>{formatOpenShiftDate(openShift.workDate)}</strong>
          </div>
          <div>
            <span>Thời gian</span>
            <strong>
              {formatOpenShiftTime(openShift.startTime)}–
              {formatOpenShiftTime(openShift.endTime)}
              {openShift.endsNextDay ? ' (hôm sau)' : ''}
            </strong>
          </div>
          <div>
            <span>Thời lượng</span>
            <strong>{formatOpenShiftHours(openShift.workMinutes)}</strong>
          </div>
          <div>
            <span>Vị trí</span>
            <strong>{openShift.positionName}</strong>
          </div>
          <div>
            <span>Mức phù hợp</span>
            <strong><Tag color={availability.color}>{availability.label}</Tag></strong>
          </div>
        </div>

        <Form
          form={form}
          id="open-shift-claim-form"
          layout="vertical"
          onFinish={handleFinish}
        >
          <Form.Item
            label="Lý do muốn nhận ca"
            name="reason"
            rules={[{
              max: 1000,
              message: 'Lý do không được vượt quá 1000 ký tự.',
            }]}
          >
            <Input.TextArea
              maxLength={1000}
              placeholder="Ví dụ: Tôi muốn đăng ký thêm giờ trong tuần này."
              rows={4}
              showCount
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  )
}
