import httpClient from '../api/httpClient.js'

export async function login(credentials) {
  const response = await httpClient.post('/auth/login', credentials)
  return response.data
}

export async function getCurrentUser() {
  const response = await httpClient.get('/auth/me')
  return response.data
}
