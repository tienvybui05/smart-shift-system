import httpClient from '../api/httpClient.js'

export async function getShiftTemplates(params) {
  const response = await httpClient.get('/shift-templates', { params })
  return response.data
}

export async function createShiftTemplate(payload) {
  const response = await httpClient.post('/shift-templates', payload)
  return response.data
}

export async function updateShiftTemplate(id, payload) {
  const response = await httpClient.put(`/shift-templates/${id}`, payload)
  return response.data
}

export async function updateShiftTemplateStatus(id, active) {
  const response = await httpClient.patch(`/shift-templates/${id}/status`, { active })
  return response.data
}
