import httpClient from '../api/httpClient.js'
export async function listCorrections(params, mine) {
  return (await httpClient.get(mine ? '/attendance-corrections/me' : '/attendance-corrections', { params })).data
}
export async function createCorrection(payload) {
  return (await httpClient.post('/attendance-corrections/me', payload)).data
}
export async function cancelCorrection(id) {
  return (await httpClient.patch(`/attendance-corrections/me/${id}/cancel`)).data
}
export async function reviewCorrection(id, payload) {
  return (await httpClient.patch(`/attendance-corrections/${id}/review`, payload)).data
}
export async function attendanceHistory(assignmentId, mine) {
  return (await httpClient.get(mine ? '/attendance-corrections/me/history' : '/attendance-corrections/history',
    { params: { shiftAssignmentId: assignmentId } })).data
}

