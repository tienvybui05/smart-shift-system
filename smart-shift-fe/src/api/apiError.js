export function getApiErrorMessage(error, fallbackMessage) {
  if (!error.response) {
    return 'Không thể kết nối đến máy chủ. Vui lòng thử lại.'
  }
  return error.response.data?.message || fallbackMessage
}
