ALTER TABLE daily_career.assigned_session_detail
    ADD COLUMN time_source varchar(16) NOT NULL DEFAULT 'SELF_REPORTED'
    CHECK (time_source IN ('SELF_REPORTED', 'ESTIMATED', 'MIXED'));

COMMENT ON COLUMN daily_career.assigned_session_detail.time_source IS
    'SELF_REPORTED is user-entered, ESTIMATED is planned duration, and MIXED combines both.';
