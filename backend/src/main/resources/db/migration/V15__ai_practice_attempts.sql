CREATE TABLE daily_career.ai_practice_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    ai_job_id bigint NOT NULL,
    question_no smallint NOT NULL CHECK (question_no BETWEEN 1 AND 10),
    answer_no smallint NOT NULL CHECK (answer_no BETWEEN 1 AND 4),
    correct boolean NOT NULL,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_practice_attempt UNIQUE (user_id, ai_job_id, question_no),
    CONSTRAINT fk_ai_practice_attempt_job FOREIGN KEY (ai_job_id, user_id)
        REFERENCES daily_career.ai_job(id, user_id) ON DELETE RESTRICT
);

CREATE INDEX ix_ai_practice_attempt_job ON daily_career.ai_practice_attempt(ai_job_id, user_id);

COMMENT ON TABLE daily_career.ai_practice_attempt IS '사용자별 AI 확인 문제의 최초 제출. 공식 시험·커리큘럼 점수와 분리한다.';
