import {
  CheckOutlined,
  NotificationOutlined,
  RightOutlined,
} from '@ant-design/icons'
import {
  Badge,
  Button,
  Empty,
  List,
  notification,
  Popover,
  Spin,
  Tooltip,
} from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../../hooks/useAuth.js'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  NOTIFICATIONS_CHANGED_EVENT,
  notifyNotificationsChanged,
  subscribeToNotifications,
} from '../../services/notificationService.js'
import {
  formatNotificationTime,
  getNotificationTarget,
  getNotificationTone,
} from './notificationDisplay.js'

const PREVIEW_SIZE = 8

export default function NotificationCenter() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [notificationApi, contextHolder] = notification.useNotification()
  const [items, setItems] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)

  const loadPreview = useCallback(async () => {
    try {
      const [page, count] = await Promise.all([
        getNotifications({ size: PREVIEW_SIZE }),
        getUnreadNotificationCount(),
      ])
      setItems(page.content)
      setUnreadCount(count)
    } catch {
      // The next reconnect or popover open will retry silently.
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialLoadId = window.setTimeout(loadPreview, 0)
    const handleChanged = () => loadPreview()
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handleChanged)
    return () => {
      window.clearTimeout(initialLoadId)
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handleChanged)
    }
  }, [loadPreview])

  const handleRealtimeNotification = useCallback(
    (item) => {
      setItems((current) => [
        item,
        ...current.filter((existing) => existing.id !== item.id),
      ].slice(0, PREVIEW_SIZE))
      if (!item.readAt) {
        setUnreadCount((current) => current + 1)
      }
      notificationApi.open({
        message: item.title,
        description: item.content,
        placement: 'topRight',
        duration: 5,
        onClick: () => {
          const target = getNotificationTarget(item, user.role)
          if (target) navigate(target)
        },
      })
      notifyNotificationsChanged({ source: 'sse', notification: item })
    },
    [navigate, notificationApi, user.role],
  )

  useEffect(() => {
    const controller = new AbortController()
    subscribeToNotifications({
      signal: controller.signal,
      onConnected: loadPreview,
      onNotification: handleRealtimeNotification,
    })
    return () => controller.abort()
  }, [handleRealtimeNotification, loadPreview])

  async function handleSelect(item) {
    if (!item.readAt) {
      await markNotificationAsRead(item.id)
      notifyNotificationsChanged({ source: 'read', id: item.id })
    }
    setOpen(false)
    const target = getNotificationTarget(item, user.role)
    if (target) navigate(target)
  }

  async function handleMarkAllRead() {
    await markAllNotificationsAsRead()
    notifyNotificationsChanged({ source: 'read-all' })
  }

  const content = (
    <div className="notification-popover">
      <div className="notification-popover__header">
        <div>
          <strong>Thông báo</strong>
          <span>{unreadCount} thông báo chưa đọc</span>
        </div>
        <Tooltip title="Đánh dấu tất cả đã đọc">
          <Button
            aria-label="Đánh dấu tất cả thông báo đã đọc"
            disabled={unreadCount === 0}
            icon={<CheckOutlined />}
            onClick={handleMarkAllRead}
            size="small"
            type="text"
          />
        </Tooltip>
      </div>

      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <Empty
            className="notification-empty"
            description="Chưa có thông báo"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <List
            className="notification-preview-list"
            dataSource={items}
            renderItem={(item) => (
              <List.Item
                className={`notification-preview-item${item.readAt ? '' : ' notification-preview-item--unread'}`}
                onClick={() => handleSelect(item)}
              >
                <span
                  className={`notification-tone notification-tone--${getNotificationTone(item.type)}`}
                />
                <div className="notification-preview-item__content">
                  <strong>{item.title}</strong>
                  <span>{item.content}</span>
                  <small>{formatNotificationTime(item.createdAt)}</small>
                </div>
                <RightOutlined />
              </List.Item>
            )}
          />
        )}
      </Spin>

      <Button
        className="notification-view-all"
        onClick={() => {
          setOpen(false)
          navigate('/notifications')
        }}
        type="link"
      >
        Xem tất cả thông báo
      </Button>
    </div>
  )

  return (
    <>
      {contextHolder}
      <Popover
        content={content}
        onOpenChange={setOpen}
        open={open}
        placement="bottomRight"
        trigger="click"
      >
        <Badge count={unreadCount} offset={[-4, 5]} overflowCount={99} size="small">
          <Button
            className="notification-button"
            type="text"
            icon={<NotificationOutlined />}
            aria-label={`Thông báo, ${unreadCount} chưa đọc`}
          />
        </Badge>
      </Popover>
    </>
  )
}
