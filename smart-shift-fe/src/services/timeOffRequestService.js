import httpClient from '../api/httpClient.js'

export async function getMyTimeOffRequests() {
  const response = await httpClient.get('/time-off-requests/me')
  return response.data
}

export async function createMyTimeOffRequest(payload) {
  const response = await httpClient.post('/time-off-requests/me', payload)
  return response.data
}

export async function cancelMyTimeOffRequest(id) {
  const response = await httpClient.patch(`/time-off-requests/me/${id}/cancel`)
  return response.data
}

export async function getTimeOffRequests(params) {
  const response = await httpClient.get('/time-off-requests', { params })
  return response.data
}

export async function reviewTimeOffRequest(id, status) {
  const response = await httpClient.patch(`/time-off-requests/${id}/review`, { status })
  return response.data
}
