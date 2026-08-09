const ACCESS_TOKEN_KEY = 'smart_shift_access_token'
const CURRENT_USER_KEY = 'smart_shift_current_user'

function getActiveStorage() {
  if (localStorage.getItem(ACCESS_TOKEN_KEY)) return localStorage
  if (sessionStorage.getItem(ACCESS_TOKEN_KEY)) return sessionStorage
  return null
}

export function getAccessToken() {
  return getActiveStorage()?.getItem(ACCESS_TOKEN_KEY) || null
}

export function getStoredUser() {
  const storage = getActiveStorage()
  const value = storage?.getItem(CURRENT_USER_KEY)
  if (!value) return null

  try {
    return JSON.parse(value)
  } catch {
    storage.removeItem(CURRENT_USER_KEY)
    return null
  }
}

export function saveAuthSession(accessToken, user, rememberMe) {
  clearAuthSession()
  const storage = rememberMe ? localStorage : sessionStorage
  storage.setItem(ACCESS_TOKEN_KEY, accessToken)
  storage.setItem(CURRENT_USER_KEY, JSON.stringify(user))
}

export function saveCurrentUser(user) {
  getActiveStorage()?.setItem(CURRENT_USER_KEY, JSON.stringify(user))
}

export function clearAuthSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(CURRENT_USER_KEY)
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(CURRENT_USER_KEY)
}
