import {
  Alert,
  Button,
  Col,
  Drawer,
  Empty,
  Form,
  InputNumber,
  Row,
  Select,
  Spin,
  Switch,
  message,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import { getShiftRequirements } from '../../services/shiftRequirementService.js'

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function normalizeTime(value) {
  return value?.slice(0, 5) || '--:--'
}

function toFormValues(summary) {
  return {
    applyToSameTemplate: false,
    requirements: summary.requirements.map((requirement) => ({
      positionId: requirement.positionId,
      minEmployees: Number(requirement.minEmployees),
      maxEmployees: Number(requirement.maxEmployees),
      priority: Number(requirement.priority),
    })),
  }
}

export default function ShiftRequirementDrawer({
  open,
  workShift,
  positions,
  editable,
  onClose,
  onSubmit,
}) {
  const [form] = Form.useForm()
  const [messageApi, messageContext] = message.useMessage()
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const requirements = Form.useWatch('requirements', form) || []

  useEffect(() => {
    if (!open || !workShift) return undefined
    let mounted = true
    getShiftRequirements(workShift.id)
      .then((result) => {
        if (mounted) form.setFieldsValue(toFormValues(result))
      })
      .catch((error) => {
        if (mounted) {
          messageApi.error(getApiErrorMessage(error, 'Không thể tải nhu cầu nhân sự của ca.'))
        }
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [form, messageApi, open, workShift])

  const positionOptions = useMemo(
    () => positions.map((position) => ({
      label: `${position.code} — ${position.name}${position.active ? '' : ' (Ngừng hoạt động)'}`,
      value: position.id,
      disabled: !position.active,
    })),
    [positions],
  )

  const totals = requirements.reduce(
    (result, requirement) => ({
      minimum: result.minimum + Number(requirement?.minEmployees || 0),
      maximum: result.maximum + Number(requirement?.maxEmployees || 0),
    }),
    { minimum: 0, maximum: 0 },
  )

  function handleClose() {
    if (submitting) return
    form.resetFields()
    setLoading(true)
    onClose()
  }

  async function handleFinish(values) {
    const rows = values.requirements || []
    const positionIds = rows.map((row) => row.positionId)
    if (new Set(positionIds).size !== positionIds.length) {
      messageApi.error('Mỗi vị trí chỉ được khai báo một lần.')
      return
    }

    const invalidIndex = rows.findIndex(
      (row) => Number(row.maxEmployees) < Number(row.minEmployees),
    )
    if (invalidIndex >= 0) {
      form.setFields([{
        name: ['requirements', invalidIndex, 'maxEmployees'],
        errors: ['Số tối đa không được nhỏ hơn số tối thiểu'],
      }])
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        applyToSameTemplate: Boolean(values.applyToSameTemplate),
        requirements: rows,
      })
      form.resetFields()
      setLoading(true)
      onClose()
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể lưu nhu cầu nhân sự.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      {messageContext}
      <Drawer
        className="shift-requirement-drawer"
        destroyOnHidden
        maskClosable={!submitting}
        onClose={handleClose}
        open={open}
        size="large"
        title="Nhu cầu nhân sự"
        extra={editable && !loading ? (
          <div className="drawer-actions">
            <Button onClick={handleClose} disabled={submitting}>Hủy</Button>
            <Button
              type="primary"
              htmlType="submit"
              form="shift-requirement-form"
              loading={submitting}
            >
              Lưu nhu cầu
            </Button>
          </div>
        ) : null}
      >
        <div className="requirement-shift-summary">
          <span
            className="shift-color-dot"
            style={{ backgroundColor: workShift?.colorCode || '#5B4CF0' }}
          />
          <div>
            <strong>{workShift?.shiftTemplateName}</strong>
            <span>
              {formatDate(workShift?.workDate)} · {normalizeTime(workShift?.startTime)}–{normalizeTime(workShift?.endTime)}
            </span>
          </div>
        </div>

        {!editable && (
          <Alert
            className="requirement-readonly-alert"
            message="Chế độ chỉ xem"
            description="Chỉ ca đang mở trong kỳ xếp lịch Nháp mới được thay đổi nhu cầu."
            showIcon
            type="warning"
          />
        )}

        {loading ? (
          <div className="requirement-loading"><Spin /></div>
        ) : (
          <Form
            disabled={!editable}
            form={form}
            id="shift-requirement-form"
            layout="vertical"
            onFinish={handleFinish}
            requiredMark="optional"
          >
            <div className="requirement-total-bar">
              <div>
                <span>Tổng tối thiểu</span>
                <strong>{totals.minimum} người</strong>
              </div>
              <div>
                <span>Tổng tối đa</span>
                <strong>{totals.maximum} người</strong>
              </div>
            </div>

            <p className="requirement-priority-help">
              Mức ưu tiên từ 1 đến 10; số 1 là ưu tiên cao nhất khi hệ thống tự động xếp lịch.
            </p>

            <Form.List name="requirements">
              {(fields, { add, remove }) => (
                <div className="requirement-list">
                  {fields.length === 0 && (
                    <Empty
                      description="Ca này chưa có nhu cầu nhân sự"
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                  )}

                  {fields.map((field, index) => (
                    <div className="requirement-row" key={field.key}>
                      <Row gutter={12} align="top">
                        <Col xs={24} lg={9}>
                          <Form.Item
                            label={index === 0 ? 'Vị trí' : undefined}
                            name={[field.name, 'positionId']}
                            rules={[{ required: true, message: 'Chọn vị trí' }]}
                          >
                            <Select
                              showSearch
                              optionFilterProp="label"
                              options={positionOptions}
                              placeholder="Chọn vị trí"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={8} lg={4}>
                          <Form.Item
                            label={index === 0 ? 'Tối thiểu' : undefined}
                            name={[field.name, 'minEmployees']}
                            rules={[{ required: true, message: 'Nhập số lượng' }]}
                          >
                            <InputNumber min={0} max={1000} precision={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col xs={8} lg={4}>
                          <Form.Item
                            label={index === 0 ? 'Tối đa' : undefined}
                            name={[field.name, 'maxEmployees']}
                            rules={[{ required: true, message: 'Nhập số lượng' }]}
                          >
                            <InputNumber min={1} max={1000} precision={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col xs={6} lg={4}>
                          <Form.Item
                            label={index === 0 ? 'Ưu tiên' : undefined}
                            name={[field.name, 'priority']}
                            rules={[{ required: true, message: 'Nhập ưu tiên' }]}
                          >
                            <InputNumber min={1} max={10} precision={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col xs={2} lg={3} className={index === 0 ? 'requirement-delete--labeled' : ''}>
                          <Button
                            danger
                            disabled={!editable}
                            type="text"
                            icon={<DeleteOutlined />}
                            aria-label="Xóa vị trí"
                            onClick={() => remove(field.name)}
                          />
                        </Col>
                      </Row>
                    </div>
                  ))}

                  {editable && (
                    <Button
                      className="requirement-add-button"
                      block
                      type="dashed"
                      icon={<PlusOutlined />}
                      disabled={fields.length >= positions.filter((position) => position.active).length}
                      onClick={() => add({ minEmployees: 1, maxEmployees: 1, priority: 1 })}
                    >
                      Thêm vị trí cần nhân sự
                    </Button>
                  )}
                </div>
              )}
            </Form.List>

            {editable && workShift?.shiftTemplateId && (
              <Form.Item
                className="requirement-apply-switch"
                label="Áp dụng nhanh"
                name="applyToSameTemplate"
                valuePropName="checked"
              >
                <Switch
                  checkedChildren="Tất cả ca cùng mẫu"
                  unCheckedChildren="Chỉ ca này"
                />
              </Form.Item>
            )}
          </Form>
        )}
      </Drawer>
    </>
  )
}
