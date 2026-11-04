import { Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getMyWorkSchedule } from '../../services/myScheduleService.js'
import { listCorrections, createCorrection, cancelCorrection, reviewCorrection } from '../../services/attendanceCorrectionService.js'
import AttendanceHistoryButton, { AttendanceSnapshot } from './AttendanceHistoryButton.jsx'

const statuses = { PENDING: ['Chờ duyệt', 'warning'], APPROVED: ['Đã duyệt', 'success'], REJECTED: ['Từ chối', 'error'], CANCELLED: ['Đã hủy', 'default'] }
function localDay(value) {
  return new Date(value.getTime() - value.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
function defaultRange() {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - 29)
  return { startDate: localDay(start), endDate: localDay(end) }
}
export default function AttendanceCorrectionsPanel({ reviewer = false, locations = [], onChanged }) {
  const [query, setQuery] = useState(defaultRange)
  const [inputs, setInputs] = useState(defaultRange)
  const [rows, setRows] = useState([])
  const [shifts, setShifts] = useState([])
  const [loading, setLoading] = useState(true)
  const [refresh, setRefresh] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [reviewing, setReviewing] = useState(null)
  const [busy, setBusy] = useState(false)
  const [form] = Form.useForm()
  const [reviewForm] = Form.useForm()
  const [api, context] = message.useMessage()
  const deviceZone = Intl.DateTimeFormat().resolvedOptions().timeZone

  useEffect(() => {
    let live = true
    Promise.all([
      listCorrections(query, !reviewer),
      reviewer ? Promise.resolve([]) : getMyWorkSchedule({ startDate: query.startDate, endDate: query.endDate }),
    ]).then(([requests, schedule]) => {
      if (live) { setRows(requests); setShifts(schedule); setLoading(false) }
    }).catch(error => {
      if (live) { api.error(getApiErrorMessage(error, 'Không tải được yêu cầu công')); setLoading(false) }
    })
    return () => { live = false }
  }, [query, reviewer, refresh, api])

  function reload() { setLoading(true); setRefresh(value => value + 1) }
  async function send(values) {
    setBusy(true)
    try {
      await createCorrection({ ...values, checkInAt: new Date(values.checkInAt).toISOString(), checkOutAt: new Date(values.checkOutAt).toISOString(), reason: values.reason.trim() })
      api.success('Đã gửi yêu cầu. Giờ công chỉ đổi sau khi quản lý duyệt.')
      setCreateOpen(false)
      form.resetFields()
      reload()
    } catch (error) { api.error(getApiErrorMessage(error, 'Không gửi được yêu cầu công')) }
    finally { setBusy(false) }
  }
  async function cancel(id) {
    setBusy(true)
    try { await cancelCorrection(id); api.success('Đã hủy yêu cầu'); reload() }
    catch (error) { api.error(getApiErrorMessage(error, 'Không hủy được yêu cầu')) }
    finally { setBusy(false) }
  }
  async function review(values) {
    setBusy(true)
    try {
      await reviewCorrection(reviewing.id, { approved: values.approved, note: values.note.trim() })
      api.success(values.approved ? 'Đã duyệt và cập nhật công. Bảng lương nháp liên quan cần tính lại.' : 'Đã từ chối yêu cầu')
      setReviewing(null)
      reviewForm.resetFields()
      reload()
      onChanged?.()
    } catch (error) { api.error(getApiErrorMessage(error, 'Không xử lý được yêu cầu')) }
    finally { setBusy(false) }
  }

  const columns = [
    ...(reviewer ? [{ title: 'Nhân viên', render: (_, row) => <div>{row.employeeName}<br /><small>{row.employeeCode} · {row.locationName}</small></div> }] : []),
    { title: 'Ngày ca', dataIndex: 'workDate' },
    { title: 'Trước khi gửi', render: (_, row) => <AttendanceSnapshot value={row.beforeData} timezone={row.timezone} /> },
    { title: 'Đề xuất', render: (_, row) => <AttendanceSnapshot value={row} timezone={row.timezone} /> },
    { title: 'Lý do', dataIndex: 'reason', width: 220 },
    { title: 'Trạng thái', dataIndex: 'status', render: value => <Tag color={statuses[value]?.[1]}>{statuses[value]?.[0] || value}</Tag> },
    { title: 'Kết quả xử lý', render: (_, row) => <div>{row.reviewedByName}{row.reviewNote && <p>{row.reviewNote}</p>}</div>, width: 200 },
    { title: 'Thao tác', render: (_, row) => <Space wrap>
      {row.status === 'PENDING' && (reviewer
        ? <Button type="primary" size="small" disabled={busy} onClick={() => { reviewForm.resetFields(); setReviewing(row) }}>Xét duyệt</Button>
        : <Popconfirm title="Hủy yêu cầu này?" onConfirm={() => cancel(row.id)}><Button size="small" disabled={busy}>Hủy</Button></Popconfirm>)}
      <AttendanceHistoryButton assignmentId={row.shiftAssignmentId} mine={!reviewer} timezone={row.timezone} />
    </Space> },
  ]
  return <section className="management-card" style={{ marginTop: 16, padding: 16 }}>
    {context}
    <h2>{reviewer ? 'Duyệt yêu cầu điều chỉnh công' : 'Yêu cầu bổ sung / điều chỉnh công'}</h2>
    <Alert type="info" showIcon title="Áp dụng cho ca đã kết thúc trong 30 ngày. Công thuộc kỳ lương đã xác nhận không thể điều chỉnh." />
    <Space wrap style={{ margin: '16px 0' }}>
      <Input aria-label="Yêu cầu công từ ngày" type="date" value={inputs.startDate} onChange={e => setInputs({ ...inputs, startDate: e.target.value })} />
      <Input aria-label="Yêu cầu công đến ngày" type="date" value={inputs.endDate} onChange={e => setInputs({ ...inputs, endDate: e.target.value })} />
      {reviewer && <Select aria-label="Lọc trạng thái yêu cầu công" allowClear placeholder="Tất cả trạng thái" style={{ minWidth: 170 }}
        value={inputs.status} options={Object.entries(statuses).map(([value, [label]]) => ({ value, label }))}
        onChange={status => setInputs({ ...inputs, status })} />}
      {reviewer && locations.length > 1 && <Select aria-label="Chi nhánh yêu cầu công" allowClear placeholder="Tất cả chi nhánh" style={{ minWidth: 180 }}
        value={inputs.locationId} options={locations.map(item => ({ value: item.id, label: item.name }))}
        onChange={locationId => setInputs({ ...inputs, locationId })} />}
      <Button onClick={() => { setLoading(true); setQuery({ ...inputs }) }}>Tra cứu</Button>
      <Button onClick={reload}>Làm mới</Button>
      {!reviewer && <Button type="primary" onClick={() => { form.resetFields(); setCreateOpen(true) }}>Gửi yêu cầu</Button>}
    </Space>
    <Table rowKey="id" dataSource={rows} columns={columns} loading={loading} scroll={{ x: 1200 }} pagination={{ pageSize: 10 }}
      locale={{ emptyText: 'Chưa có yêu cầu trong khoảng ngày đã chọn.' }} />
    <Modal title="Yêu cầu bổ sung / điều chỉnh công" open={createOpen} onCancel={() => setCreateOpen(false)}
      confirmLoading={busy} onOk={() => form.submit()} okText="Gửi yêu cầu" cancelText="Đóng">
      <Alert type="info" showIcon title={`Giờ nhập theo múi giờ thiết bị: ${deviceZone}. Ngày ca trong danh sách theo chi nhánh.`} />
      <Form form={form} layout="vertical" onFinish={send} initialValues={{ breakMinutes: 0 }} style={{ marginTop: 16 }}>
        <Form.Item label="Ca được phân công" name="shiftAssignmentId" rules={[{ required: true, message: 'Chọn ca cần bổ sung hoặc điều chỉnh công' }]}>
          <Select showSearch optionFilterProp="label" options={shifts.filter(s => s.workDate <= localDay(new Date()) &&
            ['ASSIGNED', 'CONFIRMED'].includes(s.assignmentStatus) && ['PUBLISHED', 'LOCKED'].includes(s.schedulePeriodStatus))
            .map(s => ({ value: s.assignmentId, label: `${s.workDate} · ${s.startTime}–${s.endTime}${s.overnight ? ' (+1 ngày)' : ''} · ${s.locationName}` }))}
            placeholder="Ca trong khoảng ngày đã tra cứu" />
        </Form.Item>
        <Form.Item label="Giờ vào đề xuất" name="checkInAt" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
        <Form.Item label="Giờ ra đề xuất" name="checkOutAt" rules={[{ required: true }]}><Input type="datetime-local" /></Form.Item>
        <Form.Item label="Nghỉ (phút)" name="breakMinutes" rules={[{ required: true }]}><InputNumber min={0} max={1440} precision={0} /></Form.Item>
        <Form.Item label="Lý do" name="reason" rules={[{ required: true, whitespace: true }, { max: 1000 }]}>
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
      </Form>
    </Modal>
    <Modal title="Xét duyệt điều chỉnh công" open={Boolean(reviewing)} onCancel={() => setReviewing(null)}
      confirmLoading={busy} onOk={() => reviewForm.submit()} okText="Xác nhận quyết định" cancelText="Đóng">
      {reviewing && <><p>{reviewing.employeeName} · {reviewing.workDate}</p><p>Lý do: {reviewing.reason}</p>
        <AttendanceSnapshot value={reviewing} timezone={reviewing.timezone} /></>}
      <Form form={reviewForm} onFinish={review} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item label="Quyết định" name="approved" rules={[{ required: true }]}>
          <Select options={[{ value: true, label: 'Duyệt và áp dụng giờ đề xuất' }, { value: false, label: 'Từ chối' }]} />
        </Form.Item>
        <Form.Item label="Lý do xử lý" name="note" rules={[{ required: true, whitespace: true }, { max: 1000 }]}>
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  </section>
}

