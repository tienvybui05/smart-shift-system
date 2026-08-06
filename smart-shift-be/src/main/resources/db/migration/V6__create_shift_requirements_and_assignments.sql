CREATE TABLE shift_requirements (
    id BIGSERIAL PRIMARY KEY,
    work_shift_id BIGINT NOT NULL REFERENCES work_shifts(id) ON DELETE CASCADE,
    position_id BIGINT NOT NULL REFERENCES positions(id),
    min_employees SMALLINT NOT NULL,
    max_employees SMALLINT NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 1,

    CONSTRAINT uk_shift_requirement_position
        UNIQUE (work_shift_id, position_id),
    CONSTRAINT chk_shift_requirement_counts
        CHECK (
            min_employees >= 0
            AND max_employees >= min_employees
            AND max_employees > 0
        ),
    CONSTRAINT chk_shift_requirement_priority
        CHECK (priority > 0)
);

CREATE TABLE shift_assignments (
    id BIGSERIAL PRIMARY KEY,
    work_shift_id BIGINT NOT NULL REFERENCES work_shifts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    position_id BIGINT NOT NULL REFERENCES positions(id),
    assignment_source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    score NUMERIC(10, 2),
    assigned_by BIGINT REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT,

    CONSTRAINT uk_shift_assignment_user
        UNIQUE (work_shift_id, user_id),
    CONSTRAINT chk_shift_assignment_source
        CHECK (assignment_source IN ('MANUAL', 'AUTO', 'SWAP')),
    CONSTRAINT chk_shift_assignment_status
        CHECK (status IN ('ASSIGNED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_shift_assignment_actor
        CHECK (assignment_source = 'AUTO' OR assigned_by IS NOT NULL)
);

