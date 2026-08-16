import httpClient from '../api/httpClient.js'

export async function getMyWorkSchedule(params) {
  const response = await httpClient.get('/shift-assignments/me', { params })
  return response.data
}
