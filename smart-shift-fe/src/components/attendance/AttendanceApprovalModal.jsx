import { Alert, Form, Input, InputNumber, Modal, Segmented, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

function toDateTimeLocal(value) {
  if (!value) return ''
  const date = new Date(value)
  const timezoneOffset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - timezoneOffset).toISOString().slice(0, 16)
}

function formatScheduledTime(value, timezone) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: timezone || 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

export default function AttendanceApprovalModal({
  attendance,
  open,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const recordType = Form.useWatch('recordType', form)

  useEffect(() => {
    if (!open || !attendance) return
    form.setFieldsValue({
      recordType: attendance.status === 'ABSENT' ? 'ABSENT' : 'PRESENT',
      checkInAt: toDateTimeLocal(attendance.checkInAt),
      checkOutAt: toDateTimeLocal(attendance.checkOutAt),
      breakMinutes: Number(attendance.breakMinutes || 0),
      note: attendance.note || '',
    })
  }, [attendance, form, open])

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    const absent = values.recordType === 'ABSENT'
    const checkInAt = absent ? null : new Date(values.checkInAt)
    const checkOutAt = absent ? null : new Date(values.checkOutAt)

    if (!absent && checkOutAt <= checkInAt) {
      messageApi.error('Thời gian check-out phải sau thời gian check-in.')
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        checkInAt: absent ? null : checkInAt.toISOString(),
        checkOutAt: absent ? null : checkOutAt.toISOString(),
        breakMinutes: Number(values.breakMinutes),
        note: values.note?.trim() || null,
      })
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      const fields = Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      }))
      if (fields.length > 0) form.setFields(fields)
      messageApi.error(getApiErrorMessage(error, 'Không thể duyệt bản chấm công.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Modal
        cancelText="Hủy"
        destroyOnHidden
        maskClosable={!submitting}
        okButtonProps={{ htmlType: 'submit', form: 'attendance-approval-form' }}
        okText="Lưu và duyệt"
        confirmLoading={submitting}
        onCancel={handleClose}
        open={open}
        title="Duyệt và điều chỉnh chấm công"
        width={640}
      >
        <div className="attendance-approval-employee">
          <div>
            <strong>{attendance?.employeeName}</strong>
            <span>{attendance?.employeeCode} · {attendance?.locationName}</span>
          </div>
          <div>
            <span>Ca dự kiến</span>
            <strong>
              {formatScheduledTime(attendance?.shiftStartAt, attendance?.timezone)} –{' '}
              {formatScheduledTime(attendance?.shiftEndAt, attendance?.timezone)}
            </strong>
          </div>
        </div>

        <Alert
          className="attendance-approval-alert"
          message="Thời gian điều chỉnh được hiểu theo múi giờ trên thiết bị đang duyệt."
          showIcon
          type="info"
        />

        <Form
          form={form}
          id="attendance-approval-form"
          layout="vertical"
          onFinish={handleFinish}
          preserve={false}
          requiredMark="optional"
        >
          <Form.Item label="Kết quả chấm công" name="recordType">
            <Segmented
              block
              options={[
                { label: 'Có mặt', value: 'PRESENT' },
                { label: 'Vắng mặt', value: 'ABSENT' },
              ]}
            />
          </Form.Item>

          {recordType !== 'ABSENT' && (
            <div className="attendance-approval-time-grid">
              <Form.Item
                label="Thời gian check-in"
                name="checkInAt"
                rules={[{ required: true, message: 'Vui lòng nhập thời gian check-in' }]}
              >
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item
                label="Thời gian check-out"
                name="checkOutAt"
                rules={[{ required: true, message: 'Vui lòng nhập thời gian check-out' }]}
              >
                <Input type="datetime-local" />
              </Form.Item>
            </div>
          )}

          <Form.Item
            label="Thời gian nghỉ (phút)"
            name="breakMinutes"
            rules={[{ required: true, message: 'Vui lòng nhập thời gian nghỉ' }]}
          >
            <InputNumber min={0} max={1440} precision={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item label="Ghi chú của quản lý" name="note" rules={[{ max: 1000 }]}>
            <Input.TextArea
              maxLength={1000}
              placeholder="Lý do điều chỉnh hoặc ghi chú xác nhận"
              rows={3}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
