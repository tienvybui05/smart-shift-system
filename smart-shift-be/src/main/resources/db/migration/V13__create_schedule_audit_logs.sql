CREATE TABLE schedule_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES locations(id),
    schedule_period_id BIGINT NOT NULL REFERENCES schedule_periods(id),
    work_shift_id BIGINT,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    actor_name VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_data TEXT,
    after_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_schedule_audit_action CHECK (
        action IN (
            'CREATED',
            'UPDATED',
            'STATUS_CHANGED',
            'GENERATED',
            'REQUIREMENTS_CHANGED',
            'ASSIGNED',
            'UNASSIGNED',
            'AUTO_SCHEDULED',
            'PUBLISHED',
            'LOCKED'
        )
    ),
    CONSTRAINT chk_schedule_audit_target_type CHECK (
        target_type IN (
            'SCHEDULE_PERIOD',
            'WORK_SHIFT',
            'SHIFT_REQUIREMENT',
            'SHIFT_ASSIGNMENT'
        )
    )
);

CREATE INDEX idx_schedule_audit_location_created
    ON schedule_audit_logs (location_id, created_at DESC);

CREATE INDEX idx_schedule_audit_period_created
    ON schedule_audit_logs (schedule_period_id, created_at DESC);

CREATE INDEX idx_schedule_audit_work_shift
    ON schedule_audit_logs (work_shift_id, created_at DESC);
