import httpClient from '../api/httpClient.js'

export async function getMyDevices() {
  const response = await httpClient.get('/employee-devices/me')
  return response.data
}

export async function getDevices(params) {
  const response = await httpClient.get('/employee-devices', { params })
  return response.data
}

export async function reviewDevice(id, payload) {
  const response = await httpClient.put(`/employee-devices/${id}/review`, payload)
  return response.data
}
