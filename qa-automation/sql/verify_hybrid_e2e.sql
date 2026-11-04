\set ON_ERROR_STOP on

-- Independent, read-only checks of persisted assignments in the isolated fixture.
WITH active AS (
    SELECT a.id, a.user_id, a.position_id, a.assignment_source, a.work_shift_id,
           w.start_at, w.end_at, w.break_minutes, l.id AS location_id, l.timezone,
           u.position_id AS user_position, u.location_id AS user_location,
           u.is_active, u.max_hours_per_day, u.max_hours_per_week,
           u.min_rest_hours, u.max_consecutive_days,
           (w.start_at AT TIME ZONE l.timezone)::date AS work_date,
           round((extract(epoch FROM w.end_at - w.start_at) / 60 - w.break_minutes) / 60, 2) AS hours
    FROM shift_assignments a
    JOIN work_shifts w ON w.id = a.work_shift_id
    JOIN schedule_periods sp ON sp.id = w.schedule_period_id
    JOIN locations l ON l.id = sp.location_id
    JOIN users u ON u.id = a.user_id
    WHERE l.code LIKE 'E2E_%' AND a.status IN ('ASSIGNED', 'CONFIRMED')
      AND w.status <> 'CANCELLED'
), daily AS (
    SELECT user_id, work_date, sum(hours) AS hours, max(max_hours_per_day) AS allowed
    FROM active GROUP BY user_id, work_date
), weekly AS (
    SELECT user_id, date_trunc('week', work_date) AS week,
           sum(hours) AS hours, max(max_hours_per_week) AS allowed
    FROM active GROUP BY user_id, date_trunc('week', work_date)
), worked_days AS (
    SELECT DISTINCT user_id, work_date, max_consecutive_days FROM active
), runs AS (
    SELECT *, work_date - row_number() OVER (PARTITION BY user_id ORDER BY work_date)::int AS island
    FROM worked_days
), consecutive AS (
    SELECT user_id, island, count(*) AS days, max(max_consecutive_days) AS allowed
    FROM runs GROUP BY user_id, island
), violations AS (
    SELECT 'wrong_position_or_location_or_inactive' AS check_name, count(*) AS violations
    FROM active WHERE position_id <> user_position OR location_id <> user_location OR NOT is_active
    UNION ALL
    SELECT 'overlap', count(*) FROM active a JOIN active b
      ON a.user_id = b.user_id AND a.id < b.id
      AND a.start_at < b.end_at AND b.start_at < a.end_at
    UNION ALL
    SELECT 'minimum_rest', count(*) FROM active a JOIN active b
      ON a.user_id = b.user_id AND a.id <> b.id AND a.end_at <= b.start_at
      AND extract(epoch FROM b.start_at - a.end_at) < a.min_rest_hours * 3600
    UNION ALL
    SELECT 'daily_hours', count(*) FROM daily WHERE hours > allowed
    UNION ALL
    SELECT 'weekly_hours', count(*) FROM weekly WHERE hours > allowed
    UNION ALL
    SELECT 'consecutive_days', count(*) FROM consecutive WHERE days > allowed
    UNION ALL
    SELECT 'approved_time_off', count(*) FROM active a JOIN time_off_requests r
      ON r.user_id = a.user_id AND r.status = 'APPROVED'
      AND a.start_at < r.end_at AND a.end_at > r.start_at
    UNION ALL
    SELECT 'availability_coverage', count(*) FROM active a WHERE NOT COALESCE((
      SELECT range_agg(tstzrange(
        (v.available_date + v.start_time) AT TIME ZONE a.timezone,
        (CASE WHEN v.end_time = TIME '23:59' THEN (v.available_date + 1)::timestamp
              ELSE v.available_date + v.end_time END) AT TIME ZONE a.timezone, '[)'))
          @> tstzrange(a.start_at, a.end_at, '[)')
      FROM employee_availabilities v WHERE v.user_id = a.user_id
        AND v.availability_type IN ('AVAILABLE', 'PREFERRED')
    ), false)
    UNION ALL
    SELECT 'unavailable_overlap', count(*) FROM active a JOIN employee_availabilities v
      ON v.user_id = a.user_id AND v.availability_type = 'UNAVAILABLE'
      AND (v.available_date + v.start_time) AT TIME ZONE a.timezone < a.end_at
      AND (CASE WHEN v.end_time = TIME '23:59' THEN (v.available_date + 1)::timestamp
                ELSE v.available_date + v.end_time END) AT TIME ZONE a.timezone > a.start_at
    UNION ALL
    SELECT 'duplicate_employee_shift', count(*) FROM (
      SELECT user_id, work_shift_id FROM active GROUP BY user_id, work_shift_id HAVING count(*) > 1
    ) duplicates
    UNION ALL
    SELECT 'requirement_maximum', count(*) FROM (
      SELECT a.work_shift_id, a.position_id FROM active a
      JOIN shift_requirements r ON r.work_shift_id = a.work_shift_id AND r.position_id = a.position_id
      GROUP BY a.work_shift_id, a.position_id, r.max_employees HAVING count(*) > r.max_employees
    ) excess
)
SELECT jsonb_pretty(jsonb_object_agg(check_name, violations)) AS independent_db_checks,
       1 / CASE WHEN sum(violations) = 0 THEN 1 ELSE 0 END AS all_checks_passed
FROM violations;

SELECT l.name, sp.name AS period, sp.status,
       count(DISTINCT w.id) AS shifts, count(DISTINCT a.id) AS assignments
FROM locations l
JOIN schedule_periods sp ON sp.location_id = l.id
LEFT JOIN work_shifts w ON w.schedule_period_id = sp.id
LEFT JOIN shift_assignments a ON a.work_shift_id = w.id AND a.status IN ('ASSIGNED', 'CONFIRMED')
WHERE l.code LIKE 'E2E_%'
GROUP BY l.name, sp.id, sp.name, sp.status ORDER BY sp.id;
