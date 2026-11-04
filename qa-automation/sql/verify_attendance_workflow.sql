-- Read-only checks after attendance_workflow_e2e.py. Each invalid_count must be 0.
-- Run against the isolated attendance_qa database, never seed/delete application data here.
SELECT 'duplicate_pending' AS check_name, count(*) AS invalid_count
FROM (
    SELECT shift_assignment_id FROM attendance_corrections
    WHERE status = 'PENDING' GROUP BY shift_assignment_id HAVING count(*) > 1
) duplicates
UNION ALL
SELECT 'requester_not_assignment_owner', count(*)
FROM attendance_corrections c JOIN shift_assignments a ON a.id = c.shift_assignment_id
WHERE c.requester_id <> a.user_id
UNION ALL
SELECT 'self_review', count(*) FROM attendance_corrections WHERE reviewed_by = requester_id
UNION ALL
SELECT 'pending_in_confirmed_payroll', count(*)
FROM attendance_corrections c
JOIN shift_assignments a ON a.id = c.shift_assignment_id
JOIN work_shifts w ON w.id = a.work_shift_id
JOIN schedule_periods s ON s.id = w.schedule_period_id
JOIN locations l ON l.id = s.location_id
WHERE c.status = 'PENDING' AND EXISTS (
    SELECT 1 FROM payroll_records p WHERE p.user_id = c.requester_id AND p.status = 'CONFIRMED'
      AND (w.start_at AT TIME ZONE l.timezone)::date <= p.period_end
      AND ((w.end_at - interval '1 microsecond') AT TIME ZONE l.timezone)::date >= p.period_start
)
UNION ALL
SELECT 'confirmed_payroll_with_stale_source', count(*)
FROM payroll_records WHERE status = 'CONFIRMED' AND source_changed
UNION ALL
SELECT 'approved_correction_without_history', count(*)
FROM attendance_corrections c WHERE c.status = 'APPROVED' AND NOT EXISTS (
    SELECT 1 FROM attendance_history h
    WHERE h.shift_assignment_id = c.shift_assignment_id AND h.action = 'CORRECTION_APPROVED'
)
UNION ALL
SELECT 'invalid_snapshot_shape', count(*) FROM attendance_history
WHERE jsonb_typeof(before_data::jsonb) NOT IN ('object','null')
   OR jsonb_typeof(after_data::jsonb) <> 'object'
ORDER BY check_name;
