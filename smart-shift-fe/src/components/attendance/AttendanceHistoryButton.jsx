import { Button, Modal, Table, message } from 'antd'
import { useState } from 'react'
import { attendanceHistory } from '../../services/attendanceCorrectionService.js'
import { getApiErrorMessage } from '../../api/apiError.js'

function snapshot(value) {
  try { return JSON.parse(value) } catch { return null }
}
function date(value, timezone) {
  return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: timezone }).format(new Date(value)) : 'Chưa có'
}
export function AttendanceSnapshot({ value, timezone }) {
  const data = typeof value === 'string' ? snapshot(value) : value
  if (!data) return <div>Chưa có bản công</div>
  return <div>
    <div>Vào: {date(data.checkInAt, timezone)}</div>
    <div>Ra: {date(data.checkOutAt, timezone)}</div>
    <div>Nghỉ: {data.breakMinutes ?? 0} phút</div>
    {'approvedAt' in data && <div>Đối soát: {data.approvedAt ? 'Đã duyệt' : 'Chưa duyệt'}</div>}
    {'unverifiedWifi' in data && <div>Mạng: {data.unverifiedWifi ? 'Chưa xác minh' : 'Đạt chính sách'}</div>}
    {'unverifiedDevice' in data && <div>Thiết bị: {data.unverifiedDevice ? 'Chưa xác minh' : 'Đã xác minh'}</div>}
  </div>
}

export default function AttendanceHistoryButton({ assignmentId, mine = false, timezone }) {
  const [open, setOpen] = useState(false)
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [api, context] = message.useMessage()
  async function show() {
    setLoading(true)
    setRows([])
    setOpen(true)
    try { setRows(await attendanceHistory(assignmentId, mine)) }
    catch (error) { api.error(getApiErrorMessage(error, 'Không tải được lịch sử công')) }
    finally { setLoading(false) }
  }
  const actions = { CHECK_IN: 'Vào ca', CHECK_OUT: 'Ra ca', MANUAL_APPROVAL: 'Đối soát trực tiếp', CORRECTION_APPROVED: 'Duyệt điều chỉnh' }
  return <>
    {context}
    <Button size="small" onClick={show}>Lịch sử</Button>
    <Modal title="Lịch sử thay đổi công" open={open} onCancel={() => setOpen(false)} footer={null} width={1000}>
      <Table rowKey="id" dataSource={rows} loading={loading} pagination={{ pageSize: 5 }} scroll={{ x: 800 }}
        locale={{ emptyText: 'Chưa có lịch sử. Dữ liệu trước khi nâng cấp không được tạo lịch sử hồi tố.' }}
        columns={[
          { title: 'Người xử lý', dataIndex: 'actorName' },
          { title: 'Thao tác', dataIndex: 'action', render: value => actions[value] || value },
          { title: 'Lý do', dataIndex: 'reason' },
          { title: 'Thời điểm', dataIndex: 'createdAt', render: value => date(value, timezone) },
        ]}
        expandable={{ expandedRowRender: row => <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
          <div><strong>Trước thay đổi</strong><AttendanceSnapshot value={row.beforeData} timezone={timezone} /></div>
          <div><strong>Sau thay đổi</strong><AttendanceSnapshot value={row.afterData} timezone={timezone} /></div>
        </div> }}
      />
    </Modal>
  </>
}

