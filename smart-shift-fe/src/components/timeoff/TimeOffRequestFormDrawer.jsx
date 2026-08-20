import { Alert, Button, Col, Drawer, Form, Input, Row, Select, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

const LEAVE_TYPE_OPTIONS = [
  { value: 'ANNUAL', label: 'Nghỉ phép năm' },
  { value: 'SICK', label: 'Nghỉ ốm' },
  { value: 'UNPAID', label: 'Nghỉ không lương' },
  { value: 'OTHER', label: 'Lý do khác' },
]

function toLocalDateTimeInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function getDefaultValues() {
  const startAt = new Date()
  startAt.setDate(startAt.getDate() + 1)
  startAt.setHours(8, 0, 0, 0)

  const endAt = new Date(startAt)
  endAt.setHours(17, 0, 0, 0)

  return {
    startAt: toLocalDateTimeInputValue(startAt),
    endAt: toLocalDateTimeInputValue(endAt),
    leaveType: 'ANNUAL',
    reason: '',
  }
}

export default function TimeOffRequestFormDrawer({ open, onClose, onSubmit }) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) form.setFieldsValue(getDefaultValues())
  }, [form, open])

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit({
        startAt: new Date(values.startAt).toISOString(),
        endAt: new Date(values.endAt).toISOString(),
        leaveType: values.leaveType,
        reason: values.reason?.trim() || null,
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
      messageApi.error(getApiErrorMessage(error, 'Không thể gửi đơn xin nghỉ.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="time-off-form-drawer"
        destroyOnHidden
        extra={(
          <div className="drawer-actions">
            <Button disabled={submitting} onClick={handleClose}>Hủy</Button>
            <Button
              form="time-off-request-form"
              htmlType="submit"
              loading={submitting}
              type="primary"
            >
              Gửi đơn
            </Button>
          </div>
        )}
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title="Tạo đơn xin nghỉ"
      >
        <Alert
          className="time-off-help-alert"
          description="Đơn đang chờ duyệt có thể hủy. Sau khi được duyệt hoặc từ chối, đơn sẽ được lưu lại và không thể thay đổi."
          message="Kiểm tra kỹ khoảng thời gian trước khi gửi"
          showIcon
          type="info"
        />

        <Form
          form={form}
          id="time-off-request-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="Loại nghỉ"
                name="leaveType"
                rules={[{ required: true, message: 'Vui lòng chọn loại nghỉ' }]}
              >
                <Select options={LEAVE_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Bắt đầu nghỉ"
                name="startAt"
                rules={[
                  { required: true, message: 'Vui lòng chọn thời gian bắt đầu' },
                  {
                    validator(_, value) {
                      if (!value || new Date(value).getTime() > Date.now()) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error('Thời gian bắt đầu phải ở tương lai'))
                    },
                  },
                ]}
              >
                <Input step="60" type="datetime-local" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                dependencies={['startAt']}
                label="Kết thúc nghỉ"
                name="endAt"
                rules={[
                  { required: true, message: 'Vui lòng chọn thời gian kết thúc' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const startAt = getFieldValue('startAt')
                      if (!value || !startAt || new Date(value) > new Date(startAt)) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error('Thời gian kết thúc phải sau thời gian bắt đầu'))
                    },
                  }),
                ]}
              >
                <Input step="60" type="datetime-local" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                label="Lý do"
                name="reason"
                rules={[{ max: 1000, message: 'Lý do không được vượt quá 1000 ký tự' }]}
              >
                <Input.TextArea
                  maxLength={1000}
                  placeholder="Mô tả ngắn gọn để quản lý xem xét (không bắt buộc)"
                  rows={5}
                  showCount
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  )
}
