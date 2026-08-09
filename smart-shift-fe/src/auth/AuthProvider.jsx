import { useCallback, useEffect, useMemo, useState } from 'react'
import { AUTH_UNAUTHORIZED_EVENT } from '../api/httpClient.js'
import { getCurrentUser, login as loginRequest } from '../services/authService.js'
import {
  clearAuthSession,
  getAccessToken,
  getStoredUser,
  saveAuthSession,
  saveCurrentUser,
} from './authStorage.js'
import AuthContext from './AuthContext.js'

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser)
  const [initializing, setInitializing] = useState(true)

  const logout = useCallback(() => {
    clearAuthSession()
    setUser(null)
  }, [])

  const login = useCallback(async (credentials, rememberMe = false) => {
    const result = await loginRequest(credentials)
    saveAuthSession(result.accessToken, result.user, rememberMe)
    setUser(result.user)
    return result.user
  }, [])

  useEffect(() => {
    let active = true

    async function restoreSession() {
      if (!getAccessToken()) {
        if (active) setInitializing(false)
        return
      }

      try {
        const currentUser = await getCurrentUser()
        if (active) {
          saveCurrentUser(currentUser)
          setUser(currentUser)
        }
      } catch {
        if (active) logout()
      } finally {
        if (active) setInitializing(false)
      }
    }

    restoreSession()
    return () => {
      active = false
    }
  }, [logout])

  useEffect(() => {
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, logout)
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, logout)
  }, [logout])

  const value = useMemo(
    () => ({
      user,
      initializing,
      isAuthenticated: Boolean(user && getAccessToken()),
      login,
      logout,
    }),
    [initializing, login, logout, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
