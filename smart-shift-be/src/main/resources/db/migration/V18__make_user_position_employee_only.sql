ALTER TABLE users
    ALTER COLUMN position_id DROP NOT NULL;

UPDATE users
SET position_id = NULL
WHERE role_id IN (
    SELECT id
    FROM roles
    WHERE name IN ('ROLE_ADMIN', 'ROLE_MANAGER')
);
