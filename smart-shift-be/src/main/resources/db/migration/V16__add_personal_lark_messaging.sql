ALTER TABLE users
    ADD COLUMN lark_open_id VARCHAR(128),
    ADD COLUMN lark_synced_at TIMESTAMPTZ,
    ADD COLUMN lark_sync_error VARCHAR(500);

ALTER TABLE users
    ADD CONSTRAINT uq_users_lark_open_id UNIQUE (lark_open_id);

ALTER TABLE lark_delivery_logs
    ADD COLUMN channel VARCHAR(30) NOT NULL DEFAULT 'GROUP_WEBHOOK',
    ADD COLUMN recipient_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN recipient_name VARCHAR(100),
    ADD COLUMN recipient_open_id VARCHAR(128);

ALTER TABLE lark_delivery_logs
    ADD CONSTRAINT chk_lark_delivery_channel CHECK (
        channel IN ('GROUP_WEBHOOK', 'PERSONAL_APP')
    );

CREATE INDEX idx_users_lark_link
    ON users (is_active, lark_open_id);

CREATE INDEX idx_lark_delivery_recipient
    ON lark_delivery_logs (recipient_user_id, created_at DESC);
