CREATE TABLE employee_devices (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    device_fingerprint VARCHAR(255) NOT NULL,
    device_label     VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    registered_at    TIMESTAMPTZ  NOT NULL,
    approved_by      BIGINT       REFERENCES users (id),
    approved_at      TIMESTAMPTZ,
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_employee_device_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED'))
);

CREATE UNIQUE INDEX idx_employee_devices_user_fingerprint
    ON employee_devices (user_id, device_fingerprint);

CREATE INDEX idx_employee_devices_user_id
    ON employee_devices (user_id);

CREATE INDEX idx_employee_devices_status
    ON employee_devices (status);
