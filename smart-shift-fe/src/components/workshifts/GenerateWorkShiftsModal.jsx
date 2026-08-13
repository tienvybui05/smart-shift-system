import { Alert, Form, Input, Modal, Select, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

function normalizeTime(value) {
  return value?.slice(0, 5) || ''
}

export default function GenerateWorkShiftsModal({
  open,
  schedulePeriod,
  shiftTemplates,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !schedulePeriod) return
    form.setFieldsValue({
      shiftTemplateIds: shiftTemplates.map((template) => template.id),
      startDate: schedulePeriod.startDate,
      endDate: schedulePeriod.endDate,
    })
  }, [form, open, schedulePeriod, shiftTemplates])

  const templateOptions = useMemo(
    () => shiftTemplates.map((template) => ({
      label: `${template.name} (${normalizeTime(template.startTime)} – ${normalizeTime(template.endTime)})`,
      value: template.id,
    })),
    [shiftTemplates],
  )

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit({ ...values, schedulePeriodId: schedulePeriod.id })
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      const fields = Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      }))
      if (fields.length > 0) form.setFields(fields)
      messageApi.error(getApiErrorMessage(error, 'Không thể sinh danh sách ca làm.'))
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
        okText="Sinh ca làm"
        confirmLoading={submitting}
        onCancel={handleClose}
        onOk={() => form.submit()}
        open={open}
        title="Sinh ca làm từ mẫu"
        width={620}
      >
        <Alert
          className="generate-shifts-alert"
          message="Hệ thống tạo mỗi mẫu ca đã chọn cho từng ngày trong khoảng"
          description="Những ca trùng giờ bắt đầu đã tồn tại sẽ được tự động bỏ qua."
          showIcon
          type="info"
        />
        <Form form={form} layout="vertical" onFinish={handleFinish} requiredMark="optional">
          <Form.Item
            label="Mẫu ca cần sinh"
            name="shiftTemplateIds"
            rules={[{ required: true, message: 'Vui lòng chọn ít nhất một mẫu ca' }]}
          >
            <Select
              mode="multiple"
              options={templateOptions}
              placeholder="Chọn các mẫu ca"
              maxTagCount="responsive"
            />
          </Form.Item>
          <div className="generate-date-grid">
            <Form.Item
              label="Từ ngày"
              name="startDate"
              rules={[{ required: true, message: 'Vui lòng chọn ngày bắt đầu' }]}
            >
              <Input
                type="date"
                min={schedulePeriod?.startDate}
                max={schedulePeriod?.endDate}
              />
            </Form.Item>
            <Form.Item
              dependencies={['startDate']}
              label="Đến ngày"
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
              <Input
                type="date"
                min={schedulePeriod?.startDate}
                max={schedulePeriod?.endDate}
              />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  )
}
