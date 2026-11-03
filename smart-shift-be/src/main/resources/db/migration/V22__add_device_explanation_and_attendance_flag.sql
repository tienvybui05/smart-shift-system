ALTER TABLE employee_devices
    ADD COLUMN device_explanation TEXT;

ALTER TABLE attendances
    ADD COLUMN is_unverified_device BOOLEAN NOT NULL DEFAULT FALSE;
