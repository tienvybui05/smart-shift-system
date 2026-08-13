import httpClient from '../api/httpClient.js'

export async function getLocations() {
  const response = await httpClient.get('/locations')
  return response.data
}

export async function getPositions() {
  const response = await httpClient.get('/positions')
  return response.data
}

export async function getUserReferences() {
  const [rolesResponse, locationsResponse, positionsResponse] = await Promise.all([
    httpClient.get('/roles'),
    httpClient.get('/locations'),
    httpClient.get('/positions'),
  ])

  return {
    roles: rolesResponse.data,
    locations: locationsResponse.data,
    positions: positionsResponse.data,
  }
}
