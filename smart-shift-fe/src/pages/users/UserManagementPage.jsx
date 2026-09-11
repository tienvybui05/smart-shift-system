import {
  EditOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  UnlockOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../api/apiError.js'
import ResetPasswordModal from '../../components/users/ResetPasswordModal.jsx'
import EmployeeWorkProfileDrawer from '../../components/users/EmployeeWorkProfileDrawer.jsx'
import UserFormDrawer from '../../components/users/UserFormDrawer.jsx'
import useAuth from '../../hooks/useAuth.js'
import { getPositions, getUserReferences } from '../../services/referenceService.js'
import {
  createUser,
  getUsers,
  resetUserPassword,
  updateUser,
  updateEmployeeWorkProfile,
  updateUserStatus,
} from '../../services/userService.js'

const ROLE_LABELS = {
  ROLE_ADMIN: 'Quản trị viên',
  ROLE_MANAGER: 'Quản lý',
  ROLE_EMPLOYEE: 'Nhân viên',
}

const ROLE_COLORS = {
  ROLE_ADMIN: 'purple',
  ROLE_MANAGER: 'blue',
  ROLE_EMPLOYEE: 'default',
}

const EMPLOYMENT_TYPE_LABELS = {
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
  SEASONAL: 'Thời vụ',
}

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const EMPTY_REFERENCES = {
  roles: [],
  locations: [],
  positions: [],
}

function getRoleLabel(roleName) {
  return ROLE_LABELS[roleName] || roleName
}

export default function UserManagementPage() {
  const { user: currentUser } = useAuth()
  const isAdmin = currentUser?.role === 'ROLE_ADMIN'
  const [messageApi, messageContext] = message.useMessage()
  const [pageData, setPageData] = useState(EMPTY_PAGE)
  const [references, setReferences] = useState(EMPTY_REFERENCES)
  const [referencesLoading, setReferencesLoading] = useState(true)
  const [referenceError, setReferenceError] = useState('')
  const [loading, setLoading] = useState(true)
  const [searchText, setSearchText] = useState('')
  const [filters, setFilters] = useState({
    keyword: '',
    locationId: undefined,
    positionId: undefined,
    active: undefined,
  })
  const [pagination, setPagination] = useState({ page: 0, size: 10 })
  const [refreshKey, setRefreshKey] = useState(0)
  const [referenceRefreshKey, setReferenceRefreshKey] = useState(0)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingUser, setEditingUser] = useState(null)
  const [resetPasswordUser, setResetPasswordUser] = useState(null)
  const [statusChangingId, setStatusChangingId] = useState(null)

  useEffect(() => {
    let active = true
    const request = isAdmin
      ? getUserReferences()
      : getPositions().then((positions) => ({ ...EMPTY_REFERENCES, positions }))

    request
      .then((result) => {
        if (active) setReferences(result)
      })
      .catch((error) => {
        if (active) {
          setReferenceError(getApiErrorMessage(error, 'Không thể tải danh mục vai trò, chi nhánh và vị trí.'))
        }
      })
      .finally(() => {
        if (active) setReferencesLoading(false)
      })

    return () => {
      active = false
    }
  }, [isAdmin, referenceRefreshKey])

  useEffect(() => {
    let active = true
    getUsers({
        page: pagination.page,
        size: pagination.size,
        keyword: filters.keyword || undefined,
        locationId: filters.locationId,
        positionId: filters.positionId,
        active: filters.active,
      })
      .then((result) => {
        if (active) setPageData(result)
      })
      .catch((error) => {
        if (active) {
          setPageData(EMPTY_PAGE)
          messageApi.error(getApiErrorMessage(error, 'Không thể tải danh sách nhân viên.'))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [filters, messageApi, pagination, refreshKey])

  function applyFilter(name, value) {
    setLoading(true)
    setFilters((current) => ({ ...current, [name]: value }))
    setPagination((current) => ({ ...current, page: 0 }))
  }

  function handleSearch() {
    applyFilter('keyword', searchText.trim())
  }

  function handleResetFilters() {
    setLoading(true)
    setSearchText('')
    setFilters({
      keyword: '',
      locationId: undefined,
      positionId: undefined,
      active: undefined,
    })
    setPagination((current) => ({ ...current, page: 0 }))
  }

  function openCreateDrawer() {
    setEditingUser(null)
    setDrawerOpen(true)
  }

  function openEditDrawer(user) {
    setEditingUser(user)
    setDrawerOpen(true)
  }

  async function handleSaveUser(payload) {
    if (!isAdmin) {
      await updateEmployeeWorkProfile(editingUser.id, payload)
      messageApi.success('Đã cập nhật thông tin công việc của nhân viên.')
      setLoading(true)
      setRefreshKey((current) => current + 1)
      return
    }
    if (editingUser) {
      await updateUser(editingUser.id, payload)
      messageApi.success('Đã cập nhật thông tin nhân viên.')
    } else {
      const createdUser = await createUser(payload)
      messageApi.success(`Đã tạo tài khoản với mã ${createdUser.employeeCode}.`)
      setPagination((current) => ({ ...current, page: 0 }))
    }
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  async function handleChangeStatus(user) {
    setStatusChangingId(user.id)
    try {
      await updateUserStatus(user.id, !user.active)
      messageApi.success(user.active ? 'Đã khóa tài khoản.' : 'Đã mở tài khoản.')
      setLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (error) {
      messageApi.error(getApiErrorMessage(error, 'Không thể thay đổi trạng thái tài khoản.'))
    } finally {
      setStatusChangingId(null)
    }
  }

  async function handleResetPassword(payload) {
    await resetUserPassword(resetPasswordUser.id, payload)
    messageApi.success(`Đã reset mật khẩu cho ${resetPasswordUser.fullName}.`)
  }

  function handleRetryReferences() {
    setReferencesLoading(true)
    setReferenceError('')
    setReferenceRefreshKey((current) => current + 1)
  }

  function handleRefresh() {
    setLoading(true)
    setRefreshKey((current) => current + 1)
  }

  function handlePageChange(page, size) {
    setLoading(true)
    setPagination({ page: page - 1, size })
  }

  const columns = [
    {
      title: 'Nhân viên',
      key: 'employee',
      fixed: 'left',
      width: 220,
      render: (_, user) => (
        <div className="employee-cell">
          <strong>{user.fullName}</strong>
          <span>{user.employeeCode}</span>
        </div>
      ),
    },
    {
      title: 'Tài khoản',
      dataIndex: 'username',
      width: 150,
      responsive: ['md'],
      render: (username, user) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{username}</strong>
          <span>{user.email || 'Chưa có email'}</span>
        </div>
      ),
    },
    {
      title: 'Vai trò',
      dataIndex: 'roleName',
      width: 130,
      responsive: ['md'],
      render: (roleName) => <Tag color={ROLE_COLORS[roleName]}>{getRoleLabel(roleName)}</Tag>,
    },
    {
      title: 'Nơi làm việc',
      key: 'workplace',
      width: 190,
      responsive: ['lg'],
      render: (_, user) => (
        <div className="employee-cell employee-cell--normal">
          <strong>{user.locationName}</strong>
          <span>{user.positionName || 'Không áp dụng vị trí'}</span>
        </div>
      ),
    },
    {
      title: 'Hợp đồng',
      dataIndex: 'employmentType',
      width: 135,
      responsive: ['xl'],
      render: (type) => EMPLOYMENT_TYPE_LABELS[type] || type,
    },
    {
      title: 'Giờ/tuần',
      key: 'hours',
      width: 100,
      align: 'center',
      responsive: ['xl'],
      render: (_, user) => user.roleName === 'ROLE_EMPLOYEE'
        ? `${Number(user.minHoursPerWeek)}–${Number(user.maxHoursPerWeek)}`
        : 'Không áp dụng',
    },
    {
      title: 'Đơn giá · hệ số',
      key: 'salary',
      width: 165,
      responsive: ['xl'],
      render: (_, user) => (
        <div className="employee-cell employee-cell--normal">
          <strong>
            {user.roleName === 'ROLE_ADMIN'
              ? 'Không áp dụng'
              : `${new Intl.NumberFormat('vi-VN').format(Number(user.basePayAmount || 0))} ${user.roleName === 'ROLE_MANAGER' ? 'đ/tháng' : 'đ/giờ'}`}
          </strong>
          {user.roleName !== 'ROLE_ADMIN' && (
            <span>Hệ số {Number(user.salaryCoefficient || 1)}</span>
          )}
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 115,
      render: (active) => (
        <Tag color={active ? 'success' : 'error'}>{active ? 'Hoạt động' : 'Đã khóa'}</Tag>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 135,
      align: 'center',
      render: (_, user) => {
        if (!isAdmin) {
          return (
            <Tooltip title="Cập nhật công việc">
              <Button type="text" icon={<EditOutlined />} onClick={() => openEditDrawer(user)} />
            </Tooltip>
          )
        }
        const isCurrentAccount = user.id === currentUser.id
        return (
          <Space size={2}>
            <Tooltip title="Cập nhật">
              <Button type="text" icon={<EditOutlined />} onClick={() => openEditDrawer(user)} />
            </Tooltip>
            <Tooltip title="Reset mật khẩu">
              <Button type="text" icon={<KeyOutlined />} onClick={() => setResetPasswordUser(user)} />
            </Tooltip>
            <Popconfirm
              cancelText="Hủy"
              description={user.active ? 'Nhân viên sẽ không thể đăng nhập sau khi bị khóa.' : 'Nhân viên có thể đăng nhập lại sau khi mở tài khoản.'}
              disabled={isCurrentAccount}
              okButtonProps={{ danger: user.active }}
              okText={user.active ? 'Khóa' : 'Mở'}
              onConfirm={() => handleChangeStatus(user)}
              title={user.active ? 'Khóa tài khoản này?' : 'Mở tài khoản này?'}
            >
              <Tooltip title={isCurrentAccount ? 'Không thể tự khóa tài khoản đang dùng' : user.active ? 'Khóa tài khoản' : 'Mở tài khoản'}>
                <span>
                  <Button
                    danger={user.active}
                    disabled={isCurrentAccount}
                    loading={statusChangingId === user.id}
                    type="text"
                    icon={user.active ? <LockOutlined /> : <UnlockOutlined />}
                  />
                </span>
              </Tooltip>
            </Popconfirm>
          </Space>
        )
      },
    },
  ].filter(Boolean)

  return (
    <>
      {messageContext}
      <section className="page-heading">
        <div>
          <span className="eyebrow">{isAdmin ? 'Quản trị hệ thống' : 'Chi nhánh của tôi'}</span>
          <h1>{isAdmin ? 'Quản lý nhân viên' : 'Thông tin nhân viên'}</h1>
          <p>{isAdmin
            ? 'Tạo tài khoản, phân công chi nhánh, vị trí và cấu hình giới hạn giờ làm.'
            : 'Theo dõi hồ sơ và quy tắc làm việc của nhân viên thuộc chi nhánh bạn quản lý.'}</p>
        </div>
        {isAdmin && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateDrawer}
            disabled={referencesLoading || Boolean(referenceError)}
          >
            Thêm nhân viên
          </Button>
        )}
      </section>

      {referenceError && (
        <Alert
          action={<Button size="small" onClick={handleRetryReferences}>Thử lại</Button>}
          message={referenceError}
          showIcon
          type="error"
        />
      )}

      <section className="management-card filter-panel">
        <Input.Search
          allowClear
          className="employee-search"
          enterButton="Tìm kiếm"
          onChange={(event) => setSearchText(event.target.value)}
          onSearch={handleSearch}
          placeholder="Mã nhân viên, tên đăng nhập, họ tên hoặc email"
          value={searchText}
        />
        {isAdmin && (
          <Select
            allowClear
            loading={referencesLoading}
            onChange={(value) => applyFilter('locationId', value)}
            options={references.locations.map((location) => ({
              label: `${location.code} — ${location.name}`,
              value: location.id,
            }))}
            placeholder="Tất cả chi nhánh"
            showSearch
            optionFilterProp="label"
            value={filters.locationId}
          />
        )}
        <Select
          allowClear
          loading={referencesLoading}
          onChange={(value) => applyFilter('positionId', value)}
          options={references.positions.map((position) => ({
            label: `${position.code} — ${position.name}`,
            value: position.id,
          }))}
          placeholder="Tất cả vị trí"
          showSearch
          optionFilterProp="label"
          value={filters.positionId}
        />
        <Select
          allowClear
          onChange={(value) => applyFilter('active', value)}
          options={[
            { label: 'Đang hoạt động', value: true },
            { label: 'Đã khóa', value: false },
          ]}
          placeholder="Tất cả trạng thái"
          value={filters.active}
        />
        <Button icon={<ReloadOutlined />} onClick={handleResetFilters}>Đặt lại</Button>
      </section>

      <section className="management-card employee-table-card">
        <div className="table-heading">
          <div>
            <strong>Danh sách nhân viên</strong>
            <span>{pageData.totalElements} {isAdmin ? 'tài khoản trong hệ thống' : 'nhân viên thuộc chi nhánh'}</span>
          </div>
          <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading}>
            Làm mới
          </Button>
        </div>
        <Table
          columns={columns}
          dataSource={pageData.content}
          loading={loading}
          pagination={{
            current: pageData.page + 1,
            pageSize: pageData.size,
            total: pageData.totalElements,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (total) => `Tổng ${total} nhân viên`,
            onChange: handlePageChange,
          }}
          rowKey="id"
          scroll={{ x: 1050 }}
        />
      </section>

      {isAdmin && (
        <>
          <UserFormDrawer
            open={drawerOpen}
            user={editingUser}
            references={references}
            usernameLocked={editingUser?.id === currentUser.id}
            onClose={() => setDrawerOpen(false)}
            onSubmit={handleSaveUser}
          />

          <ResetPasswordModal
            open={Boolean(resetPasswordUser)}
            user={resetPasswordUser}
            onClose={() => setResetPasswordUser(null)}
            onSubmit={handleResetPassword}
          />
        </>
      )}
      {!isAdmin && (
        <EmployeeWorkProfileDrawer
          open={drawerOpen}
          user={editingUser}
          positions={references.positions}
          onClose={() => setDrawerOpen(false)}
          onSubmit={handleSaveUser}
        />
      )}
    </>
  )
}
