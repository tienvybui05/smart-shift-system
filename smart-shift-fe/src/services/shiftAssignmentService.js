import httpClient from '../api/httpClient.js'

export async function getShiftAssignmentSummary(workShiftId) {
  const response = await httpClient.get(
    `/shift-assignments/work-shifts/${workShiftId}`,
  )
  return response.data
}

export async function getAssignmentCandidates(workShiftId, positionId) {
  const response = await httpClient.get(
    `/shift-assignments/work-shifts/${workShiftId}/candidates`,
    { params: { positionId } },
  )
  return response.data
}

export async function assignEmployee(workShiftId, userId, note = null) {
  const response = await httpClient.post(
    `/shift-assignments/work-shifts/${workShiftId}`,
    { userId, note },
  )
  return response.data
}

export async function removeShiftAssignment(assignmentId) {
  const response = await httpClient.delete(
    `/shift-assignments/${assignmentId}`,
  )
  return response.data
}
