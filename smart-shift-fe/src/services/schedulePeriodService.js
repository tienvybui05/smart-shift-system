import httpClient from '../api/httpClient.js'

export async function getSchedulePeriods(params) {
  const response = await httpClient.get('/schedule-periods', { params })
  return response.data
}

export async function createSchedulePeriod(payload) {
  const response = await httpClient.post('/schedule-periods', payload)
  return response.data
}

export async function updateSchedulePeriod(id, payload) {
  const response = await httpClient.put(`/schedule-periods/${id}`, payload)
  return response.data
}

export async function getSchedulePublicationCheck(id) {
  const response = await httpClient.get(
    `/schedule-periods/${id}/publication-check`,
  )
  return response.data
}

export async function publishSchedulePeriod(id) {
  const response = await httpClient.post(`/schedule-periods/${id}/publish`)
  return response.data
}

export async function lockSchedulePeriod(id) {
  const response = await httpClient.post(`/schedule-periods/${id}/lock`)
  return response.data
}
