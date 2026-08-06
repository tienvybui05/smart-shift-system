CREATE TABLE shift_templates (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES locations(id),
    name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes SMALLINT NOT NULL DEFAULT 0,
    color_code VARCHAR(7),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_shift_templates_location_name UNIQUE (location_id, name),
    CONSTRAINT chk_shift_templates_break
        CHECK (break_minutes >= 0),
    CONSTRAINT chk_shift_templates_color
        CHECK (color_code IS NULL OR color_code ~ '^#[0-9A-Fa-f]{6}$')
);

