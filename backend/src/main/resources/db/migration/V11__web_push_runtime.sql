ALTER TABLE daily_career.push_subscription
    ADD COLUMN expiration_at timestamptz;

COMMENT ON COLUMN daily_career.push_subscription.expiration_at IS '브라우저 PushSubscription 만료 시각. null이면 공급자가 만료를 알릴 때까지 유효.';
