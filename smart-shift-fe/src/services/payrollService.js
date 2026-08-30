import httpClient from '../api/httpClient.js'

export async function getPayrollRecords(params) {
  const response = await httpClient.get('/payroll', { params })
  return response.data
}

export async function getMyPayrollRecords(params) {
  const response = await httpClient.get('/payroll/me', { params })
  return response.data
}

export async function calculatePayroll(payload) {
  const response = await httpClient.post('/payroll/calculate', payload)
  return response.data
}

export async function updatePayrollBonus(id, payload) {
  const response = await httpClient.patch(`/payroll/${id}/bonus`, payload)
  return response.data
}

export async function confirmPayroll(id) {
  const response = await httpClient.post(`/payroll/${id}/confirm`)
  return response.data
}
