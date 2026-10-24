CREATE TABLE daily_career.ai_practice_question_report (
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    ai_job_id bigint NOT NULL,
    question_no smallint NOT NULL CHECK (question_no BETWEEN 1 AND 20),
    reason varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ai_job_id, question_no),
    CONSTRAINT fk_ai_practice_question_report_job FOREIGN KEY (ai_job_id, user_id)
        REFERENCES daily_career.ai_job(id, user_id) ON DELETE RESTRICT
);

COMMENT ON TABLE daily_career.ai_practice_question_report IS
    '학습자가 정답 또는 해설 오류를 표시한 AI 문항. 검토 전까지 재학습과 점수에서 제외한다.';
