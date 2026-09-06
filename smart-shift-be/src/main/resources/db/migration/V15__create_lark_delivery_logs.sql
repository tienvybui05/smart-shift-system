CREATE TABLE lark_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(128) NOT NULL UNIQUE,
    notification_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    http_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_lark_delivery_status CHECK (
        status IN ('PENDING', 'SENT', 'FAILED')
    ),
    CONSTRAINT chk_lark_delivery_reference CHECK (
        (reference_type IS NULL AND reference_id IS NULL)
        OR (reference_type IS NOT NULL AND reference_id IS NOT NULL)
    )
);

CREATE INDEX idx_lark_delivery_status_due
    ON lark_delivery_logs (status, next_attempt_at, created_at);

CREATE INDEX idx_lark_delivery_created
    ON lark_delivery_logs (created_at DESC);
