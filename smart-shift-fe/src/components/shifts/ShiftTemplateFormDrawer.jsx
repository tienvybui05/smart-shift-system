import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Switch, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

const DEFAULT_COLOR = '#5B4CF0'

function normalizeTime(time) {
  return time ? time.slice(0, 5) : ''
}

function getDurationMinutes(startTime, endTime) {
  if (!startTime || !endTime || startTime === endTime) return 0

  const [startHour, startMinute] = startTime.split(':').map(Number)
  const [endHour, endMinute] = endTime.split(':').map(Number)
  const start = startHour * 60 + startMinute
  const end = endHour * 60 + endMinute
  return end > start ? end - start : 24 * 60 - start + end
}

function getDefaultValues(locations) {
  return {
    locationId: locations.find((location) => location.active)?.id,
    name: '',
    startTime: '06:00',
    endTime: '14:00',
    breakMinutes: 30,
    colorCode: DEFAULT_COLOR,
    active: true,
  }
}

function getEditValues(shiftTemplate) {
  return {
    locationId: shiftTemplate.locationId,
    name: shiftTemplate.name,
    startTime: normalizeTime(shiftTemplate.startTime),
    endTime: normalizeTime(shiftTemplate.endTime),
    breakMinutes: Number(shiftTemplate.breakMinutes),
    colorCode: shiftTemplate.colorCode || DEFAULT_COLOR,
    active: shiftTemplate.active,
  }
}

export default function ShiftTemplateFormDrawer({
  open,
  shiftTemplate,
  locations,
  locationLocked = false,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const editing = Boolean(shiftTemplate)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue(
      shiftTemplate ? getEditValues(shiftTemplate) : getDefaultValues(locations),
    )
  }, [form, locations, open, shiftTemplate])

  const locationOptions = useMemo(
    () => locations
      .filter((location) => location.active || location.id === shiftTemplate?.locationId)
      .map((location) => ({
        label: `${location.code ? `${location.code} — ` : ''}${location.name}${location.active ? '' : ' (Ngừng hoạt động)'}`,
        value: location.id,
        disabled: !location.active,
      })),
    [locations, shiftTemplate?.locationId],
  )

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit({
        ...values,
        name: values.name.trim(),
        colorCode: values.colorCode?.toUpperCase() || null,
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
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu mẫu ca.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="shift-template-form-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title={editing ? `Cập nhật ${shiftTemplate.name}` : 'Thêm mẫu ca mới'}
        extra={(
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button
              type="primary"
              htmlType="submit"
              form="shift-template-form"
              loading={submitting}
            >
              {editing ? 'Lưu thay đổi' : 'Tạo mẫu ca'}
            </Button>
          </div>
        )}
      >
        <Form
          form={form}
          id="shift-template-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <div className="form-section-intro">
            <strong>Thông tin mẫu ca</strong>
            <span>Mẫu ca là khung giờ dùng lại khi tạo lịch làm việc cho một chi nhánh.</span>
          </div>

          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="Chi nhánh"
                name="locationId"
                rules={[{ required: true, message: 'Vui lòng chọn chi nhánh' }]}
              >
                <Select
                  disabled={locationLocked}
                  showSearch
                  optionFilterProp="label"
                  options={locationOptions}
                  placeholder="Chọn chi nhánh áp dụng"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={16}>
              <Form.Item
                label="Tên mẫu ca"
                name="name"
                rules={[
                  { required: true, whitespace: true, message: 'Vui lòng nhập tên mẫu ca' },
                  { max: 50, message: 'Tên mẫu ca không được vượt quá 50 ký tự' },
                ]}
              >
                <Input placeholder="Ví dụ: Ca Sáng" maxLength={50} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item
                label="Màu hiển thị"
                name="colorCode"
                rules={[{
                  pattern: /^#[0-9A-Fa-f]{6}$/,
                  message: 'Màu phải có định dạng #RRGGBB',
                }]}
              >
                <Input className="shift-color-input" type="color" />
              </Form.Item>
            </Col>
          </Row>

          <div className="form-section-intro shift-time-section">
            <strong>Khung giờ làm việc</strong>
            <span>Nếu giờ kết thúc nhỏ hơn giờ bắt đầu, hệ thống hiểu đây là ca qua ngày.</span>
          </div>

          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item
                label="Giờ bắt đầu"
                name="startTime"
                rules={[{ required: true, message: 'Vui lòng chọn giờ bắt đầu' }]}
              >
                <Input type="time" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item
                dependencies={['startTime']}
                label="Giờ kết thúc"
                name="endTime"
                rules={[
                  { required: true, message: 'Vui lòng chọn giờ kết thúc' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || value !== getFieldValue('startTime')) return Promise.resolve()
                      return Promise.reject(new Error('Giờ kết thúc phải khác giờ bắt đầu'))
                    },
                  }),
                ]}
              >
                <Input type="time" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item
                dependencies={['startTime', 'endTime']}
                label="Thời gian nghỉ (phút)"
                name="breakMinutes"
                rules={[
                  { required: true, message: 'Vui lòng nhập thời gian nghỉ' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const duration = getDurationMinutes(
                        getFieldValue('startTime'),
                        getFieldValue('endTime'),
                      )
                      if (value == null || !duration || value < duration) return Promise.resolve()
                      return Promise.reject(new Error('Thời gian nghỉ phải nhỏ hơn thời lượng ca'))
                    },
                  }),
                ]}
              >
                <InputNumber min={0} max={1439} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="Trạng thái mẫu ca" name="active" valuePropName="checked">
            <Switch checkedChildren="Hoạt động" unCheckedChildren="Ngừng dùng" />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  )
}
