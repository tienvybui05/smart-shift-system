import {
  CalendarOutlined,
  ClockCircleOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Tag } from 'antd'
import useAuth from '../hooks/useAuth.js'

const ROLE_LABELS = {
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_MANAGER: 'Quản lý',
  ROLE_EMPLOYEE: 'Nhân viên',
}

export default function DashboardPage() {
  const { user } = useAuth()

  return (
    <>
      <section className="welcome-panel">
        <div>
          <Tag color="purple">{ROLE_LABELS[user.role] || user.role}</Tag>
          <h1>Xin chào, {user.fullName}!</h1>
          <p>
            Theo dõi nhân sự, ca làm và hoạt động vận hành của bạn trên một
            hệ thống thống nhất.
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
          <small>Tài khoản và hồ sơ nhân sự</small>
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
    </>
  )
}
