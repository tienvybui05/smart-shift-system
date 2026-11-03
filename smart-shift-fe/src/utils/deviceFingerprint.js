/**
 * Utility for generating and maintaining a stable device fingerprint and human-readable label.
 * Used for anti-fraud employee device verification during check-in / check-out.
 */

function generateSimpleUUID() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function getDeviceFingerprint() {
  const STORAGE_KEY = 'smartshift_device_fingerprint'
  let fingerprint = localStorage.getItem(STORAGE_KEY)
  if (!fingerprint) {
    const randomPart = generateSimpleUUID().replace(/-/g, '').slice(0, 16)
    const platform = (navigator.userAgentData?.platform || navigator.platform || 'web').toLowerCase().replace(/[^a-z0-9]/g, '')
    fingerprint = `fp_${platform}_${randomPart}`
    try {
      localStorage.setItem(STORAGE_KEY, fingerprint)
    } catch {
      // Storage unavailable or quota exceeded; return in-memory fingerprint
    }
  }
  return fingerprint
}

export function getDeviceLabel() {
  const userAgent = navigator.userAgent || ''

  let browser = 'Trình duyệt web'
  if (/edg/i.test(userAgent)) {
    browser = 'Microsoft Edge'
  } else if (/chrome|crios/i.test(userAgent) && !/opr|opera/i.test(userAgent)) {
    browser = 'Google Chrome'
  } else if (/firefox|fxios/i.test(userAgent)) {
    browser = 'Mozilla Firefox'
  } else if (/safari/i.test(userAgent) && !/chrome/i.test(userAgent)) {
    browser = 'Apple Safari'
  } else if (/opr|opera/i.test(userAgent)) {
    browser = 'Opera'
  }

  let os = 'Thiết bị'
  if (/iphone/i.test(userAgent)) {
    os = 'iPhone'
  } else if (/ipad/i.test(userAgent)) {
    os = 'iPad'
  } else if (/android/i.test(userAgent)) {
    os = 'Android'
  } else if (/windows/i.test(userAgent)) {
    os = 'Windows'
  } else if (/macintosh|mac os x/i.test(userAgent)) {
    os = 'macOS'
  } else if (/linux/i.test(userAgent)) {
    os = 'Linux'
  }

  return `${browser} trên ${os}`
}
