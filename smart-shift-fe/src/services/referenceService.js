import httpClient from '../api/httpClient.js'

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
