-- Additive authentication support; V1-V3 and existing users remain unchanged.
ALTER TABLE daily_career.app_user ADD COLUMN profile_image_url varchar(2048);
ALTER TABLE daily_career.app_user ADD CONSTRAINT ck_app_user_profile_https
    CHECK (profile_image_url IS NULL OR profile_image_url ~ '^https://[^[:space:]]+$');
COMMENT ON COLUMN daily_career.app_user.profile_image_url IS '검증된 OIDC 공급자의 HTTPS 프로필 이미지; 임의 URL 프록시 금지';

CREATE TABLE daily_career.oidc_login_state (
    state_hash varchar(64) PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '10 minutes'),
    CONSTRAINT ck_oidc_state_hash CHECK (state_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_oidc_state_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_oidc_state_expiry ON daily_career.oidc_login_state(expires_at);
COMMENT ON TABLE daily_career.oidc_login_state IS '세션 결합 OIDC state 일회성 소비; 원문 state 및 공급자 토큰 미저장';
COMMENT ON COLUMN daily_career.oidc_login_state.state_hash IS 'state SHA-256; 동시 콜백도 원자적 DELETE로 한 번만 소비';
COMMENT ON COLUMN daily_career.oidc_login_state.created_at IS '로그인 시작 시각';
COMMENT ON COLUMN daily_career.oidc_login_state.expires_at IS '10분 유효시간';
