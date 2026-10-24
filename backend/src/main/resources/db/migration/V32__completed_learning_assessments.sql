-- New records only: preserve all existing assignments, answers and test attempts.
CREATE TABLE daily_career.study_completion (
    assigned_content_id bigint PRIMARY KEY,
    user_id bigint NOT NULL,
    course_id bigint NOT NULL,
    studied_on date NOT NULL,
    day_no smallint NOT NULL,
    week_no smallint NOT NULL,
    month_no smallint NOT NULL,
    extra boolean NOT NULL,
    FOREIGN KEY (assigned_content_id,user_id) REFERENCES daily_career.assigned_content(id,user_id),
    FOREIGN KEY (course_id,user_id) REFERENCES daily_career.user_curriculum(id,user_id)
);
CREATE INDEX ix_study_completion_scope ON daily_career.study_completion(user_id,course_id,studied_on);

CREATE FUNCTION daily_career.record_study_completion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.completed_at IS NOT NULL THEN
        INSERT INTO daily_career.study_completion
        SELECT NEW.id,NEW.user_id,i.user_curriculum_id,(NEW.completed_at AT TIME ZONE 'Asia/Seoul')::date,
               period.day_no,period.week_no,period.month_no,
               (NOT i.required OR EXISTS(SELECT 1 FROM daily_career.extra_learning e WHERE e.session_id=i.id))
        FROM daily_career.learning_day_item i
        CROSS JOIN LATERAL (
            SELECT a.day_no,a.week_no,a.month_no FROM daily_career.assigned_learning_day a
            JOIN daily_career.learning_day d ON d.id=a.learning_day_id
            WHERE d.user_curriculum_id=i.user_curriculum_id AND d.user_id=NEW.user_id
            ORDER BY (d.local_date <= (NEW.completed_at AT TIME ZONE 'Asia/Seoul')::date) DESC,
              CASE WHEN d.local_date <= (NEW.completed_at AT TIME ZONE 'Asia/Seoul')::date THEN d.local_date END DESC,
              d.local_date ASC LIMIT 1
        ) period
        WHERE i.id=NEW.learning_day_item_id AND i.user_id=NEW.user_id
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER record_study_completion AFTER INSERT OR UPDATE OF completed_at ON daily_career.assigned_content
    FOR EACH ROW EXECUTE FUNCTION daily_career.record_study_completion();

-- Backfill without changing the original completion timestamps or revisions.
INSERT INTO daily_career.study_completion
SELECT a.id,a.user_id,i.user_curriculum_id,(a.completed_at AT TIME ZONE 'Asia/Seoul')::date,
       period.day_no,period.week_no,period.month_no,
       (NOT i.required OR EXISTS(SELECT 1 FROM daily_career.extra_learning e WHERE e.session_id=i.id))
FROM daily_career.assigned_content a JOIN daily_career.learning_day_item i ON i.id=a.learning_day_item_id
CROSS JOIN LATERAL (
    SELECT ad.day_no,ad.week_no,ad.month_no FROM daily_career.assigned_learning_day ad
    JOIN daily_career.learning_day d ON d.id=ad.learning_day_id
    WHERE d.user_curriculum_id=i.user_curriculum_id AND d.user_id=a.user_id
    ORDER BY (d.local_date <= (a.completed_at AT TIME ZONE 'Asia/Seoul')::date) DESC,
      CASE WHEN d.local_date <= (a.completed_at AT TIME ZONE 'Asia/Seoul')::date THEN d.local_date END DESC,
      d.local_date ASC LIMIT 1
) period WHERE a.completed_at IS NOT NULL;

CREATE TABLE daily_career.study_evaluation (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL,
    course_id bigint NOT NULL,
    kind varchar(10) NOT NULL CHECK (kind IN ('DAILY','WEEKLY','MONTHLY','FINAL')),
    period_key varchar(16) NOT NULL,
    title varchar(160) NOT NULL,
    scope_snapshot jsonb NOT NULL,
    retake_of bigint REFERENCES daily_career.study_evaluation(id),
    request_key uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (user_id,request_key), UNIQUE(id,user_id),
    FOREIGN KEY (course_id,user_id) REFERENCES daily_career.user_curriculum(id,user_id)
);
CREATE INDEX ix_study_evaluation_scope ON daily_career.study_evaluation(user_id,course_id,kind,period_key);
CREATE TABLE daily_career.study_evaluation_part (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluation_id bigint NOT NULL,
    user_id bigint NOT NULL,
    part_no integer NOT NULL,
    plan jsonb NOT NULL,
    generation_job_id bigint,
    grading_job_id bigint,
    started_at timestamptz,
    expires_at timestamptz,
    submitted_at timestamptz,
    answers jsonb NOT NULL DEFAULT '{}',
    revision integer NOT NULL DEFAULT 0,
    UNIQUE(evaluation_id,part_no),
    FOREIGN KEY (evaluation_id,user_id) REFERENCES daily_career.study_evaluation(id,user_id),
    FOREIGN KEY (generation_job_id,user_id) REFERENCES daily_career.ai_job(id,user_id),
    FOREIGN KEY (grading_job_id,user_id) REFERENCES daily_career.ai_job(id,user_id),
    CHECK ((started_at IS NULL)=(expires_at IS NULL)),
    CHECK (submitted_at IS NULL OR started_at IS NOT NULL)
);
