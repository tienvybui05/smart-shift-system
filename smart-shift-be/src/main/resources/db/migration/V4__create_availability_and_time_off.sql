CREATE TABLE employee_availabilities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    available_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    availability_type VARCHAR(20) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_employee_availability_time
        CHECK (end_time > start_time),
    CONSTRAINT chk_employee_availability_type
        CHECK (availability_type IN ('AVAILABLE', 'UNAVAILABLE', 'PREFERRED')),
    CONSTRAINT uk_employee_availability_slot
        UNIQUE (user_id, available_date, start_time, end_time, availability_type)
);

CREATE TABLE time_off_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    leave_type VARCHAR(20) NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_time_off_period
        CHECK (end_at > start_at),
    CONSTRAINT chk_time_off_leave_type
        CHECK (leave_type IN ('ANNUAL', 'SICK', 'UNPAID', 'OTHER')),
    CONSTRAINT chk_time_off_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_time_off_approval
        CHECK (
            (status IN ('APPROVED', 'REJECTED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
            OR status IN ('PENDING', 'CANCELLED')
        )
);

