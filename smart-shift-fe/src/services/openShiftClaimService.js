import httpClient from '../api/httpClient.js'

export async function getAvailableOpenShifts() {
  const response = await httpClient.get('/open-shift-claims/available')
  return response.data
}

export async function getMyOpenShiftClaims() {
  const response = await httpClient.get('/open-shift-claims/me')
  return response.data
}

export async function createOpenShiftClaim(payload) {
  const response = await httpClient.post('/open-shift-claims', payload)
  return response.data
}

export async function cancelOpenShiftClaim(id) {
  const response = await httpClient.patch(`/open-shift-claims/me/${id}/cancel`)
  return response.data
}

export async function getOpenShiftClaims(params) {
  const response = await httpClient.get('/open-shift-claims', { params })
  return response.data
}

export async function reviewOpenShiftClaim(id, status, reviewerNote = null) {
  const response = await httpClient.patch(`/open-shift-claims/${id}/review`, {
    status,
    reviewerNote,
  })
  return response.data
}
