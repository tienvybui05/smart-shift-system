import { Button, Form, Input, InputNumber, Modal, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

export default function PayrollBonusModal({ open, record, onClose, onSubmit }) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !record) return
    form.setFieldsValue({
      bonusAmount: Number(record.bonusAmount || 0),
      bonusNote: record.bonusNote || '',
    })
  }, [form, open, record])

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit(values)
      form.resetFields()
      onClose()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể cập nhật tiền thưởng.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Modal
        destroyOnHidden
        footer={null}
        maskClosable={!submitting}
        onCancel={handleClose}
        open={open}
        title={`Cập nhật thưởng · ${record?.employeeName || ''}`}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <Form.Item
            label="Tiền thưởng"
            name="bonusAmount"
            rules={[{ required: true, message: 'Vui lòng nhập tiền thưởng' }]}
          >
            <InputNumber
              addonAfter="đồng"
              min={0}
              max={9999999999999}
              precision={2}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item label="Ghi chú thưởng" name="bonusNote" rules={[{ max: 500 }]}>
            <Input.TextArea
              maxLength={500}
              placeholder="Ví dụ: Thưởng hiệu suất trong kỳ"
              rows={3}
              showCount
            />
          </Form.Item>
          <div className="modal-actions">
            <Button disabled={submitting} onClick={handleClose}>Hủy</Button>
            <Button htmlType="submit" loading={submitting} type="primary">
              Lưu tiền thưởng
            </Button>
          </div>
        </Form>
      </Modal>
    </>
  )
}
