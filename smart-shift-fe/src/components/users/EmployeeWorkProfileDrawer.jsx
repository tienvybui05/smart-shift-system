import { Button, Col, Divider, Drawer, Form, Input, InputNumber, Row, Select, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

const EMPLOYMENT_TYPE_OPTIONS = [
  { label: 'Toàn thời gian', value: 'FULL_TIME' },
  { label: 'Bán thời gian', value: 'PART_TIME' },
  { label: 'Thời vụ', value: 'SEASONAL' },
]

export default function EmployeeWorkProfileDrawer({ open, user, positions, onClose, onSubmit }) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !user) return
    form.setFieldsValue({
      positionId: user.positionId,
      employmentType: user.employmentType,
      minHoursPerWeek: Number(user.minHoursPerWeek),
      maxHoursPerWeek: Number(user.maxHoursPerWeek),
      maxHoursPerDay: Number(user.maxHoursPerDay),
      minRestHours: Number(user.minRestHours),
      maxConsecutiveDays: user.maxConsecutiveDays,
    })
  }, [form, open, user])

  async function handleFinish(values) {
    setSubmitting(true)
    try {
      await onSubmit(values)
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      form.setFields(Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      })))
      messageApi.error(getApiErrorMessage(error, 'Không thể cập nhật thông tin công việc.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="user-form-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={onClose}
        open={open}
        size="large"
        title={`Cập nhật công việc · ${user?.fullName || ''}`}
        extra={(
          <div className="drawer-actions">
            <Button onClick={onClose} disabled={submitting}>Hủy</Button>
            <Button type="primary" htmlType="submit" form="employee-work-profile-form" loading={submitting}>
              Lưu thay đổi
            </Button>
          </div>
        )}
      >
        <div className="form-section-intro">
          <strong>Thông tin nhân viên</strong>
          <span>Thông tin tài khoản, chi nhánh và lương chỉ do Admin quản lý.</span>
        </div>
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item label="Mã nhân viên">
              <Input disabled value={user?.employeeCode} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item label="Chi nhánh">
              <Input disabled value={user?.locationName} />
            </Form.Item>
          </Col>
        </Row>

        <Divider />
        <Form
          form={form}
          id="employee-work-profile-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <div className="form-section-intro">
            <strong>Công việc và hợp đồng</strong>
            <span>Manager chỉ được điều chỉnh thông tin phục vụ vận hành và xếp lịch.</span>
          </div>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item label="Vị trí" name="positionId" rules={[{ required: true, message: 'Vui lòng chọn vị trí' }]}>
                <Select
                  options={positions
                    .filter((position) => position.active || position.id === user?.positionId)
                    .map((position) => ({
                      label: `${position.code} — ${position.name}`,
                      value: position.id,
                      disabled: !position.active,
                    }))}
                  placeholder="Chọn vị trí"
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Loại hợp đồng" name="employmentType" rules={[{ required: true, message: 'Vui lòng chọn loại hợp đồng' }]}>
                <Select options={EMPLOYMENT_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <Divider />
          <div className="form-section-intro">
            <strong>Quy tắc giờ làm</strong>
            <span>Các giới hạn này được sử dụng khi kiểm tra và tự động xếp lịch.</span>
          </div>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                dependencies={['maxHoursPerWeek']}
                label="Giờ tối thiểu/tuần"
                name="minHoursPerWeek"
                rules={[
                  { required: true, message: 'Vui lòng nhập số giờ tối thiểu' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const maximum = getFieldValue('maxHoursPerWeek')
                      if (value == null || maximum == null || value <= maximum) return Promise.resolve()
                      return Promise.reject(new Error('Không được lớn hơn giờ tối đa/tuần'))
                    },
                  }),
                ]}
              >
                <InputNumber min={0} max={168} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                dependencies={['minHoursPerWeek']}
                label="Giờ tối đa/tuần"
                name="maxHoursPerWeek"
                rules={[
                  { required: true, message: 'Vui lòng nhập số giờ tối đa' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const minimum = getFieldValue('minHoursPerWeek')
                      if (value == null || minimum == null || value >= minimum) return Promise.resolve()
                      return Promise.reject(new Error('Không được nhỏ hơn giờ tối thiểu/tuần'))
                    },
                  }),
                ]}
              >
                <InputNumber min={0.01} max={168} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Giờ tối đa/ngày" name="maxHoursPerDay" rules={[{ required: true, message: 'Vui lòng nhập giới hạn' }]}>
                <InputNumber min={0.01} max={24} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Giờ nghỉ tối thiểu" name="minRestHours" rules={[{ required: true, message: 'Vui lòng nhập thời gian nghỉ' }]}>
                <InputNumber min={0} max={24} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Ngày làm liên tiếp tối đa" name="maxConsecutiveDays" rules={[{ required: true, message: 'Vui lòng nhập số ngày' }]}>
                <InputNumber min={1} max={7} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  )
}
