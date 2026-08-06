CREATE TABLE attendances (
    id BIGSERIAL PRIMARY KEY,
    shift_assignment_id BIGINT NOT NULL UNIQUE REFERENCES shift_assignments(id),
    check_in_at TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    break_minutes SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    note TEXT,
    approved_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_attendance_time
        CHECK (check_out_at IS NULL OR (check_in_at IS NOT NULL AND check_out_at > check_in_at)),
    CONSTRAINT chk_attendance_break
        CHECK (break_minutes >= 0),
    CONSTRAINT chk_attendance_status
        CHECK (status IN ('PRESENT', 'LATE', 'EARLY_LEAVE', 'ABSENT'))
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_notification_reference
        CHECK (
            (reference_type IS NULL AND reference_id IS NULL)
            OR (reference_type IS NOT NULL AND reference_id IS NOT NULL)
        )
);

