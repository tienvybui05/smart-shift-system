import httpClient from '../api/httpClient.js'

export async function getDashboard() {
  const response = await httpClient.get('/dashboard')
  return response.data
}
