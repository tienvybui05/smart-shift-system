import httpClient from '../api/httpClient.js'

export async function getMyAttendances(params) {
  const response = await httpClient.get('/attendances/me', { params })
  return response.data
}

export async function checkIn(payload) {
  const response = await httpClient.post('/attendances/check-in', payload)
  return response.data
}

export async function checkOut(payload) {
  const response = await httpClient.post('/attendances/check-out', payload)
  return response.data
}
