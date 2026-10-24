ALTER TABLE daily_career.ai_practice_attempt
    DROP CONSTRAINT ai_practice_attempt_question_no_check,
    ADD CONSTRAINT ai_practice_attempt_question_no_check CHECK (question_no BETWEEN 1 AND 20);

ALTER TABLE daily_career.ai_practice_run_answer
    DROP CONSTRAINT ai_practice_run_answer_question_no_check,
    ADD CONSTRAINT ai_practice_run_answer_question_no_check CHECK (question_no BETWEEN 1 AND 20);

ALTER TABLE daily_career.ai_practice_wrong_note
    DROP CONSTRAINT ai_practice_wrong_note_question_no_check,
    ADD CONSTRAINT ai_practice_wrong_note_question_no_check CHECK (question_no BETWEEN 1 AND 20);
