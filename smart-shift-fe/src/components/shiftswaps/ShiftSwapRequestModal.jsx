import { Alert, Button, Form, Input, Modal, Radio, Select, Spin, Tag } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getShiftSwapCandidates } from '../../services/shiftSwapService.js'
import {
  formatShiftSwapAssignment,
  formatShiftSwapDate,
  formatShiftSwapTime,
} from './shiftSwapDisplay.js'

function assignmentOption(assignment) {
  return {
    label: `${assignment.shiftTemplateName} · ${formatShiftSwapDate(assignment.workDate)} · ${formatShiftSwapTime(assignment.startTime)}–${formatShiftSwapTime(assignment.endTime)}`,
    value: assignment.assignmentId,
  }
}

export default function ShiftSwapRequestModal({
  assignments,
  onClose,
  onSubmit,
  submitting,
}) {
  const [form] = Form.useForm()
  const [type, setType] = useState('GIVEAWAY')
  const [requesterAssignmentId, setRequesterAssignmentId] = useState(null)
  const [candidates, setCandidates] = useState([])
  const [candidatesLoading, setCandidatesLoading] = useState(false)
  const [candidateError, setCandidateError] = useState('')

  useEffect(() => {
    if (type !== 'SWAP' || !requesterAssignmentId) {
      return undefined
    }

    let mounted = true
    getShiftSwapCandidates(requesterAssignmentId)
      .then((result) => {
        if (mounted) setCandidates(result)
      })
      .catch((error) => {
        if (mounted) {
          setCandidates([])
          setCandidateError(getApiErrorMessage(
            error,
            'Không thể tải danh sách ca có thể đổi.',
          ))
        }
      })
      .finally(() => {
        if (mounted) setCandidatesLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [form, requesterAssignmentId, type])

  function handleTypeChange(event) {
    const nextType = event.target.value
    setType(nextType)
    setCandidates([])
    setCandidateError('')
    setCandidatesLoading(nextType === 'SWAP' && Boolean(requesterAssignmentId))
    form.setFieldValue('targetAssignmentId', undefined)
  }

  function handleRequesterAssignmentChange(value) {
    setRequesterAssignmentId(value)
    setCandidates([])
    setCandidateError('')
    setCandidatesLoading(type === 'SWAP' && Boolean(value))
    form.setFieldValue('targetAssignmentId', undefined)
  }

  const selectedAssignment = useMemo(
    () => assignments.find((item) => item.assignmentId === requesterAssignmentId),
    [assignments, requesterAssignmentId],
  )
  const eligibleCandidates = candidates.filter((candidate) => candidate.eligible)

  function handleFinish(values) {
    onSubmit({
      requesterAssignmentId: values.requesterAssignmentId,
      targetAssignmentId: type === 'SWAP' ? values.targetAssignmentId : null,
      reason: values.reason?.trim() || null,
    })
  }

  return (
    <Modal
      centered
      destroyOnHidden
      footer={null}
      onCancel={onClose}
      open
      title="Tạo yêu cầu đổi hoặc nhường ca"
      width={680}
    >
      <Alert
        className="shift-swap-modal-alert"
        description={type === 'GIVEAWAY'
          ? 'Ca được đăng công khai cho nhân viên cùng vị trí. Sau khi có người nhận, quản lý phải duyệt thì lịch mới thay đổi.'
          : 'Chọn một ca của đồng nghiệp trong cùng kỳ lịch. Đồng nghiệp xác nhận trước, sau đó quản lý xét duyệt.'}
        message={type === 'GIVEAWAY' ? 'Nhường ca công khai' : 'Đổi ca trực tiếp'}
        showIcon
        type="info"
      />

      <Form
        form={form}
        initialValues={{ type: 'GIVEAWAY' }}
        layout="vertical"
        onFinish={handleFinish}
      >
        <Form.Item label="Hình thức" name="type">
          <Radio.Group
            buttonStyle="solid"
            onChange={handleTypeChange}
          >
            <Radio.Button value="GIVEAWAY">Nhường ca</Radio.Button>
            <Radio.Button value="SWAP">Đổi ca</Radio.Button>
          </Radio.Group>
        </Form.Item>

        <Form.Item
          label="Ca của bạn"
          name="requesterAssignmentId"
          rules={[{ required: true, message: 'Vui lòng chọn ca của bạn.' }]}
        >
          <Select
            onChange={handleRequesterAssignmentChange}
            options={assignments.map(assignmentOption)}
            optionFilterProp="label"
            placeholder="Chọn ca muốn đổi hoặc nhường"
            showSearch
          />
        </Form.Item>

        {selectedAssignment && (
          <div className="shift-swap-selected-assignment">
            <span
              className="shift-color-dot"
              style={{ backgroundColor: selectedAssignment.colorCode || '#5B4CF0' }}
            />
            <div>
              <strong>{selectedAssignment.shiftTemplateName}</strong>
              <span>{selectedAssignment.locationName} · {selectedAssignment.positionName}</span>
            </div>
          </div>
        )}

        {type === 'SWAP' && (
          <Form.Item
            extra={candidateError || (
              requesterAssignmentId && !candidatesLoading && eligibleCandidates.length === 0
                ? 'Chưa có ca đối ứng nào thỏa toàn bộ ràng buộc.'
                : null
            )}
            label="Ca muốn nhận từ đồng nghiệp"
            name="targetAssignmentId"
            rules={[{ required: true, message: 'Vui lòng chọn ca đối ứng.' }]}
          >
            <Select
              disabled={!requesterAssignmentId || Boolean(candidateError)}
              loading={candidatesLoading}
              notFoundContent={candidatesLoading ? <Spin size="small" /> : 'Không có ca phù hợp'}
              optionFilterProp="label"
              options={eligibleCandidates.map((candidate) => ({
                label: `${candidate.fullName} (${candidate.employeeCode}) · ${formatShiftSwapAssignment(candidate.targetAssignment)}`,
                value: candidate.targetAssignmentId,
              }))}
              placeholder="Chọn đồng nghiệp và ca đối ứng"
              showSearch
            />
          </Form.Item>
        )}

        {type === 'SWAP' && candidates.some((candidate) => !candidate.eligible) && (
          <div className="shift-swap-ineligible-note">
            <Tag color="default">Đã ẩn {candidates.filter((candidate) => !candidate.eligible).length} ca không phù hợp</Tag>
          </div>
        )}

        <Form.Item
          label="Lý do"
          name="reason"
          rules={[{ max: 1000, message: 'Lý do không được vượt quá 1000 ký tự.' }]}
        >
          <Input.TextArea
            maxLength={1000}
            placeholder="Ví dụ: Có việc cá nhân và mong muốn đổi sang ca khác."
            rows={4}
            showCount
          />
        </Form.Item>

        <div className="modal-action-row">
          <Button disabled={submitting} onClick={onClose}>Hủy</Button>
          <Button htmlType="submit" loading={submitting} type="primary">
            Gửi yêu cầu
          </Button>
        </div>
      </Form>
    </Modal>
  )
}
