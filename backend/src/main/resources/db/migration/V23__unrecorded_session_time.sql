ALTER TABLE daily_career.assigned_session_detail
    DROP CONSTRAINT assigned_session_detail_time_source_check;

ALTER TABLE daily_career.assigned_session_detail
    ADD CONSTRAINT assigned_session_detail_time_source_check
    CHECK (time_source IN ('NOT_RECORDED', 'SELF_REPORTED', 'ESTIMATED', 'MIXED'));

UPDATE daily_career.assigned_session_detail
SET time_source = 'NOT_RECORDED'
WHERE actual_minutes = 0;

ALTER TABLE daily_career.assigned_session_detail
    ALTER COLUMN time_source SET DEFAULT 'NOT_RECORDED';

COMMENT ON COLUMN daily_career.assigned_session_detail.time_source IS
    'NOT_RECORDED means no actual duration was entered. Planned duration remains in estimated_minutes.';
