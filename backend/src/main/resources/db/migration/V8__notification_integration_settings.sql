ALTER TABLE daily_career.user_setting
    ADD COLUMN notification_settings jsonb NOT NULL DEFAULT '{"studyReminder":{"enabled":true,"time":"19:30"},"testReminder":{"enabled":true,"minutesBefore":30},"streakReminder":{"enabled":true,"time":"21:00"},"weeklyReport":{"enabled":true,"dayOfWeek":"SUNDAY","time":"20:00"},"pushEnabled":false}'::jsonb,
    ADD COLUMN notification_revision bigint NOT NULL DEFAULT 0;

ALTER TABLE daily_career.user_setting
    ADD CONSTRAINT ck_user_setting_notification_revision CHECK (notification_revision >= 0);

ALTER TABLE daily_career.push_subscription
    ADD COLUMN device_id uuid;

CREATE UNIQUE INDEX uq_push_subscription_device
    ON daily_career.push_subscription (user_id, device_id)
    WHERE device_id IS NOT NULL AND status = 'ACTIVE';

COMMENT ON COLUMN daily_career.user_setting.notification_settings IS 'NOTI-004/005 aggregate settings snapshot. Secrets are prohibited.';
COMMENT ON COLUMN daily_career.user_setting.notification_revision IS 'Notification settings aggregate optimistic-lock revision.';
COMMENT ON COLUMN daily_career.push_subscription.device_id IS 'Browser installation UUID from PUSH-002.';
