export const SHIFT_SWAP_STATUS_CONFIG = {
  PENDING: { label: 'Chờ người nhận', color: 'gold' },
  ACCEPTED: { label: 'Chờ quản lý', color: 'blue' },
  DECLINED: { label: 'Người nhận từ chối', color: 'red' },
  APPROVED: { label: 'Đã duyệt', color: 'green' },
  REJECTED: { label: 'Quản lý từ chối', color: 'volcano' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
}

export const SHIFT_SWAP_TYPE_CONFIG = {
  GIVEAWAY: { label: 'Nhường ca', color: 'orange' },
  SWAP: { label: 'Đổi ca', color: 'purple' },
}

export function formatShiftSwapDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

export function formatShiftSwapTime(value) {
  return value?.slice(0, 5) || '--:--'
}

export function formatShiftSwapDateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatShiftSwapAssignment(assignment) {
  if (!assignment) return 'Không có ca đối ứng'
  return `${assignment.shiftName} · ${formatShiftSwapDate(assignment.workDate)} · ${formatShiftSwapTime(assignment.startTime)}–${formatShiftSwapTime(assignment.endTime)}`
}
