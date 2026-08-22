ALTER TABLE shift_assignments
    DROP CONSTRAINT chk_shift_assignment_source;

ALTER TABLE shift_assignments
    ADD CONSTRAINT chk_shift_assignment_source
        CHECK (assignment_source IN ('MANUAL', 'AUTO', 'SWAP', 'CLAIM'));

CREATE TABLE open_shift_claims (
    id BIGSERIAL PRIMARY KEY,
    work_shift_id BIGINT NOT NULL REFERENCES work_shifts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    reviewer_note TEXT,
    assignment_id BIGINT REFERENCES shift_assignments(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_open_shift_claim_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_open_shift_claim_review
        CHECK (
            (
                status = 'APPROVED'
                AND reviewed_by IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND assignment_id IS NOT NULL
            )
            OR (
                status = 'REJECTED'
                AND reviewed_by IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND assignment_id IS NULL
            )
            OR (
                status IN ('PENDING', 'CANCELLED')
                AND assignment_id IS NULL
            )
        )
);

CREATE UNIQUE INDEX uk_open_shift_claim_pending
    ON open_shift_claims (work_shift_id, user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_open_shift_claim_user_status
    ON open_shift_claims (user_id, status, created_at DESC);

CREATE INDEX idx_open_shift_claim_shift_status
    ON open_shift_claims (work_shift_id, status, created_at);
