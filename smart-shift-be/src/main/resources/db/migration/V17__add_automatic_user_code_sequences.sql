CREATE SEQUENCE admin_code_sequence START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE employee_code_sequence START WITH 1 INCREMENT BY 1;

SELECT setval(
    'admin_code_sequence',
    COALESCE((
        SELECT MAX(SUBSTRING(employee_code FROM 3)::BIGINT)
        FROM users
        WHERE employee_code ~ '^AD[0-9]{6,}$'
    ), 0) + 1,
    FALSE
);

SELECT setval(
    'employee_code_sequence',
    COALESCE((
        SELECT MAX(SUBSTRING(employee_code FROM 3)::BIGINT)
        FROM users
        WHERE employee_code ~ '^NV[0-9]{6,}$'
    ), 0) + 1,
    FALSE
);
