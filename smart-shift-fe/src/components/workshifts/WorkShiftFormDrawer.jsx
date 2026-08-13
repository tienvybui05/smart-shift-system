import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

function normalizeTime(value) {
  return value?.slice(0, 5) || ''
}

function getDurationMinutes(startTime, endTime) {
  if (!startTime || !endTime || startTime === endTime) return 0
  const [startHour, startMinute] = startTime.split(':').map(Number)
  const [endHour, endMinute] = endTime.split(':').map(Number)
  const start = startHour * 60 + startMinute
  const end = endHour * 60 + endMinute
  return end > start ? end - start : 24 * 60 - start + end
}

function valuesFromTemplate(template, workDate) {
  return {
    shiftTemplateId: template?.id,
    workDate,
    startTime: normalizeTime(template?.startTime),
    endTime: normalizeTime(template?.endTime),
    breakMinutes: Number(template?.breakMinutes || 0),
    note: '',
  }
}

function getEditValues(workShift) {
  return {
    shiftTemplateId: workShift.shiftTemplateId,
    workDate: workShift.workDate,
    startTime: normalizeTime(workShift.startTime),
    endTime: normalizeTime(workShift.endTime),
    breakMinutes: Number(workShift.breakMinutes),
    note: workShift.note,
  }
}

export default function WorkShiftFormDrawer({
  open,
  workShift,
  schedulePeriod,
  shiftTemplates,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const editing = Boolean(workShift)

  useEffect(() => {
    if (!open || !schedulePeriod) return
    form.setFieldsValue(
      workShift
        ? getEditValues(workShift)
        : valuesFromTemplate(shiftTemplates[0], schedulePeriod.startDate),
    )
  }, [form, open, schedulePeriod, shiftTemplates, workShift])

  const templateOptions = useMemo(
    () => shiftTemplates.map((template) => ({
      label: `${template.name} (${normalizeTime(template.startTime)} – ${normalizeTime(template.endTime)})`,
      value: template.id,
    })),
    [shiftTemplates],
  )

  function handleTemplateChange(templateId) {
    const template = shiftTemplates.find((item) => item.id === templateId)
    if (!template) return
    form.setFieldsValue({
      startTime: normalizeTime(template.startTime),
      endTime: normalizeTime(template.endTime),
      breakMinutes: Number(template.breakMinutes),
    })
  }

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
        schedulePeriodId: schedulePeriod.id,
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
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu ca làm.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="work-shift-form-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title={editing ? `Cập nhật ${workShift.shiftTemplateName}` : 'Thêm ca làm'}
        extra={(
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button
              type="primary"
              htmlType="submit"
              form="work-shift-form"
              loading={submitting}
            >
              {editing ? 'Lưu thay đổi' : 'Tạo ca làm'}
            </Button>
          </div>
        )}
      >
        <div className="form-section-intro">
          <strong>{schedulePeriod?.name}</strong>
          <span>{schedulePeriod?.locationName} · Chọn mẫu ca rồi điều chỉnh giờ nếu cần.</span>
        </div>

        <Form
          form={form}
          id="work-shift-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <Row gutter={16}>
            <Col xs={24} md={14}>
              <Form.Item
                label="Mẫu ca"
                name="shiftTemplateId"
                rules={[{ required: true, message: 'Vui lòng chọn mẫu ca' }]}
              >
                <Select
                  onChange={handleTemplateChange}
                  options={templateOptions}
                  placeholder="Chọn mẫu ca"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={10}>
              <Form.Item
                label="Ngày làm việc"
                name="workDate"
                rules={[{ required: true, message: 'Vui lòng chọn ngày làm việc' }]}
              >
                <Input
                  type="date"
                  min={schedulePeriod?.startDate}
                  max={schedulePeriod?.endDate}
                />
              </Form.Item>
            </Col>
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
            <Col span={24}>
              <Form.Item label="Ghi chú" name="note" rules={[{ max: 1000 }]}>
                <Input.TextArea rows={4} maxLength={1000} showCount placeholder="Ghi chú riêng cho ca này" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  )
}
