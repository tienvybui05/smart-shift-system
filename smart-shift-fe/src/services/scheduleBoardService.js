import httpClient from '../api/httpClient.js'
export async function getScheduleBoard(periodId) {
  return (await httpClient.get(`/schedule-periods/${periodId}/board`)).data
}
export async function previewCalendarPlacement(periodId, payload) {
  return (await httpClient.post(`/schedule-periods/${periodId}/board/preview`, payload)).data
}
export async function placeCalendarEmployee(periodId, payload) {
  return (await httpClient.post(`/schedule-periods/${periodId}/board/place`, payload)).data
}
