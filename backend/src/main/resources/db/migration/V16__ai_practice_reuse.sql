CREATE TABLE daily_career.ai_practice_run (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    ai_job_id bigint NOT NULL,
    mode varchar(12) NOT NULL CHECK (mode IN ('STUDY', 'EXAM', 'REVIEW')),
    question_numbers jsonb NOT NULL CHECK (jsonb_typeof(question_numbers) = 'array'),
    status varchar(12) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT uq_ai_practice_run_owner UNIQUE (id, user_id),
    CONSTRAINT fk_ai_practice_run_job FOREIGN KEY (ai_job_id, user_id)
        REFERENCES daily_career.ai_job(id, user_id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_ai_practice_active_run
    ON daily_career.ai_practice_run(user_id, ai_job_id, mode) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_ai_practice_run_user ON daily_career.ai_practice_run(user_id, mode, id DESC);

CREATE TABLE daily_career.ai_practice_run_answer (
    user_id bigint NOT NULL,
    run_id bigint NOT NULL,
    question_no smallint NOT NULL CHECK (question_no BETWEEN 1 AND 10),
    answer_no smallint NOT NULL CHECK (answer_no BETWEEN 1 AND 4),
    correct boolean NOT NULL,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, question_no),
    CONSTRAINT fk_ai_practice_run_answer_owner FOREIGN KEY (run_id, user_id)
        REFERENCES daily_career.ai_practice_run(id, user_id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.ai_practice_wrong_note (
    user_id bigint NOT NULL REFERENCES daily_career.app_user(id) ON DELETE RESTRICT,
    ai_job_id bigint NOT NULL,
    question_no smallint NOT NULL CHECK (question_no BETWEEN 1 AND 10),
    note_text varchar(10000) NOT NULL DEFAULT '',
    status varchar(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    wrong_count integer NOT NULL DEFAULT 1 CHECK (wrong_count > 0),
    last_wrong_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ai_job_id, question_no),
    CONSTRAINT fk_ai_practice_wrong_job FOREIGN KEY (ai_job_id, user_id)
        REFERENCES daily_career.ai_job(id, user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_ai_practice_wrong_user ON daily_career.ai_practice_wrong_note(user_id, status, last_wrong_at DESC);

INSERT INTO daily_career.ai_practice_wrong_note(user_id, ai_job_id, question_no, wrong_count, last_wrong_at, created_at, updated_at)
SELECT user_id, ai_job_id, question_no, 1, submitted_at, submitted_at, submitted_at
FROM daily_career.ai_practice_attempt WHERE correct = false;
