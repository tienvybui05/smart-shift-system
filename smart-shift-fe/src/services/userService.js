import httpClient from '../api/httpClient.js'

export async function getUsers(params) {
  const response = await httpClient.get('/users', { params })
  return response.data
}

export async function createUser(payload) {
  const response = await httpClient.post('/users', payload)
  return response.data
}

export async function updateUser(id, payload) {
  const response = await httpClient.put(`/users/${id}`, payload)
  return response.data
}

export async function updateUserStatus(id, active) {
  const response = await httpClient.patch(`/users/${id}/status`, { active })
  return response.data
}

export async function resetUserPassword(id, payload) {
  await httpClient.post(`/users/${id}/reset-password`, payload)
}
