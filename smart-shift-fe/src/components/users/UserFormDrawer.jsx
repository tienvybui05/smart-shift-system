import { Button, Col, Divider, Drawer, Form, Input, InputNumber, Row, Select, Switch, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'

const ROLE_LABELS = {
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_MANAGER: 'Quản lý',
  ROLE_EMPLOYEE: 'Nhân viên',
}

const EMPLOYMENT_TYPE_OPTIONS = [
  { label: 'Toàn thời gian', value: 'FULL_TIME' },
  { label: 'Bán thời gian', value: 'PART_TIME' },
  { label: 'Thời vụ', value: 'SEASONAL' },
]

function getToday() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getDefaultValues() {
  return {
    employmentType: 'FULL_TIME',
    hireDate: getToday(),
    minHoursPerWeek: 40,
    maxHoursPerWeek: 48,
    maxHoursPerDay: 8,
    minRestHours: 8,
    maxConsecutiveDays: 6,
    hourlyRate: 0,
    salaryCoefficient: 1,
    active: true,
  }
}

function getEditValues(user) {
  return {
    employeeCode: user.employeeCode,
    username: user.username,
    fullName: user.fullName,
    email: user.email,
    phoneNumber: user.phoneNumber,
    roleId: user.roleId,
    locationId: user.locationId,
    positionId: user.positionId,
    employmentType: user.employmentType,
    hireDate: user.hireDate,
    minHoursPerWeek: Number(user.minHoursPerWeek),
    maxHoursPerWeek: Number(user.maxHoursPerWeek),
    maxHoursPerDay: Number(user.maxHoursPerDay),
    minRestHours: Number(user.minRestHours),
    maxConsecutiveDays: user.maxConsecutiveDays,
    hourlyRate: Number(user.hourlyRate || 0),
    salaryCoefficient: Number(user.salaryCoefficient || 1),
  }
}

export default function UserFormDrawer({
  open,
  user,
  references,
  usernameLocked = false,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [submitting, setSubmitting] = useState(false)
  const editing = Boolean(user)

  useEffect(() => {
    if (!open) return
    form.setFieldsValue(user ? getEditValues(user) : getDefaultValues())
  }, [form, open, user])

  const locationOptions = useMemo(
    () => references.locations
      .filter((location) => location.active || location.id === user?.locationId)
      .map((location) => ({
        label: `${location.code} — ${location.name}${location.active ? '' : ' (Ngừng hoạt động)'}`,
        value: location.id,
        disabled: !location.active,
      })),
    [references.locations, user?.locationId],
  )

  const positionOptions = useMemo(
    () => references.positions
      .filter((position) => position.active || position.id === user?.positionId)
      .map((position) => ({
        label: `${position.code} — ${position.name}${position.active ? '' : ' (Ngừng hoạt động)'}`,
        value: position.id,
        disabled: !position.active,
      })),
    [references.positions, user?.positionId],
  )

  const roleOptions = useMemo(
    () => references.roles.map((role) => ({
      label: ROLE_LABELS[role.name] || role.name,
      value: role.id,
    })),
    [references.roles],
  )

  function handleClose() {
    if (submitting) return
    form.resetFields()
    onClose()
  }

  async function handleFinish(values) {
    const payload = { ...values }
    delete payload.confirmPassword
    if (editing) {
      delete payload.password
      delete payload.active
    }

    setSubmitting(true)
    try {
      await onSubmit(payload)
      form.resetFields()
      onClose()
    } catch (error) {
      const fieldErrors = error.response?.data?.fieldErrors || {}
      const fields = Object.entries(fieldErrors).map(([name, errors]) => ({
        name,
        errors: [errors],
      }))
      if (fields.length > 0) form.setFields(fields)
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu thông tin nhân viên.'))
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
        onClose={handleClose}
        open={open}
        size="large"
        title={editing ? `Cập nhật ${user.fullName}` : 'Thêm nhân viên mới'}
        extra={(
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button type="primary" htmlType="submit" form="user-form" loading={submitting}>
              {editing ? 'Lưu thay đổi' : 'Tạo nhân viên'}
            </Button>
          </div>
        )}
      >
        <Form
          form={form}
          id="user-form"
          layout="vertical"
          onFinish={handleFinish}
          requiredMark="optional"
        >
          <div className="form-section-intro">
            <strong>Thông tin tài khoản</strong>
            <span>Thông tin định danh và thông tin dùng để đăng nhập hệ thống.</span>
          </div>

          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                label="Mã nhân viên"
                name="employeeCode"
                rules={[{ required: true, message: 'Vui lòng nhập mã nhân viên' }, { max: 30 }]}
              >
                <Input placeholder="Ví dụ: NV001" maxLength={30} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Tên đăng nhập"
                name="username"
                rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }, { max: 50 }]}
              >
                <Input
                  disabled={usernameLocked}
                  placeholder="Ví dụ: nguyenvana"
                  maxLength={50}
                />
              </Form.Item>
            </Col>
          </Row>

          {!editing && (
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item
                  label="Mật khẩu ban đầu"
                  name="password"
                  rules={[
                    { required: true, message: 'Vui lòng nhập mật khẩu ban đầu' },
                    { min: 8, max: 72, message: 'Mật khẩu phải từ 8 đến 72 ký tự' },
                  ]}
                >
                  <Input.Password placeholder="Tối thiểu 8 ký tự" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item
                  dependencies={['password']}
                  label="Xác nhận mật khẩu"
                  name="confirmPassword"
                  rules={[
                    { required: true, message: 'Vui lòng xác nhận mật khẩu' },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('password') === value) return Promise.resolve()
                        return Promise.reject(new Error('Xác nhận mật khẩu không khớp'))
                      },
                    }),
                  ]}
                >
                  <Input.Password placeholder="Nhập lại mật khẩu" />
                </Form.Item>
              </Col>
            </Row>
          )}

          <Divider />
          <div className="form-section-intro">
            <strong>Hồ sơ nhân viên</strong>
            <span>Thông tin liên hệ, vai trò và nơi làm việc chính.</span>
          </div>

          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="Họ và tên"
                name="fullName"
                rules={[{ required: true, message: 'Vui lòng nhập họ tên' }, { max: 100 }]}
              >
                <Input placeholder="Nguyễn Văn A" maxLength={100} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Email"
                name="email"
                rules={[{ type: 'email', message: 'Email không đúng định dạng' }, { max: 100 }]}
              >
                <Input placeholder="nhanvien@company.vn" maxLength={100} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Số điện thoại" name="phoneNumber" rules={[{ max: 20 }]}>
                <Input placeholder="0901234567" maxLength={20} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Vai trò" name="roleId" rules={[{ required: true, message: 'Vui lòng chọn vai trò' }]}>
                <Select options={roleOptions} placeholder="Chọn vai trò" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Chi nhánh" name="locationId" rules={[{ required: true, message: 'Vui lòng chọn chi nhánh' }]}>
                <Select showSearch optionFilterProp="label" options={locationOptions} placeholder="Chọn chi nhánh" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Vị trí" name="positionId" rules={[{ required: true, message: 'Vui lòng chọn vị trí' }]}>
                <Select showSearch optionFilterProp="label" options={positionOptions} placeholder="Chọn vị trí" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Loại hợp đồng" name="employmentType" rules={[{ required: true, message: 'Vui lòng chọn loại hợp đồng' }]}>
                <Select options={EMPLOYMENT_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Ngày vào làm" name="hireDate" rules={[{ required: true, message: 'Vui lòng chọn ngày vào làm' }]}>
                <Input type="date" max={getToday()} />
              </Form.Item>
            </Col>
          </Row>

          <Divider />
          <div className="form-section-intro">
            <strong>Thông tin tính lương</strong>
            <span>Lương dự tính được tính theo giờ thực tế × đơn giá × hệ số.</span>
          </div>

          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                label="Đơn giá theo giờ"
                name="hourlyRate"
                rules={[{ required: true, message: 'Vui lòng nhập đơn giá theo giờ' }]}
              >
                <InputNumber
                  addonAfter="đ/giờ"
                  min={0}
                  max={9999999999}
                  precision={2}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Hệ số lương"
                name="salaryCoefficient"
                rules={[{ required: true, message: 'Vui lòng nhập hệ số lương' }]}
              >
                <InputNumber
                  min={0.01}
                  max={10}
                  precision={2}
                  step={0.1}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Divider />
          <div className="form-section-intro">
            <strong>Quy tắc giờ làm</strong>
            <span>Các giới hạn này sẽ được thuật toán sử dụng khi xếp lịch.</span>
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

          {!editing && (
            <Form.Item label="Trạng thái tài khoản" name="active" valuePropName="checked">
              <Switch checkedChildren="Hoạt động" unCheckedChildren="Đã khóa" />
            </Form.Item>
          )}
        </Form>
      </Drawer>
    </>
  )
}
