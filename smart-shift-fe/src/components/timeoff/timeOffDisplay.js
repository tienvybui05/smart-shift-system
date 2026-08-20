export const LEAVE_TYPE_CONFIG = {
  ANNUAL: { label: 'Nghỉ phép năm', color: 'blue' },
  SICK: { label: 'Nghỉ ốm', color: 'orange' },
  UNPAID: { label: 'Nghỉ không lương', color: 'default' },
  OTHER: { label: 'Lý do khác', color: 'purple' },
}

export const TIME_OFF_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', color: 'processing' },
  APPROVED: { label: 'Đã duyệt', color: 'success' },
  REJECTED: { label: 'Từ chối', color: 'error' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
}

const DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const TIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(value) {
  if (!value) return '—'
  return DATE_TIME_FORMATTER.format(new Date(value))
}

export function formatTimeOffPeriod(startValue, endValue) {
  if (!startValue || !endValue) return '—'

  const startAt = new Date(startValue)
  const endAt = new Date(endValue)
  const sameDate = startAt.getFullYear() === endAt.getFullYear()
    && startAt.getMonth() === endAt.getMonth()
    && startAt.getDate() === endAt.getDate()

  if (sameDate) {
    return `${DATE_FORMATTER.format(startAt)}, ${TIME_FORMATTER.format(startAt)} – ${TIME_FORMATTER.format(endAt)}`
  }

  return `${DATE_TIME_FORMATTER.format(startAt)} – ${DATE_TIME_FORMATTER.format(endAt)}`
}
