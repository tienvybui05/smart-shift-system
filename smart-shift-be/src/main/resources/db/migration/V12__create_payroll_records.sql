ALTER TABLE users
    ADD COLUMN hourly_rate NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN salary_coefficient NUMERIC(5, 2) NOT NULL DEFAULT 1.00;

ALTER TABLE users
    ADD CONSTRAINT chk_users_hourly_rate
        CHECK (hourly_rate >= 0),
    ADD CONSTRAINT chk_users_salary_coefficient
        CHECK (salary_coefficient > 0 AND salary_coefficient <= 10);

CREATE TABLE payroll_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    worked_minutes INTEGER NOT NULL DEFAULT 0,
    hourly_rate NUMERIC(12, 2) NOT NULL,
    salary_coefficient NUMERIC(5, 2) NOT NULL,
    base_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bonus_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bonus_note VARCHAR(500),
    total_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    calculated_by BIGINT NOT NULL REFERENCES users(id),
    confirmed_by BIGINT REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payroll_user_period
        UNIQUE (user_id, period_start, period_end),
    CONSTRAINT chk_payroll_period
        CHECK (period_start <= period_end),
    CONSTRAINT chk_payroll_worked_minutes
        CHECK (worked_minutes >= 0),
    CONSTRAINT chk_payroll_money
        CHECK (
            hourly_rate >= 0
            AND base_amount >= 0
            AND bonus_amount >= 0
            AND total_amount >= 0
        ),
    CONSTRAINT chk_payroll_coefficient
        CHECK (salary_coefficient > 0 AND salary_coefficient <= 10),
    CONSTRAINT chk_payroll_status
        CHECK (status IN ('DRAFT', 'CONFIRMED')),
    CONSTRAINT chk_payroll_confirmation
        CHECK (
            (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
            OR (status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL)
        )
);

CREATE INDEX idx_payroll_location_period
    ON payroll_records (location_id, period_start, period_end);

CREATE INDEX idx_payroll_user_period
    ON payroll_records (user_id, period_end DESC);
