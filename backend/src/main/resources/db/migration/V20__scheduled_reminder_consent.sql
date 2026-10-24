ALTER TABLE daily_career.user_setting
    ADD COLUMN scheduled_reminders_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN scheduled_reminders_enabled_at timestamptz;

ALTER TABLE daily_career.user_setting
    ADD CONSTRAINT ck_scheduled_reminder_consent
        CHECK (scheduled_reminders_enabled = (scheduled_reminders_enabled_at IS NOT NULL));

COMMENT ON COLUMN daily_career.user_setting.scheduled_reminders_enabled IS 'Explicit opt-in for newly generated scheduled reminders. Existing saved preferences do not grant consent.';
COMMENT ON COLUMN daily_career.user_setting.scheduled_reminders_enabled_at IS 'Consent time; reminders scheduled before this instant are not generated.';
