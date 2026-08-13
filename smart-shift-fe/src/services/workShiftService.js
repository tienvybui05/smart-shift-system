import httpClient from '../api/httpClient.js'

export async function getWorkShifts(params) {
  const response = await httpClient.get('/work-shifts', { params })
  return response.data
}

export async function createWorkShift(payload) {
  const response = await httpClient.post('/work-shifts', payload)
  return response.data
}

export async function generateWorkShifts(payload) {
  const response = await httpClient.post('/work-shifts/generate', payload)
  return response.data
}

export async function updateWorkShift(id, payload) {
  const response = await httpClient.put(`/work-shifts/${id}`, payload)
  return response.data
}

export async function updateWorkShiftStatus(id, status) {
  const response = await httpClient.patch(`/work-shifts/${id}/status`, { status })
  return response.data
}
