-- OpenAPI v1.2.1 additions to the archived DB v0.1 baseline. Issue #2.
ALTER TABLE daily_career.test DROP CONSTRAINT ck_test_02;
ALTER TABLE daily_career.test ADD CONSTRAINT ck_test_02
    CHECK (test_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'FINAL'));

CREATE TABLE daily_career.schedule_preview (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    schedule_revision bigint NOT NULL,
    action varchar(32) NOT NULL,
    request_snapshot jsonb NOT NULL,
    preview_snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '10 minutes'),
    consumed_at timestamptz,
    CONSTRAINT pk_schedule_preview PRIMARY KEY (id),
    CONSTRAINT fk_schedule_preview_user FOREIGN KEY (user_id)
        REFERENCES daily_career.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_preview_curriculum FOREIGN KEY (user_curriculum_id, user_id)
        REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_schedule_preview_revision CHECK (schedule_revision >= 0),
    CONSTRAINT ck_schedule_preview_action CHECK (action IN ('REST_ADD', 'REST_REMOVE', 'POLICY_APPLY', 'SHIFT_BACKLOG')),
    CONSTRAINT ck_schedule_preview_request CHECK (jsonb_typeof(request_snapshot) = 'object'),
    CONSTRAINT ck_schedule_preview_snapshot CHECK (jsonb_typeof(preview_snapshot) = 'object'),
    CONSTRAINT ck_schedule_preview_expiry CHECK (expires_at = created_at + interval '10 minutes'),
    CONSTRAINT ck_schedule_preview_consumed CHECK (consumed_at IS NULL OR (consumed_at >= created_at AND consumed_at < expires_at))
);
COMMENT ON TABLE daily_career.schedule_preview IS '일정 변경 미리보기; 과정 잠금 및 revision/만료 검증 후 일정 변경과 원자적으로 1회 소비';
COMMENT ON COLUMN daily_career.schedule_preview.id IS '내부 bigint 식별자; API previewId는 문자열';
COMMENT ON COLUMN daily_career.schedule_preview.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.schedule_preview.user_curriculum_id IS '변경 대상 개인 과정';
COMMENT ON COLUMN daily_career.schedule_preview.schedule_revision IS '미리보기 생성 당시 일정 revision';
COMMENT ON COLUMN daily_career.schedule_preview.action IS 'OpenAPI 일정 변경 action';
COMMENT ON COLUMN daily_career.schedule_preview.request_snapshot IS '검증된 미리보기 요청의 고정 스냅샷; 자격증명 제외';
COMMENT ON COLUMN daily_career.schedule_preview.preview_snapshot IS '계산된 이동 계획과 응답의 고정 스냅샷';
COMMENT ON COLUMN daily_career.schedule_preview.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.schedule_preview.expires_at IS '생성 후 10분 만료; 서비스는 소비 시 실제 시각을 검사';
COMMENT ON COLUMN daily_career.schedule_preview.consumed_at IS '소비 완료 시각; 서비스는 미소비 조건부 갱신으로 재사용 방지';
CREATE INDEX ix_schedule_preview_curriculum ON daily_career.schedule_preview (user_curriculum_id, user_id);
CREATE INDEX ix_schedule_preview_expiry ON daily_career.schedule_preview (expires_at);
CREATE INDEX ix_schedule_preview_user ON daily_career.schedule_preview (user_id);
