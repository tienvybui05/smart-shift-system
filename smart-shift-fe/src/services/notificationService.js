import { AUTH_UNAUTHORIZED_EVENT } from '../api/httpClient.js'
import httpClient from '../api/httpClient.js'
import { clearAuthSession, getAccessToken } from '../auth/authStorage.js'

export const NOTIFICATIONS_CHANGED_EVENT = 'smart-shift:notifications-changed'

export async function getNotifications({
  page = 0,
  size = 20,
  unreadOnly = false,
} = {}) {
  const response = await httpClient.get('/notifications', {
    params: { page, size, unreadOnly },
  })
  return response.data
}

export async function getUnreadNotificationCount() {
  const response = await httpClient.get('/notifications/unread-count')
  return response.data.unreadCount
}

export async function markNotificationAsRead(id) {
  const response = await httpClient.patch(`/notifications/${id}/read`)
  return response.data
}

export async function markAllNotificationsAsRead() {
  const response = await httpClient.patch('/notifications/read-all')
  return response.data
}

export function notifyNotificationsChanged(detail) {
  window.dispatchEvent(
    new CustomEvent(NOTIFICATIONS_CHANGED_EVENT, { detail }),
  )
}

export async function subscribeToNotifications({
  signal,
  onConnected,
  onNotification,
}) {
  let retryDelay = 1_000

  while (!signal.aborted) {
    try {
      const accessToken = getAccessToken()
      if (!accessToken) return

      const response = await fetch('/api/notifications/stream', {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${accessToken}`,
        },
        cache: 'no-store',
        signal,
      })

      if (response.status === 401) {
        clearAuthSession()
        window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT))
        return
      }
      if (!response.ok || !response.body) {
        throw new Error(`SSE connection failed with status ${response.status}`)
      }

      retryDelay = 1_000
      onConnected?.()
      await consumeEventStream(response.body, signal, onNotification)
    } catch (error) {
      if (signal.aborted || error?.name === 'AbortError') return
    }

    await waitForRetry(retryDelay, signal)
    retryDelay = Math.min(retryDelay * 2, 10_000)
  }
}

async function consumeEventStream(stream, signal, onNotification) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replaceAll('\r\n', '\n')

      let boundaryIndex = buffer.indexOf('\n\n')
      while (boundaryIndex >= 0) {
        const block = buffer.slice(0, boundaryIndex)
        buffer = buffer.slice(boundaryIndex + 2)
        handleEventBlock(block, onNotification)
        boundaryIndex = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function handleEventBlock(block, onNotification) {
  if (!block || block.startsWith(':')) return

  let eventName = 'message'
  const dataLines = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (eventName !== 'notification' || dataLines.length === 0) return
  try {
    onNotification?.(JSON.parse(dataLines.join('\n')))
  } catch {
    // Ignore a malformed event and keep the live stream connected.
  }
}

function waitForRetry(delay, signal) {
  if (signal.aborted) return Promise.resolve()

  return new Promise((resolve) => {
    const timeoutId = window.setTimeout(resolve, delay)
    signal.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timeoutId)
        resolve()
      },
      { once: true },
    )
  })
}
