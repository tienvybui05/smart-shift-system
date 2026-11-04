import httpClient from '../api/httpClient.js'
export async function getWeeklyBudget(locationId) {
  return (await httpClient.get(`/scheduling-budgets/locations/${locationId}`)).data
}
export async function updateWeeklyBudget(locationId, weeklyBudget) {
  return (await httpClient.patch(`/scheduling-budgets/locations/${locationId}`, { weeklyBudget })).data
}
export async function getScheduleBudget(periodId) {
  return (await httpClient.get(`/scheduling-budgets/periods/${periodId}`)).data
}
