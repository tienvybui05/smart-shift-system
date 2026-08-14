import { Alert, Button, Col, Drawer, Form, Input, Row, Select, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

const TYPE_OPTIONS = [
  {
    value: 'AVAILABLE',
    label: 'Có thể làm',
    description: 'Bạn có thể nhận ca trong khung giờ này.',
  },
  {
    value: 'PREFERRED',
    label: 'Ưu tiên muốn làm',
    description: 'Hệ thống sẽ ưu tiên xếp bạn vào khung giờ này.',
  },
  {
    value: 'UNAVAILABLE',
    label: 'Không thể làm',
    description: 'Hệ thống không được xếp bạn trong khung giờ này.',
  },
]

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function normalizeTime(value) {
  return value?.slice(0, 5) || ''
}

function getDefaultValues() {
  return {
    availableDate: toDateInputValue(new Date()),
    startTime: '06:00',
    endTime: '14:00',
    availabilityType: 'AVAILABLE',
    note: '',
  }
}

function getEditValues(availability) {
  return {
    availableDate: availability.availableDate,
    startTime: normalizeTime(availability.startTime),
    endTime: normalizeTime(availability.endTime),
    availabilityType: availability.availabilityType,
    note: availability.note,
  }
}

export default function AvailabilityFormDrawer({
  open,
  availability,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const selectedType = Form.useWatch('availabilityType', form)
  const editing = Boolean(availability)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue(
      availability ? getEditValues(availability) : getDefaultValues(),
    )
  }, [availability, form, open])

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit({ ...values, note: values.note?.trim() || null })
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      const fields = Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      }))
      if (fields.length > 0) form.setFields(fields)
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu lịch rảnh.'))
    } finally {
      setSubmitting(false)
    }
  }

  const typeDescription = TYPE_OPTIONS.find(
    (option) => option.value === selectedType,
  )?.description

  return (
    <>
      {messageContext}
      <Drawer
        className="availability-form-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title={editing ? 'Cập nhật lịch rảnh' : 'Đăng ký lịch rảnh'}
        extra={(
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button
              type="primary"
              htmlType="submit"
              form="availability-form"
              loading={submitting}
            >
              {editing ? 'Lưu thay đổi' : 'Đăng ký'}
            </Button>
          </div>
        )}
      >
        <Alert
          className="availability-help-alert"
          message="Mỗi khung giờ phải nằm trong cùng một ngày"
          description="Các khung giờ của bạn không được chồng lên nhau. Bạn có thể đăng ký nhiều khung liên tiếp trong một ngày."
          showIcon
          type="info"
        />

        <Form
          form={form}
          id="availability-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="Ngày đăng ký"
                name="availableDate"
                rules={[{ required: true, message: 'Vui lòng chọn ngày' }]}
              >
                <Input type="date" min={toDateInputValue(new Date())} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Giờ bắt đầu"
                name="startTime"
                rules={[{ required: true, message: 'Vui lòng chọn giờ bắt đầu' }]}
              >
                <Input type="time" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                dependencies={['startTime']}
                label="Giờ kết thúc"
                name="endTime"
                rules={[
                  { required: true, message: 'Vui lòng chọn giờ kết thúc' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const startTime = getFieldValue('startTime')
                      if (!value || !startTime || value > startTime) return Promise.resolve()
                      return Promise.reject(new Error('Giờ kết thúc phải sau giờ bắt đầu'))
                    },
                  }),
                ]}
              >
                <Input type="time" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                label="Khả năng làm việc"
                name="availabilityType"
                rules={[{ required: true, message: 'Vui lòng chọn loại lịch rảnh' }]}
              >
                <Select options={TYPE_OPTIONS.map(({ value, label }) => ({ value, label }))} />
              </Form.Item>
              {typeDescription && <p className="availability-type-help">{typeDescription}</p>}
            </Col>
            <Col span={24}>
              <Form.Item label="Ghi chú" name="note" rules={[{ max: 1000 }]}>
                <Input.TextArea
                  rows={4}
                  maxLength={1000}
                  showCount
                  placeholder="Ví dụ: Có thể bắt đầu muộn hơn 30 phút nếu cần"
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  )
}
