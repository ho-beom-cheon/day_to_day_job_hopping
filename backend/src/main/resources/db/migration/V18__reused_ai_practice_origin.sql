ALTER TABLE daily_career.ai_job
    ADD COLUMN reused_from_job_id bigint REFERENCES daily_career.ai_job(id) ON DELETE RESTRICT;

CREATE INDEX ix_ai_job_reused_from ON daily_career.ai_job(reused_from_job_id)
    WHERE reused_from_job_id IS NOT NULL;

COMMENT ON COLUMN daily_career.ai_job.reused_from_job_id IS
    '기존 생성 문제를 새 학습 과정에 비용 없이 복사한 경우 원본 작업 ID';

ALTER TABLE daily_career.test_attempt
    DROP CONSTRAINT uq_test_attempt_01,
    ADD CONSTRAINT uq_test_attempt_01 UNIQUE (user_id, user_curriculum_id, test_version_id, attempt_no);
