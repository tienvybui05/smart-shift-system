-- Optimistic version used by calendar placement to reject stale drag/drop requests.
ALTER TABLE shift_assignments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
