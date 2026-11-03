-- V20: Add weekly_budget to locations for budget-constrained auto-scheduling
-- weekly_budget: optional cap on estimated weekly payroll cost (basePay * coefficient * hours)
-- NULL means no budget limit is enforced for that location

ALTER TABLE locations
    ADD COLUMN weekly_budget NUMERIC(15, 2) DEFAULT NULL;

COMMENT ON COLUMN locations.weekly_budget IS
    'Giới hạn quỹ lương ước tính mỗi tuần cho chi nhánh (NULL = không giới hạn)';
