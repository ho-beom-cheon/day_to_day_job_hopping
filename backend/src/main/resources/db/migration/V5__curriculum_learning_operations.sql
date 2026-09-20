-- API-facing extensions; preserve the original business dictionary and existing rows.
CREATE TABLE daily_career.curriculum_release (
    curriculum_version_id bigint PRIMARY KEY REFERENCES daily_career.curriculum_version(id) ON DELETE RESTRICT,
    version_label varchar(40) NOT NULL CHECK (length(version_label) > 0),
    title varchar(120) NOT NULL CHECK (length(title) > 0)
);
CREATE TABLE daily_career.curriculum_unit_detail (
    curriculum_unit_id bigint PRIMARY KEY REFERENCES daily_career.curriculum_unit(id) ON DELETE RESTRICT,
    description varchar(2000) NOT NULL DEFAULT '',
    learning_goals jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(learning_goals)='array'),
    month_no smallint CHECK (month_no BETWEEN 1 AND 6),
    week_no smallint CHECK (week_no BETWEEN 1 AND 60),
    day_no smallint CHECK (day_no BETWEEN 1 AND 366)
);
CREATE TABLE daily_career.learning_content_detail (
    content_version_id bigint PRIMARY KEY REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    title varchar(120) NOT NULL CHECK (length(title)>0),
    learning_objective varchar(1000) NOT NULL CHECK (length(learning_objective)>0),
    key_points jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(key_points)='array'),
    interview_points jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(interview_points)='array')
);
CREATE TABLE daily_career.learning_content_problem (
    content_version_id bigint NOT NULL REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    problem_version_id bigint NOT NULL REFERENCES daily_career.problem_version(id) ON DELETE RESTRICT,
    sequence smallint NOT NULL CHECK (sequence BETWEEN 1 AND 100),
    PRIMARY KEY (content_version_id, problem_version_id),
    UNIQUE (content_version_id, sequence)
);
CREATE INDEX ix_content_problem_version ON daily_career.learning_content_problem(problem_version_id);
CREATE TABLE daily_career.curriculum_item_content (
    curriculum_unit_id bigint NOT NULL REFERENCES daily_career.curriculum_unit(id) ON DELETE RESTRICT,
    sequence smallint NOT NULL CHECK (sequence BETWEEN 1 AND 100),
    content_version_id bigint NOT NULL REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    required boolean NOT NULL,
    content_type varchar(16) NOT NULL CHECK (content_type IN ('CONCEPT','EXAMPLE','PRACTICE','REVIEW')),
    completion_rule varchar(32) NOT NULL CHECK (completion_rule IN ('READ_ACK','ALL_PROBLEMS_ATTEMPTED')),
    PRIMARY KEY (curriculum_unit_id, sequence),
    UNIQUE (curriculum_unit_id, content_version_id)
);
CREATE INDEX ix_item_content_version ON daily_career.curriculum_item_content(content_version_id);
CREATE TABLE daily_career.assigned_learning_day (
    learning_day_id bigint PRIMARY KEY,
    user_id bigint NOT NULL,
    curriculum_unit_id bigint NOT NULL REFERENCES daily_career.curriculum_unit(id) ON DELETE RESTRICT,
    day_no smallint NOT NULL CHECK (day_no BETWEEN 1 AND 366),
    month_no smallint NOT NULL CHECK (month_no BETWEEN 1 AND 6),
    week_no smallint NOT NULL CHECK (week_no BETWEEN 1 AND 60),
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    FOREIGN KEY (learning_day_id,user_id) REFERENCES daily_career.learning_day(id,user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_assigned_day_unit ON daily_career.assigned_learning_day(curriculum_unit_id);
CREATE TABLE daily_career.assigned_session_detail (
    learning_day_item_id bigint PRIMARY KEY,
    user_id bigint NOT NULL,
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    category varchar(40) NOT NULL CHECK (category ~ '^[A-Z][A-Z0-9_]*$'),
    estimated_minutes smallint NOT NULL CHECK (estimated_minutes BETWEEN 1 AND 480),
    actual_minutes smallint NOT NULL DEFAULT 0 CHECK (actual_minutes BETWEEN 0 AND 480),
    FOREIGN KEY (learning_day_item_id,user_id) REFERENCES daily_career.learning_day_item(id,user_id) ON DELETE RESTRICT
);
CREATE TABLE daily_career.assigned_content (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL,
    learning_day_item_id bigint NOT NULL,
    content_version_id bigint NOT NULL REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
    sequence smallint NOT NULL CHECK (sequence BETWEEN 1 AND 100),
    required boolean NOT NULL,
    content_type varchar(16) NOT NULL CHECK (content_type IN ('CONCEPT','EXAMPLE','PRACTICE','REVIEW')),
    completion_rule varchar(32) NOT NULL CHECK (completion_rule IN ('READ_ACK','ALL_PROBLEMS_ATTEMPTED')),
    completed_at timestamptz,
    UNIQUE (learning_day_item_id,sequence),
    UNIQUE (id,user_id),
    FOREIGN KEY (learning_day_item_id,user_id) REFERENCES daily_career.learning_day_item(id,user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_assigned_content_version ON daily_career.assigned_content(content_version_id);

-- Serialize publication with edits. Once published, payload is immutable; retiring is allowed.
CREATE FUNCTION daily_career.guard_published_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' THEN
        IF TG_OP='DELETE' THEN RAISE EXCEPTION 'published version is immutable' USING ERRCODE='23514'; END IF;
        IF (to_jsonb(OLD)-ARRAY['status','updated_at','revision']) IS DISTINCT FROM
           (to_jsonb(NEW)-ARRAY['status','updated_at','revision']) OR
           NEW.status NOT IN ('PUBLISHED','RETIRED') OR (OLD.status='RETIRED' AND NEW.status<>'RETIRED') THEN
            RAISE EXCEPTION 'published version is immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_curriculum_version BEFORE UPDATE OR DELETE ON daily_career.curriculum_version
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_version();
CREATE TRIGGER immutable_learning_content_version BEFORE UPDATE OR DELETE ON daily_career.learning_content_version
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_version();

CREATE FUNCTION daily_career.guard_published_child() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payload jsonb; previous jsonb; version_id bigint; state text;
BEGIN
    -- Check both old and new parents so a published row cannot be moved to a draft.
    IF TG_OP<>'INSERT' THEN previous:=to_jsonb(OLD); END IF;
    IF TG_OP<>'DELETE' THEN payload:=to_jsonb(NEW); ELSE payload:=previous; END IF;
    FOR version_id IN
        SELECT DISTINCT v FROM (
            SELECT CASE WHEN TG_ARGV[0]='unit' THEN
                (SELECT curriculum_version_id FROM daily_career.curriculum_unit WHERE id=(p->>'curriculum_unit_id')::bigint)
                ELSE (p->>TG_ARGV[1])::bigint END AS v
            FROM (VALUES (payload),(previous)) AS items(p) WHERE p IS NOT NULL
        ) versions WHERE v IS NOT NULL ORDER BY v
    LOOP
        IF TG_ARGV[0]='content' THEN
            SELECT status INTO state FROM daily_career.learning_content_version WHERE id=version_id FOR UPDATE;
        ELSE
            SELECT status INTO state FROM daily_career.curriculum_version WHERE id=version_id FOR UPDATE;
        END IF;
        IF state<>'DRAFT' THEN RAISE EXCEPTION 'published composition is immutable' USING ERRCODE='23514'; END IF;
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_curriculum_release BEFORE INSERT OR UPDATE OR DELETE ON daily_career.curriculum_release
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('curriculum','curriculum_version_id');
CREATE TRIGGER immutable_curriculum_unit BEFORE INSERT OR UPDATE OR DELETE ON daily_career.curriculum_unit
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('curriculum','curriculum_version_id');
CREATE TRIGGER immutable_curriculum_unit_detail BEFORE INSERT OR UPDATE OR DELETE ON daily_career.curriculum_unit_detail
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('unit','curriculum_unit_id');
CREATE TRIGGER immutable_curriculum_item_content BEFORE INSERT OR UPDATE OR DELETE ON daily_career.curriculum_item_content
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('unit','curriculum_unit_id');
CREATE TRIGGER immutable_learning_content_detail BEFORE INSERT OR UPDATE OR DELETE ON daily_career.learning_content_detail
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('content','content_version_id');
CREATE TRIGGER immutable_learning_content_problem BEFORE INSERT OR UPDATE OR DELETE ON daily_career.learning_content_problem
    FOR EACH ROW EXECUTE FUNCTION daily_career.guard_published_child('content','content_version_id');

COMMENT ON TABLE daily_career.curriculum_release IS '발행된 교육 버전의 공개 제목과 버전명. templateId는 curriculum_version_id다.';
COMMENT ON TABLE daily_career.curriculum_unit_detail IS '불변 교육 단위의 설명과 목표. DAY의 월차/주차/일차는 달력과 분리한다.';
COMMENT ON TABLE daily_career.learning_content_detail IS '불변 콘텐츠 버전의 공개 제목과 학습 목표. 원형 제목 변경이 과거 학습을 바꾸지 않는다.';
COMMENT ON TABLE daily_career.learning_content_problem IS '콘텐츠의 확인 문제 버전. 정답은 별도 문제 버전에만 보관한다.';
COMMENT ON TABLE daily_career.curriculum_item_content IS '교육 세션의 콘텐츠 편성 및 완료 조건. 발행 후 변경 금지.';
COMMENT ON TABLE daily_career.assigned_learning_day IS '배정된 고정 학습 단위. 휴식일에는 생성하지 않는다. 날짜 이동 시 행을 보존한다.';
COMMENT ON TABLE daily_career.assigned_session_detail IS 'API 세션은 learning_day_item이다. actual_minutes는 자기보고 값으로 시간 측정 세션과 구분한다.';
COMMENT ON TABLE daily_career.assigned_content IS '사용자/세션별 콘텐츠 완료 인스턴스. 공유 콘텐츠 버전과 완료 상태를 분리한다.';
COMMENT ON COLUMN daily_career.curriculum_release.curriculum_version_id IS '고정 교육 버전';
COMMENT ON COLUMN daily_career.curriculum_release.version_label IS '공개 콘텐츠 버전명';
COMMENT ON COLUMN daily_career.curriculum_release.title IS '발행 또는 배정 시 고정한 제목';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.curriculum_unit_id IS '불변 교육 단위';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.description IS '설명';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.learning_goals IS '교육 목표 문자열 목록';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.month_no IS '교육 월차 1..6';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.week_no IS '교육 주차 1..60';
COMMENT ON COLUMN daily_career.curriculum_unit_detail.day_no IS '고정 학습 일차 1..366';
COMMENT ON COLUMN daily_career.learning_content_detail.content_version_id IS '불변 학습 콘텐츠 버전';
COMMENT ON COLUMN daily_career.learning_content_detail.title IS '발행 또는 배정 시 고정한 제목';
COMMENT ON COLUMN daily_career.learning_content_detail.learning_objective IS '학습 목표';
COMMENT ON COLUMN daily_career.learning_content_detail.key_points IS '핵심 정리 문자열 목록';
COMMENT ON COLUMN daily_career.learning_content_detail.interview_points IS '면접 관점 문자열 목록';
COMMENT ON COLUMN daily_career.learning_content_problem.content_version_id IS '불변 학습 콘텐츠 버전';
COMMENT ON COLUMN daily_career.learning_content_problem.problem_version_id IS '불변 확인 문제 버전';
COMMENT ON COLUMN daily_career.learning_content_problem.sequence IS '내부 표시 순서';
COMMENT ON COLUMN daily_career.curriculum_item_content.curriculum_unit_id IS '불변 교육 단위';
COMMENT ON COLUMN daily_career.curriculum_item_content.sequence IS '내부 표시 순서';
COMMENT ON COLUMN daily_career.curriculum_item_content.content_version_id IS '불변 학습 콘텐츠 버전';
COMMENT ON COLUMN daily_career.curriculum_item_content.required IS '완료에 필수인지 여부';
COMMENT ON COLUMN daily_career.curriculum_item_content.content_type IS '공개 콘텐츠 종류';
COMMENT ON COLUMN daily_career.curriculum_item_content.completion_rule IS '읽음 또는 전체 확인 문제 제출 조건';
COMMENT ON COLUMN daily_career.assigned_learning_day.learning_day_id IS '배정 달력 행';
COMMENT ON COLUMN daily_career.assigned_learning_day.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.assigned_learning_day.curriculum_unit_id IS '불변 교육 단위';
COMMENT ON COLUMN daily_career.assigned_learning_day.day_no IS '고정 학습 일차 1..366';
COMMENT ON COLUMN daily_career.assigned_learning_day.month_no IS '교육 월차 1..6';
COMMENT ON COLUMN daily_career.assigned_learning_day.week_no IS '교육 주차 1..60';
COMMENT ON COLUMN daily_career.assigned_learning_day.title IS '발행 또는 배정 시 고정한 제목';
COMMENT ON COLUMN daily_career.assigned_learning_day.description IS '설명';
COMMENT ON COLUMN daily_career.assigned_session_detail.learning_day_item_id IS '배정 학습 세션';
COMMENT ON COLUMN daily_career.assigned_session_detail.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.assigned_session_detail.title IS '발행 또는 배정 시 고정한 제목';
COMMENT ON COLUMN daily_career.assigned_session_detail.description IS '설명';
COMMENT ON COLUMN daily_career.assigned_session_detail.category IS '교육 분야 코드';
COMMENT ON COLUMN daily_career.assigned_session_detail.estimated_minutes IS '발행 시 예상 학습 분';
COMMENT ON COLUMN daily_career.assigned_session_detail.actual_minutes IS '완료 시 자기보고 학습 분; 완료 전 0';
COMMENT ON COLUMN daily_career.assigned_content.id IS '배정 콘텐츠 인스턴스; JSON 문자열 ID';
COMMENT ON COLUMN daily_career.assigned_content.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.assigned_content.learning_day_item_id IS '배정 학습 세션';
COMMENT ON COLUMN daily_career.assigned_content.content_version_id IS '불변 학습 콘텐츠 버전';
COMMENT ON COLUMN daily_career.assigned_content.sequence IS '내부 표시 순서';
COMMENT ON COLUMN daily_career.assigned_content.required IS '완료에 필수인지 여부';
COMMENT ON COLUMN daily_career.assigned_content.content_type IS '공개 콘텐츠 종류';
COMMENT ON COLUMN daily_career.assigned_content.completion_rule IS '읽음 또는 전체 확인 문제 제출 조건';
COMMENT ON COLUMN daily_career.assigned_content.completed_at IS '최초 완료 시각; 미완료는 NULL';
