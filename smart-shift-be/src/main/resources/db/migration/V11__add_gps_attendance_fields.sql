ALTER TABLE locations
    ADD COLUMN latitude NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD COLUMN attendance_radius_meters INTEGER NOT NULL DEFAULT 200;

ALTER TABLE locations
    ADD CONSTRAINT chk_location_coordinates
        CHECK (
            (latitude IS NULL AND longitude IS NULL)
            OR (
                latitude IS NOT NULL
                AND longitude IS NOT NULL
                AND latitude BETWEEN -90 AND 90
                AND longitude BETWEEN -180 AND 180
            )
        ),
    ADD CONSTRAINT chk_location_attendance_radius
        CHECK (attendance_radius_meters BETWEEN 10 AND 5000);

UPDATE locations
SET
    latitude = 10.776889,
    longitude = 106.700806
WHERE code = 'STORE_HCM_001'
  AND latitude IS NULL
  AND longitude IS NULL;

ALTER TABLE attendances
    ADD COLUMN check_in_latitude NUMERIC(9, 6),
    ADD COLUMN check_in_longitude NUMERIC(9, 6),
    ADD COLUMN check_in_accuracy_meters NUMERIC(8, 2),
    ADD COLUMN check_in_distance_meters NUMERIC(10, 2),
    ADD COLUMN check_out_latitude NUMERIC(9, 6),
    ADD COLUMN check_out_longitude NUMERIC(9, 6),
    ADD COLUMN check_out_accuracy_meters NUMERIC(8, 2),
    ADD COLUMN check_out_distance_meters NUMERIC(10, 2),
    ADD COLUMN late_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN early_leave_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN overtime_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN approved_at TIMESTAMPTZ;

ALTER TABLE attendances
    ADD CONSTRAINT chk_attendance_check_in_gps
        CHECK (
            (check_in_latitude IS NULL
                AND check_in_longitude IS NULL
                AND check_in_accuracy_meters IS NULL
                AND check_in_distance_meters IS NULL)
            OR (
                check_in_latitude IS NOT NULL
                AND check_in_longitude IS NOT NULL
                AND check_in_accuracy_meters IS NOT NULL
                AND check_in_distance_meters IS NOT NULL
                AND check_in_latitude BETWEEN -90 AND 90
                AND check_in_longitude BETWEEN -180 AND 180
                AND check_in_accuracy_meters >= 0
                AND check_in_distance_meters >= 0
            )
        ),
    ADD CONSTRAINT chk_attendance_check_out_gps
        CHECK (
            (check_out_latitude IS NULL
                AND check_out_longitude IS NULL
                AND check_out_accuracy_meters IS NULL
                AND check_out_distance_meters IS NULL)
            OR (
                check_out_latitude IS NOT NULL
                AND check_out_longitude IS NOT NULL
                AND check_out_accuracy_meters IS NOT NULL
                AND check_out_distance_meters IS NOT NULL
                AND check_out_latitude BETWEEN -90 AND 90
                AND check_out_longitude BETWEEN -180 AND 180
                AND check_out_accuracy_meters >= 0
                AND check_out_distance_meters >= 0
            )
        ),
    ADD CONSTRAINT chk_attendance_deviation_minutes
        CHECK (
            late_minutes >= 0
            AND early_leave_minutes >= 0
            AND overtime_minutes >= 0
        );

CREATE INDEX idx_attendance_status
    ON attendances (status);
