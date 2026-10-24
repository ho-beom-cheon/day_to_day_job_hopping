UPDATE daily_career.user_setting
SET notification_settings = jsonb_set(
        notification_settings #- '{testReminder,minutesBefore}',
        '{testReminder,beforeMinutes}',
        COALESCE(
            notification_settings #> '{testReminder,beforeMinutes}',
            notification_settings #> '{testReminder,minutesBefore}',
            '30'::jsonb
        ),
        true
    )
WHERE notification_settings #> '{testReminder,minutesBefore}' IS NOT NULL;

ALTER TABLE daily_career.user_setting
    ALTER COLUMN notification_settings SET DEFAULT '{"studyReminder":{"enabled":true,"time":"19:30"},"testReminder":{"enabled":true,"beforeMinutes":30},"streakReminder":{"enabled":true,"time":"21:00"},"weeklyReport":{"enabled":true,"dayOfWeek":"SUNDAY","time":"20:00"},"pushEnabled":false}'::jsonb;

COMMENT ON COLUMN daily_career.user_setting.notification_settings IS 'NOTI-004/005 aggregate settings snapshot using TestReminder.beforeMinutes. Secrets are prohibited.';
