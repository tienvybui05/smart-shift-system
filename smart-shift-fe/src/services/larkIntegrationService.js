import httpClient from '../api/httpClient.js'

export async function getLarkStatus() {
  const response = await httpClient.get('/integrations/lark/status')
  return response.data
}

export async function getLarkDeliveries(params) {
  const response = await httpClient.get('/integrations/lark/deliveries', { params })
  return response.data
}

export async function sendLarkTest() {
  const response = await httpClient.post('/integrations/lark/test')
  return response.data
}

export async function getLarkUsers(params) {
  const response = await httpClient.get('/integrations/lark/users', { params })
  return response.data
}

export async function syncLarkUser(id) {
  const response = await httpClient.post(`/integrations/lark/users/${id}/sync`)
  return response.data
}

export async function syncAllLarkUsers() {
  const response = await httpClient.post('/integrations/lark/users/sync-all')
  return response.data
}

export async function sendPersonalLarkTest(id) {
  const response = await httpClient.post(`/integrations/lark/users/${id}/test`)
  return response.data
}

export async function retryLarkDelivery(id) {
  const response = await httpClient.post(`/integrations/lark/deliveries/${id}/retry`)
  return response.data
}
