export const OPEN_SHIFT_CLAIM_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', color: 'gold' },
  APPROVED: { label: 'Đã duyệt', color: 'green' },
  REJECTED: { label: 'Từ chối', color: 'red' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
}

export const AVAILABILITY_CONFIG = {
  PREFERRED: { label: 'Ưu tiên', color: 'purple' },
  AVAILABLE: { label: 'Sẵn sàng', color: 'blue' },
}

export function formatOpenShiftDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

export function formatOpenShiftTime(value) {
  return value?.slice(0, 5) || '--:--'
}

export function formatOpenShiftDateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatOpenShiftHours(minutes) {
  const numericMinutes = Number(minutes || 0)
  const hours = Math.floor(numericMinutes / 60)
  const remainingMinutes = numericMinutes % 60
  return remainingMinutes ? `${hours} giờ ${remainingMinutes} phút` : `${hours} giờ`
}
