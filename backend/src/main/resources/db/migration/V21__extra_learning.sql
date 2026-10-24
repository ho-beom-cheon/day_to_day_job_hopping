CREATE TABLE daily_career.extra_learning (
    session_id bigint PRIMARY KEY,
    user_id bigint NOT NULL,
    mode varchar(16) NOT NULL CHECK (mode IN ('DEEPEN','EXPLORE','REVIEW','AHEAD')),
    target_minutes smallint NOT NULL CHECK (target_minutes IN (15,30,60)),
    problem_limit smallint NOT NULL CHECK (problem_limit BETWEEN 1 AND 100),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (session_id,user_id) REFERENCES daily_career.learning_day_item(id,user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_extra_learning_user ON daily_career.extra_learning(user_id,session_id);
CREATE TABLE daily_career.extra_learning_time (
    session_id bigint NOT NULL REFERENCES daily_career.extra_learning(session_id) ON DELETE RESTRICT,
    activity_date date NOT NULL,
    minutes smallint NOT NULL CHECK (minutes BETWEEN 1 AND 480),
    PRIMARY KEY (session_id,activity_date)
);
COMMENT ON TABLE daily_career.extra_learning IS '선택형 추가 학습. AHEAD만 기존 필수 세션을 참조하며 나머지는 선택 세션이다.';
COMMENT ON TABLE daily_career.extra_learning_time IS '서울 날짜별 자기보고 추가 학습 시간. 정규 세션 시간과 중복 합산하지 않는다.';
