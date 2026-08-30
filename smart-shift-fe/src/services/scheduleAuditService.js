import httpClient from '../api/httpClient.js'

export async function getScheduleAuditLogs(params) {
  const response = await httpClient.get('/schedule-audits', { params })
  return response.data
}
