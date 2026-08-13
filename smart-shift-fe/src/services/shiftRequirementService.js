import httpClient from '../api/httpClient.js'

export async function getShiftRequirements(workShiftId) {
  const response = await httpClient.get('/shift-requirements', {
    params: { workShiftId },
  })
  return response.data
}

export async function saveShiftRequirements(workShiftId, payload) {
  const response = await httpClient.put(
    `/shift-requirements/work-shifts/${workShiftId}`,
    payload,
  )
  return response.data
}
