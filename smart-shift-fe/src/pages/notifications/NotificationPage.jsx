import {
  CheckOutlined,
  NotificationOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { Button, Empty, List, message, Pagination, Segmented, Spin } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../../hooks/useAuth.js'
import {
  getNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  NOTIFICATIONS_CHANGED_EVENT,
  notifyNotificationsChanged,
} from '../../services/notificationService.js'
import {
  formatNotificationTime,
  getNotificationTarget,
  getNotificationTone,
} from '../../components/notifications/notificationDisplay.js'
import { getApiErrorMessage } from '../../api/apiError.js'

const PAGE_SIZE = 12

export default function NotificationPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [messageApi, messageContext] = message.useMessage()
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(true)

  const loadNotifications = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getNotifications({
        page,
        size: PAGE_SIZE,
        unreadOnly,
      })
      setItems(result.content)
      setTotal(result.totalElements)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể tải thông báo'))
    } finally {
      setLoading(false)
    }
  }, [messageApi, page, unreadOnly])

  useEffect(() => {
    const initialLoadId = window.setTimeout(loadNotifications, 0)
    const handleChanged = () => loadNotifications()
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handleChanged)
    return () => {
      window.clearTimeout(initialLoadId)
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handleChanged)
    }
  }, [loadNotifications])

  async function handleSelect(item) {
    try {
      if (!item.readAt) {
        await markNotificationAsRead(item.id)
        notifyNotificationsChanged({ source: 'read', id: item.id })
      }
      const target = getNotificationTarget(item, user.role)
      if (target) navigate(target)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể mở thông báo'))
    }
  }

  async function handleMarkAllRead() {
    try {
      await markAllNotificationsAsRead()
      notifyNotificationsChanged({ source: 'read-all' })
      messageApi.success('Đã đánh dấu tất cả thông báo là đã đọc')
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể cập nhật thông báo'))
    }
  }

  return (
    <div className="page-stack notification-page">
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">CẬP NHẬT CÔNG VIỆC</span>
          <h1>Thông báo</h1>
          <p>Theo dõi lịch làm việc và kết quả các yêu cầu của bạn.</p>
        </div>
        <Button icon={<CheckOutlined />} onClick={handleMarkAllRead}>
          Đánh dấu tất cả đã đọc
        </Button>
      </section>

      <section className="table-card notification-history-card">
        <div className="table-heading notification-history-heading">
          <div>
            <strong>Danh sách thông báo</strong>
            <span>{total} thông báo phù hợp bộ lọc</span>
          </div>
          <Segmented
            onChange={(value) => {
              setPage(0)
              setUnreadOnly(value === 'unread')
            }}
            options={[
              { label: 'Tất cả', value: 'all' },
              { label: 'Chưa đọc', value: 'unread' },
            ]}
            value={unreadOnly ? 'unread' : 'all'}
          />
        </div>

        <Spin spinning={loading}>
          {items.length === 0 && !loading ? (
            <Empty description="Không có thông báo phù hợp" />
          ) : (
            <List
              className="notification-history-list"
              dataSource={items}
              renderItem={(item) => (
                <List.Item
                  className={`notification-history-item${item.readAt ? '' : ' notification-history-item--unread'}`}
                  onClick={() => handleSelect(item)}
                >
                  <div
                    className={`notification-history-icon notification-history-icon--${getNotificationTone(item.type)}`}
                  >
                    <NotificationOutlined />
                  </div>
                  <div className="notification-history-content">
                    <div>
                      <strong>{item.title}</strong>
                      {!item.readAt && <span>Chưa đọc</span>}
                    </div>
                    <p>{item.content}</p>
                    <small>{formatNotificationTime(item.createdAt)}</small>
                  </div>
                  <RightOutlined />
                </List.Item>
              )}
            />
          )}
        </Spin>

        {total > PAGE_SIZE && (
          <div className="notification-pagination">
            <Pagination
              current={page + 1}
              onChange={(nextPage) => setPage(nextPage - 1)}
              pageSize={PAGE_SIZE}
              showSizeChanger={false}
              total={total}
            />
          </div>
        )}
      </section>
    </div>
  )
}
