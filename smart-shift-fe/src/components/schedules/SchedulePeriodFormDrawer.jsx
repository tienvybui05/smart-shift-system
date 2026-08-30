import { Alert, Button, Col, Drawer, Form, Input, Row, Select, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatShortDate(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function getDefaultValues(locations) {
  const today = new Date()
  const daysUntilMonday = (8 - today.getDay()) % 7
  const startDate = new Date(today)
  startDate.setDate(today.getDate() + daysUntilMonday)
  const endDate = new Date(startDate)
  endDate.setDate(startDate.getDate() + 6)

  const startValue = toDateInputValue(startDate)
  const endValue = toDateInputValue(endDate)
  return {
    locationId: locations.find((location) => location.active)?.id,
    name: `Lịch tuần ${formatShortDate(startValue)} – ${formatShortDate(endValue)}`,
    startDate: startValue,
    endDate: endValue,
    changeReason: '',
  }
}

function getEditValues(schedulePeriod) {
  return {
    locationId: schedulePeriod.locationId,
    name: schedulePeriod.name,
    startDate: schedulePeriod.startDate,
    endDate: schedulePeriod.endDate,
    changeReason: '',
  }
}

export default function SchedulePeriodFormDrawer({
  open,
  schedulePeriod,
  locations,
  locationLocked = false,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const editing = Boolean(schedulePeriod)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue(
      schedulePeriod ? getEditValues(schedulePeriod) : getDefaultValues(locations),
    )
  }, [form, locations, open, schedulePeriod])

  const locationOptions = useMemo(
    () => locations
      .filter((location) => location.active || location.id === schedulePeriod?.locationId)
      .map((location) => ({
        label: `${location.code ? `${location.code} — ` : ''}${location.name}${location.active ? '' : ' (Ngừng hoạt động)'}`,
        value: location.id,
        disabled: !location.active,
      })),
    [locations, schedulePeriod?.locationId],
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
        changeReason: values.changeReason?.trim() || null,
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
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu kỳ xếp lịch.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="schedule-period-form-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title={editing ? `Cập nhật ${schedulePeriod.name}` : 'Tạo kỳ xếp lịch'}
        extra={(
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button
              type="primary"
              htmlType="submit"
              form="schedule-period-form"
              loading={submitting}
            >
              {editing ? 'Lưu thay đổi' : 'Tạo kỳ lịch'}
            </Button>
          </div>
        )}
      >
        <Alert
          className="schedule-period-alert"
          message="Kỳ mới được tạo ở trạng thái Nháp"
          description="Sau khi tạo kỳ, bước tiếp theo là sinh các ca làm thực tế và khai báo nhu cầu nhân sự."
          showIcon
          type="info"
        />

        <Form
          form={form}
          id="schedule-period-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <div className="form-section-intro">
            <strong>Thông tin kỳ xếp lịch</strong>
            <span>Mỗi kỳ xác định một khoảng ngày lập lịch riêng cho một chi nhánh.</span>
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
                  placeholder="Chọn chi nhánh cần lập lịch"
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                label="Tên kỳ xếp lịch"
                name="name"
                rules={[
                  { required: true, whitespace: true, message: 'Vui lòng nhập tên kỳ xếp lịch' },
                  { max: 100, message: 'Tên không được vượt quá 100 ký tự' },
                ]}
              >
                <Input placeholder="Ví dụ: Lịch tuần 12/08 – 18/08" maxLength={100} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Ngày bắt đầu"
                name="startDate"
                rules={[{ required: true, message: 'Vui lòng chọn ngày bắt đầu' }]}
              >
                <Input type="date" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                dependencies={['startDate']}
                label="Ngày kết thúc"
                name="endDate"
                rules={[
                  { required: true, message: 'Vui lòng chọn ngày kết thúc' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const startDate = getFieldValue('startDate')
                      if (!value || !startDate || value >= startDate) return Promise.resolve()
                      return Promise.reject(new Error('Ngày kết thúc không được trước ngày bắt đầu'))
                    },
                  }),
                ]}
              >
                <Input type="date" />
              </Form.Item>
            </Col>
          </Row>
          {editing && (
            <Form.Item
              label="Lý do chỉnh sửa"
              name="changeReason"
              rules={[
                { required: true, whitespace: true, message: 'Vui lòng nhập lý do chỉnh sửa' },
                { max: 500, message: 'Lý do không được vượt quá 500 ký tự' },
              ]}
            >
              <Input.TextArea
                maxLength={500}
                placeholder="Ví dụ: Điều chỉnh phạm vi lịch theo kế hoạch vận hành"
                rows={3}
                showCount
              />
            </Form.Item>
          )}
        </Form>
      </Drawer>
    </>
  )
}
