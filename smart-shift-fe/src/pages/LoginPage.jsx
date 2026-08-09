import {
  ArrowRightOutlined,
  CalendarOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, Button, Checkbox, Form, Input } from 'antd'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../api/apiError.js'
import heroImage from '../assets/hero.png'
import useAuth from '../hooks/useAuth.js'

export default function LoginPage() {
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  async function handleSubmit(values) {
    setSubmitting(true)
    setErrorMessage('')

    try {
      await login(
        {
          username: values.username.trim(),
          password: values.password,
        },
        values.rememberMe,
      )
      const destination = location.state?.from?.pathname || '/'
      navigate(destination, { replace: true })
    } catch (error) {
      setErrorMessage(
        getApiErrorMessage(error, 'Đăng nhập thất bại. Vui lòng thử lại.'),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual" aria-label="Giới thiệu Smart Shift">
        <div className="visual-orb visual-orb--one" />
        <div className="visual-orb visual-orb--two" />

        <div className="login-brand">
          <div className="brand-mark">S</div>
          <div>
            <strong>Smart Shift</strong>
            <span>Workforce Management</span>
          </div>
        </div>

        <div className="visual-content">
          <div className="visual-badge">
            <SafetyCertificateOutlined />
            Vận hành thông minh, bảo mật
          </div>
          <h1>Xếp đúng người, đúng ca, đúng thời điểm.</h1>
          <p>
            Một không gian làm việc thống nhất để quản lý nhân sự, lịch làm
            việc và vận hành chuỗi cửa hàng hiệu quả hơn.
          </p>

          <div className="visual-features">
            <div>
              <CalendarOutlined />
              <span>
                <strong>Lịch làm trực quan</strong>
                Theo dõi mọi ca làm trong một nơi
              </span>
            </div>
            <div>
              <TeamOutlined />
              <span>
                <strong>Nhân sự đồng bộ</strong>
                Kết nối quản lý và nhân viên
              </span>
            </div>
          </div>
        </div>

        <img className="login-hero-image" src={heroImage} alt="" />
        <p className="visual-footer">© 2026 Smart Shift System</p>
      </section>

      <section className="login-form-panel">
        <div className="mobile-brand login-brand">
          <div className="brand-mark">S</div>
          <div>
            <strong>Smart Shift</strong>
            <span>Workforce Management</span>
          </div>
        </div>

        <div className="login-form-card">
          <div className="form-heading">
            <span className="eyebrow">Chào mừng trở lại</span>
            <h2>Đăng nhập hệ thống</h2>
            <p>Sử dụng tài khoản được doanh nghiệp cấp để tiếp tục.</p>
          </div>

          {errorMessage && (
            <Alert
              className="login-alert"
              type="error"
              showIcon
              message={errorMessage}
            />
          )}

          <Form
            layout="vertical"
            requiredMark={false}
            onFinish={handleSubmit}
            autoComplete="on"
            size="large"
            initialValues={{ rememberMe: true }}
          >
            <Form.Item
              label="Tên đăng nhập"
              name="username"
              rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="Nhập tên đăng nhập"
                autoComplete="username"
                autoFocus
              />
            </Form.Item>

            <Form.Item
              label="Mật khẩu"
              name="password"
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Nhập mật khẩu"
                autoComplete="current-password"
              />
            </Form.Item>

            <div className="form-options">
              <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                <Checkbox>Ghi nhớ đăng nhập</Checkbox>
              </Form.Item>
              <span>Liên hệ quản trị viên nếu quên mật khẩu</span>
            </div>

            <Button
              className="login-submit"
              type="primary"
              htmlType="submit"
              loading={submitting}
              block
            >
              Đăng nhập
              {!submitting && <ArrowRightOutlined />}
            </Button>
          </Form>

          <div className="login-help">
            <SafetyCertificateOutlined />
            Phiên đăng nhập được bảo vệ bằng JWT và tự động xác minh lại khi
            tải trang.
          </div>
        </div>
      </section>
    </main>
  )
}
