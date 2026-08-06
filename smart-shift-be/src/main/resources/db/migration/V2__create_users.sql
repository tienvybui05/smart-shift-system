CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone_number VARCHAR(20),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    location_id BIGINT NOT NULL REFERENCES locations(id),
    position_id BIGINT NOT NULL REFERENCES positions(id),
    employment_type VARCHAR(20) NOT NULL,
    hire_date DATE NOT NULL,
    min_hours_per_week NUMERIC(5, 2) NOT NULL DEFAULT 0,
    max_hours_per_week NUMERIC(5, 2) NOT NULL,
    max_hours_per_day NUMERIC(5, 2) NOT NULL,
    min_rest_hours NUMERIC(5, 2) NOT NULL,
    max_consecutive_days SMALLINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_employment_type
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'SEASONAL')),
    CONSTRAINT chk_users_weekly_hours
        CHECK (
            min_hours_per_week >= 0
            AND max_hours_per_week > 0
            AND min_hours_per_week <= max_hours_per_week
        ),
    CONSTRAINT chk_users_daily_hours
        CHECK (max_hours_per_day > 0 AND max_hours_per_day <= 24),
    CONSTRAINT chk_users_rest_hours
        CHECK (min_rest_hours >= 0 AND min_rest_hours <= 24),
    CONSTRAINT chk_users_consecutive_days
        CHECK (max_consecutive_days > 0)
);

