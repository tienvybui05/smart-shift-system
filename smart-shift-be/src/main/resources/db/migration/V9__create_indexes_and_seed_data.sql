CREATE INDEX idx_users_location_active
    ON users (location_id, is_active);

CREATE INDEX idx_users_role
    ON users (role_id);

CREATE INDEX idx_users_position
    ON users (position_id);

CREATE INDEX idx_shift_templates_location_active
    ON shift_templates (location_id, is_active);

CREATE INDEX idx_availability_user_date
    ON employee_availabilities (user_id, available_date);

CREATE INDEX idx_time_off_user_status_time
    ON time_off_requests (user_id, status, start_at, end_at);

CREATE INDEX idx_schedule_period_location_dates
    ON schedule_periods (location_id, start_date, end_date);

CREATE INDEX idx_work_shift_period_time
    ON work_shifts (schedule_period_id, start_at, end_at);

CREATE INDEX idx_shift_requirement_shift
    ON shift_requirements (work_shift_id);

CREATE INDEX idx_assignment_user_status
    ON shift_assignments (user_id, status);

CREATE INDEX idx_assignment_shift
    ON shift_assignments (work_shift_id);

CREATE INDEX idx_shift_swap_status
    ON shift_swap_requests (status, created_at);

CREATE INDEX idx_notification_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

INSERT INTO roles (name, description)
VALUES
    ('ROLE_ADMIN', 'Quản trị toàn bộ hệ thống'),
    ('ROLE_MANAGER', 'Quản lý chi nhánh và lịch làm việc'),
    ('ROLE_EMPLOYEE', 'Nhân viên xem lịch và gửi yêu cầu')
ON CONFLICT (name) DO NOTHING;

INSERT INTO positions (code, name, description)
VALUES
    ('SHIFT_MANAGER', 'Quản lý ca', 'Điều phối hoạt động trong ca'),
    ('CASHIER', 'Thu ngân', 'Thanh toán và hỗ trợ khách hàng'),
    ('SERVICE_STAFF', 'Phục vụ', 'Phục vụ và chăm sóc khách hàng'),
    ('KITCHEN_STAFF', 'Nhân viên bếp', 'Chuẩn bị và chế biến sản phẩm'),
    ('WAREHOUSE_STAFF', 'Nhân viên kho', 'Nhập, xuất và kiểm kê hàng hóa')
ON CONFLICT (code) DO NOTHING;

INSERT INTO locations (code, name, address, timezone)
VALUES (
    'STORE_HCM_001',
    'Chi nhánh Hồ Chí Minh 01',
    'Thành phố Hồ Chí Minh',
    'Asia/Ho_Chi_Minh'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO shift_templates (
    location_id,
    name,
    start_time,
    end_time,
    break_minutes,
    color_code
)
SELECT
    location.id,
    seed.name,
    seed.start_time,
    seed.end_time,
    seed.break_minutes,
    seed.color_code
FROM locations AS location
CROSS JOIN (
    VALUES
        ('Ca Sáng', TIME '06:00', TIME '14:00', 30::SMALLINT, '#3182CE'),
        ('Ca Chiều', TIME '14:00', TIME '22:00', 30::SMALLINT, '#DD6B20'),
        ('Ca Tối', TIME '22:00', TIME '06:00', 30::SMALLINT, '#6B46C1')
) AS seed(name, start_time, end_time, break_minutes, color_code)
WHERE location.code = 'STORE_HCM_001'
ON CONFLICT (location_id, name) DO NOTHING;

