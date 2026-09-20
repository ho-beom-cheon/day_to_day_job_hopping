-- Stage 8 roll-forward support for the public practice API.
ALTER TABLE daily_career.problem_version DROP CONSTRAINT ck_problem_version_07;
ALTER TABLE daily_career.problem_version ADD CONSTRAINT ck_problem_version_07 CHECK (
    question_type IN ('SINGLE_CHOICE', 'TRUE_FALSE', 'MULTI_CHOICE', 'SHORT_ANSWER', 'ESSAY', 'CODE', 'INTERVIEW')
);

CREATE TABLE daily_career.problem_report (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    problem_id bigint NOT NULL,
    problem_version_id bigint NOT NULL,
    reason varchar(32) NOT NULL,
    comment text NOT NULL,
    status varchar(32) DEFAULT 'RECEIVED' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_problem_report PRIMARY KEY (id),
    CONSTRAINT uq_problem_report_01 UNIQUE (id, user_id),
    CONSTRAINT ck_problem_report_01 CHECK (reason IN ('WRONG_ANSWER', 'UNCLEAR_QUESTION', 'OTHER')),
    CONSTRAINT ck_problem_report_02 CHECK (status IN ('RECEIVED', 'REVIEWING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_problem_report_03 CHECK (length(comment) BETWEEN 1 AND 2000),
    CONSTRAINT ck_problem_report_04 CHECK (revision >= 0),
    CONSTRAINT fk_problem_report_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_problem_report_02 FOREIGN KEY (problem_id) REFERENCES daily_career.problem(id) ON DELETE RESTRICT,
    CONSTRAINT fk_problem_report_03 FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES daily_career.problem_version(id, problem_id) ON DELETE RESTRICT
);

CREATE INDEX ix_problem_report_problem ON daily_career.problem_report(problem_id, created_at DESC, id DESC);
CREATE INDEX ix_problem_report_user ON daily_career.problem_report(user_id, created_at DESC, id DESC);

COMMENT ON TABLE daily_career.problem_report IS '접근 가능한 일반 문제의 사용자 오류 신고. 원본 문제 버전은 불변으로 보존한다.';
COMMENT ON COLUMN daily_career.problem_report.problem_version_id IS '신고 당시 조회한 불변 문제 버전';
COMMENT ON COLUMN daily_career.problem_report.status IS '허용값: RECEIVED, REVIEWING, RESOLVED, REJECTED';

ALTER TABLE daily_career.wrong_answer
    ADD COLUMN review_count integer DEFAULT 0 NOT NULL,
    ADD COLUMN resolution_type varchar(32),
    ADD COLUMN resolution_attempt_id bigint;
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT ck_wrong_answer_03 CHECK (review_count >= 0);
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT ck_wrong_answer_04 CHECK (
    (status = 'MASTERED' AND mastered_at IS NOT NULL AND resolution_type IN ('MANUAL', 'CORRECT_RETRY')) OR
    (status <> 'MASTERED' AND mastered_at IS NULL AND resolution_type IS NULL AND resolution_attempt_id IS NULL)
);
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT ck_wrong_answer_05 CHECK (
    (resolution_type = 'MANUAL' AND resolution_attempt_id IS NULL) OR
    (resolution_type = 'CORRECT_RETRY' AND resolution_attempt_id IS NOT NULL) OR
    resolution_type IS NULL
);
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT fk_wrong_answer_resolution_attempt
    FOREIGN KEY (resolution_attempt_id, user_id) REFERENCES daily_career.problem_attempt(id, user_id) ON DELETE RESTRICT;
