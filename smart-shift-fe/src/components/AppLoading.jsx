import { Spin } from 'antd'

export default function AppLoading() {
  return (
    <div className="app-loading" role="status" aria-label="Đang tải">
      <div className="brand-mark brand-mark--loading">S</div>
      <Spin size="large" />
      <p>Đang kiểm tra phiên đăng nhập...</p>
    </div>
  )
}
