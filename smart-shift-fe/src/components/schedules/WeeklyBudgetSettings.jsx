import { Alert, Button, Checkbox, InputNumber, Space, Spin, message } from 'antd'
import { useEffect, useState } from 'react'
import { getWeeklyBudget, updateWeeklyBudget } from '../../services/schedulingBudgetService.js'
import { getApiErrorMessage } from '../../api/apiError.js'

export default function WeeklyBudgetSettings({ locationId }) {
  const [settings, setSettings] = useState(null)
  const [enabled, setEnabled] = useState(false)
  const [amount, setAmount] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [api, context] = message.useMessage()
  useEffect(() => {
    let live = true
    getWeeklyBudget(locationId).then(data => {
      if (live) { setSettings(data); setEnabled(data.weeklyBudget !== null); setAmount(data.weeklyBudget === null ? null : String(data.weeklyBudget)); setError(''); setLoading(false) }
    }).catch(err => { if (live) { setError(getApiErrorMessage(err, 'Không tải được ngân sách')); setLoading(false) } })
    return () => { live = false }
  }, [locationId, retry])
  async function save() {
    if (enabled && amount === null) { api.error('Nhập số tiền ngân sách'); return }
    setBusy(true)
    try {
      const data = await updateWeeklyBudget(locationId, enabled ? amount : null)
      setSettings(data)
      api.success('Đã lưu ngân sách tuần. Lần xếp lịch tiếp theo sẽ dùng cấu hình này.')
    } catch (err) { api.error(getApiErrorMessage(err, 'Không lưu được ngân sách')) }
    finally { setBusy(false) }
  }
  return <section className="management-card" style={{ padding: 16, marginBottom: 16 }}>
    {context}<h2>Ngân sách xếp lịch theo tuần</h2>
    {loading ? <Spin /> : error ? <Alert type="error" title={error} action={<Button onClick={() => { setLoading(true); setRetry(v => v + 1) }}>Thử lại</Button>} /> : <>
      <p>{settings?.locationName} · {settings?.timezone} · Tuần từ thứ Hai đến Chủ nhật</p>
      <Space wrap>
        <Checkbox checked={enabled} disabled={busy} onChange={e => setEnabled(e.target.checked)}>Thiết lập ngân sách</Checkbox>
        <InputNumber aria-label="Ngân sách tuần (VND)" stringMode min="0" max="9999999999999.99" precision={2}
          style={{ width: 240 }} value={amount} disabled={!enabled || busy} onChange={setAmount} suffix="VND"
          formatter={value => value === '' || value == null ? '' : `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={value => value?.replace(/,/g, '')} />
        <Button type="primary" loading={busy} onClick={save}>Lưu ngân sách</Button>
      </Space>
      <p>Ngân sách là mục tiêu mềm: lịch ưu tiên đủ người, sau đó giảm chi phí và phần vượt. Bỏ chọn để không giới hạn; nhập 0 để đặt ngân sách bằng 0.</p>
      <small>Cấu hình áp dụng cho dự toán hiện tại và lần xếp lịch tiếp theo; không tự thay đổi các phân công đã lưu.</small>
    </>}
  </section>
}
