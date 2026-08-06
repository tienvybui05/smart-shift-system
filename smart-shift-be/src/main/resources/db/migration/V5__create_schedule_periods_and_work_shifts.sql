CREATE TABLE schedule_periods (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES locations(id),
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL REFERENCES users(id),
    published_by BIGINT REFERENCES users(id),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_schedule_period_dates
        CHECK (end_date >= start_date),
    CONSTRAINT chk_schedule_period_status
        CHECK (status IN ('DRAFT', 'GENERATING', 'PUBLISHED', 'LOCKED')),
    CONSTRAINT chk_schedule_period_publication
        CHECK (
            (status IN ('PUBLISHED', 'LOCKED') AND published_by IS NOT NULL AND published_at IS NOT NULL)
            OR status IN ('DRAFT', 'GENERATING')
        ),
    CONSTRAINT uk_schedule_period_location_dates
        UNIQUE (location_id, start_date, end_date)
);

CREATE TABLE work_shifts (
    id BIGSERIAL PRIMARY KEY,
    schedule_period_id BIGINT NOT NULL REFERENCES schedule_periods(id) ON DELETE CASCADE,
    shift_template_id BIGINT REFERENCES shift_templates(id) ON DELETE SET NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    break_minutes SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_work_shift_period
        CHECK (end_at > start_at),
    CONSTRAINT chk_work_shift_break
        CHECK (break_minutes >= 0),
    CONSTRAINT chk_work_shift_status
        CHECK (status IN ('OPEN', 'FILLED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT uk_work_shift_period_start
        UNIQUE (schedule_period_id, start_at)
);

