import {
  CalendarOutlined,
  ClockCircleOutlined,
  DownOutlined,
  LogoutOutlined,
  MenuOutlined,
  NotificationOutlined,
  SettingOutlined,
  SwapOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Dropdown, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

const ROLE_LABELS = {
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_MANAGER: 'Quản lý',
  ROLE_EMPLOYEE: 'Nhân viên',
}

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const accountMenu = {
    items: [
      {
        key: 'profile',
        icon: <UserOutlined />,
        label: 'Thông tin tài khoản',
        disabled: true,
      },
      { type: 'divider' },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: 'Đăng xuất',
        danger: true,
        onClick: handleLogout,
      },
    ],
  }

  return (
    <div className="dashboard-shell">
      <aside className="dashboard-sidebar">
        <div className="sidebar-brand login-brand">
          <div className="brand-mark">S</div>
          <div>
            <strong>Smart Shift</strong>
            <span>Workforce Management</span>
          </div>
        </div>

        <nav className="sidebar-nav" aria-label="Điều hướng chính">
          <span className="nav-section-label">Tổng quan</span>
          <button className="nav-item nav-item--active" type="button">
            <MenuOutlined /> Tổng quan
          </button>
          <span className="nav-section-label">Quản lý</span>
          <button className="nav-item" type="button" disabled>
            <TeamOutlined /> Nhân viên
          </button>
          <button className="nav-item" type="button" disabled>
            <CalendarOutlined /> Lịch làm việc
          </button>
          <button className="nav-item" type="button" disabled>
            <SwapOutlined /> Đổi ca
          </button>
          <button className="nav-item" type="button" disabled>
            <ClockCircleOutlined /> Chấm công
          </button>
          <span className="nav-section-label">Hệ thống</span>
          <button className="nav-item" type="button" disabled>
            <SettingOutlined /> Thiết lập
          </button>
        </nav>

        <div className="sidebar-help">
          <strong>Smart scheduling</strong>
          <span>Các phân hệ nghiệp vụ sẽ được bổ sung tiếp theo.</span>
        </div>
      </aside>

      <div className="dashboard-main">
        <header className="dashboard-header">
          <div>
            <span className="header-date">
              {new Intl.DateTimeFormat('vi-VN', {
                weekday: 'long',
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
              }).format(new Date())}
            </span>
          </div>

          <div className="header-actions">
            <Button
              className="notification-button"
              type="text"
              icon={<NotificationOutlined />}
              aria-label="Thông báo"
            />
            <Dropdown menu={accountMenu} trigger={['click']} placement="bottomRight">
              <button className="account-button" type="button">
                <Avatar size={38} icon={<UserOutlined />} />
                <span>
                  <strong>{user.fullName}</strong>
                  <small>{ROLE_LABELS[user.role] || user.role}</small>
                </span>
                <DownOutlined />
              </button>
            </Dropdown>
          </div>
        </header>

        <main className="dashboard-content">
          <section className="welcome-panel">
            <div>
              <Tag color="purple">{ROLE_LABELS[user.role] || user.role}</Tag>
              <h1>Xin chào, {user.fullName}!</h1>
              <p>
                Phiên đăng nhập đã hoạt động. Đây là trang tổng quan ban đầu
                trước khi xây dựng các phân hệ quản lý.
              </p>
            </div>
            <div className="welcome-meta">
              <span>Chi nhánh</span>
              <strong>{user.locationName}</strong>
              <span>Vị trí</span>
              <strong>{user.positionName}</strong>
            </div>
          </section>

          <section className="summary-grid" aria-label="Tóm tắt hệ thống">
            <article className="summary-card">
              <span className="summary-icon summary-icon--purple"><TeamOutlined /></span>
              <div><span>Nhân viên</span><strong>Quản lý tập trung</strong></div>
              <small>User API đã sẵn sàng</small>
            </article>
            <article className="summary-card">
              <span className="summary-icon summary-icon--blue"><CalendarOutlined /></span>
              <div><span>Lịch làm việc</span><strong>Sắp triển khai</strong></div>
              <small>Ca sáng · chiều · tối</small>
            </article>
            <article className="summary-card">
              <span className="summary-icon summary-icon--green"><ClockCircleOutlined /></span>
              <div><span>Chấm công</span><strong>Sắp triển khai</strong></div>
              <small>Theo dõi giờ vào và ra</small>
            </article>
          </section>

          <section className="account-overview">
            <div className="section-heading">
              <div>
                <span className="eyebrow">Tài khoản hiện tại</span>
                <h2>Thông tin đăng nhập</h2>
              </div>
              <Tag color="success">Đang hoạt động</Tag>
            </div>
            <dl className="account-details">
              <div><dt>Mã nhân viên</dt><dd>{user.employeeCode}</dd></div>
              <div><dt>Tên đăng nhập</dt><dd>{user.username}</dd></div>
              <div><dt>Vai trò</dt><dd>{ROLE_LABELS[user.role] || user.role}</dd></div>
              <div><dt>Chi nhánh</dt><dd>{user.locationName}</dd></div>
              <div><dt>Vị trí</dt><dd>{user.positionName}</dd></div>
            </dl>
          </section>
        </main>
      </div>
    </div>
  )
}
