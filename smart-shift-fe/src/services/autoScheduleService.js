import httpClient from '../api/httpClient.js'

export async function generateAutomaticSchedule(schedulePeriodId) {
  const response = await httpClient.post('/schedules/generate', {
    schedulePeriodId,
  })
  return response.data
}
