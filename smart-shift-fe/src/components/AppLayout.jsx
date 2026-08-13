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
import { Avatar, Button, Drawer, Dropdown } from 'antd'
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'

const ROLE_LABELS = {
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_MANAGER: 'Quản lý',
  ROLE_EMPLOYEE: 'Nhân viên',
}

function SidebarContent({ role, onNavigate }) {
  return (
    <>
      <div className="sidebar-brand login-brand">
        <div className="brand-mark">S</div>
        <div>
          <strong>Smart Shift</strong>
          <span>Workforce Management</span>
        </div>
      </div>

      <nav className="sidebar-nav" aria-label="Điều hướng chính">
        <span className="nav-section-label">Tổng quan</span>
        <NavLink
          className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
          end
          onClick={onNavigate}
          to="/"
        >
          <MenuOutlined /> Tổng quan
        </NavLink>

        <span className="nav-section-label">Quản lý</span>
        {role === 'ROLE_ADMIN' && (
          <NavLink
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
            onClick={onNavigate}
            to="/admin/employees"
          >
            <TeamOutlined /> Nhân viên
          </NavLink>
        )}
        {role === 'ROLE_ADMIN' && (
          <NavLink
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
            onClick={onNavigate}
            to="/admin/shift-templates"
          >
            <ClockCircleOutlined /> Mẫu ca
          </NavLink>
        )}
        {role === 'ROLE_ADMIN' && (
          <NavLink
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
            onClick={onNavigate}
            to="/admin/schedule-periods"
          >
            <CalendarOutlined /> Kỳ xếp lịch
          </NavLink>
        )}
        {role === 'ROLE_ADMIN' && (
          <NavLink
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
            onClick={onNavigate}
            to="/admin/work-shifts"
          >
            <CalendarOutlined /> Lịch làm việc
          </NavLink>
        )}
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
        <span>Quản lý nhân sự và lịch làm việc tập trung.</span>
      </div>
    </>
  )
}

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

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
        <SidebarContent role={user.role} />
      </aside>

      <Drawer
        className="mobile-navigation-drawer"
        closable={false}
        onClose={() => setMobileMenuOpen(false)}
        open={mobileMenuOpen}
        placement="left"
        width={270}
        styles={{ body: { display: 'flex', flexDirection: 'column', padding: '27px 20px 22px' } }}
      >
        <SidebarContent role={user.role} onNavigate={() => setMobileMenuOpen(false)} />
      </Drawer>

      <div className="dashboard-main">
        <header className="dashboard-header">
          <div className="header-leading">
            <Button
              className="mobile-menu-button"
              type="text"
              icon={<MenuOutlined />}
              aria-label="Mở menu"
              onClick={() => setMobileMenuOpen(true)}
            />
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
          <Outlet />
        </main>
      </div>
    </div>
  )
}
