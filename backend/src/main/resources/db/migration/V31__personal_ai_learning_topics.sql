CREATE TABLE daily_career.ai_learning_topic (
    content_version_id bigint PRIMARY KEY REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    ai_job_id bigint NOT NULL UNIQUE,
    base_content_version_id bigint REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    stage varchar(8) NOT NULL CHECK (stage IN ('기초','실무','심화')),
    target_minutes smallint NOT NULL CHECK (target_minutes IN (30,60,90,120)),
    review_origin varchar(40) NOT NULL DEFAULT 'AI_GENERATED_AUTOMATIC_CHECKS',
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (ai_job_id,user_id) REFERENCES daily_career.ai_job(id,user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_ai_learning_topic_user ON daily_career.ai_learning_topic(user_id,created_at DESC);
COMMENT ON TABLE daily_career.ai_learning_topic IS '개인 AI 원문: 공식 웹 출처 및 구조 자동 검사. 사람 검수 완료가 아님. 기존 원문/진도 불변.';
