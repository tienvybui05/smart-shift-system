import httpClient from '../api/httpClient.js'

export async function getMyAvailabilities(params) {
  const response = await httpClient.get('/employee-availabilities/me', { params })
  return response.data
}

export async function getUserAvailabilities(userId, params) {
  const response = await httpClient.get(
    `/employee-availabilities/users/${userId}`,
    { params },
  )
  return response.data
}

export async function createMyAvailability(payload) {
  const response = await httpClient.post('/employee-availabilities/me', payload)
  return response.data
}

export async function updateMyAvailability(id, payload) {
  const response = await httpClient.put(`/employee-availabilities/me/${id}`, payload)
  return response.data
}

export async function deleteMyAvailability(id) {
  await httpClient.delete(`/employee-availabilities/me/${id}`)
}
