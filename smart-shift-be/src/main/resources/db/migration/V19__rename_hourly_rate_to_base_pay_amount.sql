ALTER TABLE users
    RENAME COLUMN hourly_rate TO base_pay_amount;

ALTER TABLE users
    RENAME CONSTRAINT chk_users_hourly_rate TO chk_users_base_pay_amount;

ALTER TABLE payroll_records
    RENAME COLUMN hourly_rate TO base_pay_amount;
