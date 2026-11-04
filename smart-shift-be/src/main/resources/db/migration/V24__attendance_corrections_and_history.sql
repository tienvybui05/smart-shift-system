CREATE TABLE attendance_corrections (
    id BIGSERIAL PRIMARY KEY,
    shift_assignment_id BIGINT NOT NULL REFERENCES shift_assignments(id),
    requester_id BIGINT NOT NULL REFERENCES users(id),
    proposed_check_in_at TIMESTAMPTZ NOT NULL,
    proposed_check_out_at TIMESTAMPTZ NOT NULL,
    proposed_break_minutes SMALLINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    baseline_attendance_id BIGINT REFERENCES attendances(id),
    baseline_updated_at TIMESTAMPTZ,
    before_data TEXT NOT NULL,
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_correction_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT chk_correction_times CHECK (proposed_check_out_at > proposed_check_in_at),
    CONSTRAINT chk_correction_break CHECK (proposed_break_minutes >= 0 AND proposed_break_minutes <= 1440),
    CONSTRAINT chk_correction_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT chk_correction_review CHECK (
        (status IN ('PENDING','CANCELLED') AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND reviewed_by <> requester_id))
);
CREATE UNIQUE INDEX uq_attendance_correction_pending ON attendance_corrections(shift_assignment_id) WHERE status='PENDING';
CREATE INDEX idx_attendance_correction_requester ON attendance_corrections(requester_id, created_at DESC);

CREATE TABLE attendance_history (
    id BIGSERIAL PRIMARY KEY,
    shift_assignment_id BIGINT NOT NULL REFERENCES shift_assignments(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_data TEXT NOT NULL,
    after_data TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_attendance_history_assignment ON attendance_history(shift_assignment_id, id DESC);
ALTER TABLE payroll_records ADD COLUMN source_changed BOOLEAN NOT NULL DEFAULT FALSE;

