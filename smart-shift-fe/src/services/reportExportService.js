import httpClient from '../api/httpClient.js'

export async function downloadExcel(kind, params) {
  const path = kind === 'attendance' ? '/attendances/export' : '/payroll/export'
  let response
  try {
    response = await httpClient.get(path, { params, responseType: 'blob', timeout: 120000 })
  } catch (error) {
    // Axios uses Blob even for JSON errors on a download request.
    if (error.response?.data instanceof Blob) {
      try { error.response.data = JSON.parse(await error.response.data.text()) } catch { /* Keep the original HTTP error. */ }
    }
    throw error
  }
  const fallback = `${kind === 'attendance' ? 'bang_cham_cong' : 'bang_luong'}_${params.startDate}_${params.endDate}.xlsx`
  const disposition = response.headers['content-disposition'] || ''
  const filename = disposition.match(/filename="?([a-zA-Z0-9_.-]+\.xlsx)"?/i)?.[1] || fallback
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
