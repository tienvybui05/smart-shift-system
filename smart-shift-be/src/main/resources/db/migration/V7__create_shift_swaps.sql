CREATE TABLE shift_swap_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_assignment_id BIGINT NOT NULL REFERENCES shift_assignments(id),
    target_user_id BIGINT REFERENCES users(id),
    target_assignment_id BIGINT REFERENCES shift_assignments(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_shift_swap_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_shift_swap_distinct_assignments
        CHECK (target_assignment_id IS NULL OR target_assignment_id <> requester_assignment_id),
    CONSTRAINT chk_shift_swap_approval
        CHECK (
            (status IN ('APPROVED', 'REJECTED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
            OR status IN ('PENDING', 'ACCEPTED', 'CANCELLED')
        )
);

