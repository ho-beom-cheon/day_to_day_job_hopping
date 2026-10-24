ALTER TABLE daily_career.grading_run
    ADD COLUMN error_code varchar(80);

ALTER TABLE daily_career.grading_run
    ADD CONSTRAINT ck_grading_run_07
    CHECK ((status = 'FAILED') = (error_code IS NOT NULL));

COMMENT ON COLUMN daily_career.grading_run.error_code IS
    '채점 실패 분류 코드. 답안, 정답, 공급자 원문 등 민감한 상세는 저장하지 않는다.';
