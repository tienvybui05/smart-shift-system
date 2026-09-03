ALTER TABLE shift_swap_requests
    ADD COLUMN requester_user_id BIGINT,
    ADD COLUMN response_note VARCHAR(1000),
    ADD COLUMN responded_at TIMESTAMPTZ,
    ADD COLUMN reviewer_note VARCHAR(1000);

UPDATE shift_swap_requests request
SET requester_user_id = assignment.user_id
FROM shift_assignments assignment
WHERE assignment.id = request.requester_assignment_id;

ALTER TABLE shift_swap_requests
    ALTER COLUMN requester_user_id SET NOT NULL,
    ADD CONSTRAINT fk_shift_swap_requester_user
        FOREIGN KEY (requester_user_id) REFERENCES users(id),
    DROP CONSTRAINT chk_shift_swap_status,
    ADD CONSTRAINT chk_shift_swap_status
        CHECK (status IN (
            'PENDING', 'ACCEPTED', 'DECLINED',
            'APPROVED', 'REJECTED', 'CANCELLED'
        )),
    DROP CONSTRAINT chk_shift_swap_approval,
    ADD CONSTRAINT chk_shift_swap_approval
        CHECK (
            (status IN ('APPROVED', 'REJECTED')
                AND approved_by IS NOT NULL
                AND approved_at IS NOT NULL)
            OR (status NOT IN ('APPROVED', 'REJECTED')
                AND approved_by IS NULL
                AND approved_at IS NULL)
        ),
    ADD CONSTRAINT chk_shift_swap_target
        CHECK (target_assignment_id IS NULL OR target_user_id IS NOT NULL),
    ADD CONSTRAINT chk_shift_swap_response
        CHECK (
            status NOT IN ('ACCEPTED', 'DECLINED', 'APPROVED', 'REJECTED')
            OR (target_user_id IS NOT NULL AND responded_at IS NOT NULL)
        );

CREATE INDEX idx_shift_swap_requester_user_created
    ON shift_swap_requests (requester_user_id, created_at DESC);

CREATE INDEX idx_shift_swap_target_user_created
    ON shift_swap_requests (target_user_id, created_at DESC);

CREATE UNIQUE INDEX uk_shift_swap_active_requester_assignment
    ON shift_swap_requests (requester_assignment_id)
    WHERE status IN ('PENDING', 'ACCEPTED');

CREATE UNIQUE INDEX uk_shift_swap_active_target_assignment
    ON shift_swap_requests (target_assignment_id)
    WHERE target_assignment_id IS NOT NULL
      AND status IN ('PENDING', 'ACCEPTED');
