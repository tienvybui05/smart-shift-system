import httpClient from '../api/httpClient.js'

export async function getMyShiftSwapRequests() {
  const response = await httpClient.get('/shift-swap-requests/me')
  return response.data
}

export async function getAvailableShiftGiveaways() {
  const response = await httpClient.get('/shift-swap-requests/available')
  return response.data
}

export async function getShiftSwapCandidates(requesterAssignmentId) {
  const response = await httpClient.get('/shift-swap-requests/candidates', {
    params: { requesterAssignmentId },
  })
  return response.data
}

export async function createShiftSwapRequest(payload) {
  const response = await httpClient.post('/shift-swap-requests', payload)
  return response.data
}

export async function respondToShiftSwapRequest(id, status, responseNote = null) {
  const response = await httpClient.patch(`/shift-swap-requests/me/${id}/respond`, {
    status,
    responseNote,
  })
  return response.data
}

export async function cancelShiftSwapRequest(id) {
  const response = await httpClient.patch(`/shift-swap-requests/me/${id}/cancel`)
  return response.data
}

export async function getShiftSwapRequests(params) {
  const response = await httpClient.get('/shift-swap-requests', { params })
  return response.data
}

export async function reviewShiftSwapRequest(id, status, reviewerNote = null) {
  const response = await httpClient.patch(`/shift-swap-requests/${id}/review`, {
    status,
    reviewerNote,
  })
  return response.data
}
