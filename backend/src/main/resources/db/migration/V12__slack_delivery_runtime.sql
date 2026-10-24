CREATE UNIQUE INDEX uq_notification_delivery_push_target
    ON daily_career.notification_delivery (notification_id, push_subscription_id)
    WHERE channel = 'WEB_PUSH';

CREATE UNIQUE INDEX uq_notification_delivery_slack_target
    ON daily_career.notification_delivery (notification_id, integration_connection_id)
    WHERE channel = 'SLACK';

COMMENT ON INDEX daily_career.uq_notification_delivery_push_target IS '동일 알림과 Push 구독의 중복 전달 방지';
COMMENT ON INDEX daily_career.uq_notification_delivery_slack_target IS '동일 알림과 Slack 연결의 중복 전달 방지';
