export function getNotificationTarget(item, role) {
  if (item.type === 'TIME_OFF_REQUEST_CREATED') {
    return '/time-off/review'
  }
  if (item.type === 'OPEN_SHIFT_CLAIM_CREATED') {
    return '/open-shifts/review'
  }

  switch (item.referenceType) {
    case 'SCHEDULE_PERIOD':
      return '/my-schedule'
    case 'TIME_OFF_REQUEST':
      return '/time-off'
    case 'OPEN_SHIFT_CLAIM':
      return role === 'ROLE_EMPLOYEE'
        ? '/open-shifts'
        : '/open-shifts/review'
    case 'ATTENDANCE':
      return role === 'ROLE_EMPLOYEE'
        ? '/attendance'
        : '/attendance/manage'
    default:
      return null
  }
}

export function formatNotificationTime(value) {
  if (!value) return ''

  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function getNotificationTone(type) {
  if (type.endsWith('_APPROVED') || type === 'SCHEDULE_PUBLISHED') {
    return 'success'
  }
  if (type.endsWith('_REJECTED')) {
    return 'danger'
  }
  return 'info'
}
