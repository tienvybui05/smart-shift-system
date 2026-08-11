import { Button, Form, Input, Modal, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

export default function ResetPasswordModal({ open, user, onClose, onSubmit }) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) form.resetFields()
  }, [form, open, user])

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit(values)
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      const fields = Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      }))
      if (fields.length > 0) form.setFields(fields)
      messageApi.error(getApiErrorMessage(error, 'Không thể reset mật khẩu.'))
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
        onCancel={onClose}
        open={open}
        title="Reset mật khẩu"
      >
        <p className="modal-description">
          Đặt mật khẩu mới cho <strong>{user?.fullName}</strong>. Nhân viên sẽ dùng mật khẩu này ở lần đăng nhập tiếp theo.
        </p>
        <Form form={form} layout="vertical" onFinish={handleFinish} requiredMark="optional">
          <Form.Item
            label="Mật khẩu mới"
            name="newPassword"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu mới' },
              { min: 8, max: 72, message: 'Mật khẩu phải từ 8 đến 72 ký tự' },
            ]}
          >
            <Input.Password placeholder="Tối thiểu 8 ký tự" />
          </Form.Item>
          <Form.Item
            dependencies={['newPassword']}
            label="Xác nhận mật khẩu"
            name="confirmPassword"
            rules={[
              { required: true, message: 'Vui lòng xác nhận mật khẩu' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) return Promise.resolve()
                  return Promise.reject(new Error('Xác nhận mật khẩu không khớp'))
                },
              }),
            ]}
          >
            <Input.Password placeholder="Nhập lại mật khẩu mới" />
          </Form.Item>
          <div className="modal-actions">
            <Button onClick={onClose} disabled={submitting}>Hủy</Button>
            <Button type="primary" htmlType="submit" loading={submitting}>Xác nhận</Button>
          </div>
        </Form>
      </Modal>
    </>
  )
}
