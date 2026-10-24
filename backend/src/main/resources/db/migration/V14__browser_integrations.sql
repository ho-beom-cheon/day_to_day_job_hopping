CREATE TABLE daily_career.browser_connection (
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id),
    provider varchar(16) NOT NULL CHECK (provider IN ('slack', 'openrouter')),
    revision bigint NOT NULL DEFAULT 0,
    credential_ref uuid,
    credential_ciphertext text,
    metadata jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, provider),
    UNIQUE (credential_ref),
    CHECK ((credential_ref IS NULL) = (credential_ciphertext IS NULL))
);
CREATE TABLE daily_career.browser_oauth_state (
    state_hash char(64) PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id),
    provider varchar(16) NOT NULL CHECK (provider IN ('slack', 'openrouter')),
    session_hash char(64) NOT NULL,
    connection_revision bigint NOT NULL,
    verifier_ciphertext text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE INDEX ix_browser_oauth_expiry ON daily_career.browser_oauth_state(expires_at);
ALTER TABLE daily_career.ai_job ADD COLUMN browser_credential_ref uuid;
COMMENT ON TABLE daily_career.browser_connection IS '사용자별 OAuth 자격정보 AES-GCM 암호화. metadata에는 비밀을 저장하지 않는다.';
COMMENT ON COLUMN daily_career.ai_job.browser_credential_ref IS '접수 시점의 OpenRouter 연결 참조. 재연결 이후 이전 작업에 새 키를 사용하지 않는다.';
