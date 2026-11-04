import { Alert, Button, Spin, Table } from 'antd'
import { useEffect, useState } from 'react'
import { getScheduleBudget } from '../../services/schedulingBudgetService.js'
import { getApiErrorMessage } from '../../api/apiError.js'

const money = value => value == null ? 'Chưa cấu hình' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 2 }).format(Number(value))
export function ScheduleBudgetSummary({ data }) {
  if (!data) return null
  const columns = [
    { title: 'Tuần', render: (_, row) => `${row.startDate} → ${row.endDate}`, width: 200 },
    { title: 'Ngân sách tuần', render: () => money(data.weeklyBudget) },
    { title: 'Kỳ này', dataIndex: 'periodCost', render: money },
    { title: 'Kỳ khác', dataIndex: 'otherPeriodsCost', render: money },
    { title: 'Tổng tuần', dataIndex: 'totalCost', render: money },
    { title: 'Vượt ngân sách', dataIndex: 'excess', render: value => <span style={{ color: Number(value) > 0 ? '#cf1322' : undefined }}>{money(value)}</span> },
    { title: 'Còn lại', dataIndex: 'remaining', render: value => value == null ? '—' : money(value) },
  ]
  return <section style={{ margin: '16px 0' }}>
    <h3>Chi phí lịch dự kiến</h3>
    <Alert showIcon type={data.overBudget ? 'warning' : 'info'}
      title={data.overBudget ? 'Lịch đang vượt ngân sách tuần' : data.weeklyBudget == null ? 'Chi nhánh chưa cấu hình ngân sách' : 'Chi phí đã phân công nằm trong ngân sách'}
      description={`Chi phí riêng kỳ này: ${money(data.periodCost)}. Tổng tuần bao gồm cả các kỳ khác cùng chi nhánh. Vượt ngân sách là cảnh báo và vẫn được phép công bố khi lịch hợp lệ.`} />
    <Table rowKey="startDate" dataSource={data.weeks} columns={columns} size="small" pagination={false} scroll={{ x: 1000 }} />
    <small>Theo mức lương hiện tại × hệ số × giờ ca sau khi trừ nghỉ; chỉ tính phân công còn hiệu lực, bỏ ca hủy. Đây là dự toán ca đã xếp, chưa gồm thưởng hay hệ số trả thêm cho tăng ca; lương thực tế dùng công đã duyệt. Ngân sách tính cho cả tuần theo múi giờ {data.timezone}, không chia theo số ngày của kỳ.</small>
  </section>
}
export default function ScheduleBudgetPanel({ periodId, refreshKey = 0 }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let live = true
    getScheduleBudget(periodId).then(result => { if (live) { setData(result); setError('') } })
      .catch(err => { if (live) setError(getApiErrorMessage(err, 'Không tải được dự toán ngân sách')) })
    return () => { live = false }
  }, [periodId, refreshKey, retry])
  if (error) return <Alert type="error" title={error} action={<Button onClick={() => setRetry(v => v + 1)}>Thử lại</Button>} />
  return data ? <ScheduleBudgetSummary data={data} /> : <Spin tip="Đang tính chi phí lịch" />
}
