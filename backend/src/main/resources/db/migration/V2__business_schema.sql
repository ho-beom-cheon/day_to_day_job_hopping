-- Source: DB detailed design v0.1 (46 tables / 557 columns / 105 foreign keys).
-- See docs/contracts/database-stage3-alignment.md. Never edit after application.
CREATE SCHEMA daily_career;

CREATE TABLE daily_career.app_user (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    display_name varchar(80) NOT NULL,
    email varchar(320),
    status varchar(32) DEFAULT 'ACTIVE' NOT NULL,
    withdrawn_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ck_app_user_01 CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)),
    CONSTRAINT ck_app_user_02 CHECK (revision >= 0),
    CONSTRAINT ck_app_user_03 CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
);
COMMENT ON TABLE daily_career.app_user IS '비밀번호·Google 액세스 토큰을 저장하지 않는다. 이메일 변경으로 계정을 자동 병합하지 않는다.';
COMMENT ON COLUMN daily_career.app_user.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.app_user.display_name IS '표시 이름';
COMMENT ON COLUMN daily_career.app_user.email IS '연락용 이메일; 로그인 식별키로 사용하지 않음';
COMMENT ON COLUMN daily_career.app_user.status IS '허용값: ACTIVE, SUSPENDED, WITHDRAWN';
COMMENT ON COLUMN daily_career.app_user.withdrawn_at IS '탈퇴 요청 시각';
COMMENT ON COLUMN daily_career.app_user.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.app_user.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.app_user.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.user_identity (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    issuer varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    last_login_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_user_identity PRIMARY KEY (id),
    CONSTRAINT uq_user_identity_01 UNIQUE (issuer, subject),
    CONSTRAINT uq_user_identity_02 UNIQUE (id, user_id),
    CONSTRAINT ck_user_identity_01 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.user_identity IS '검증된 issuer + subject로 계정을 찾는다. 외부 로그인 연결·해제는 별도 인증 검증을 거친다.';
COMMENT ON COLUMN daily_career.user_identity.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.user_identity.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.user_identity.issuer IS 'OIDC 발급자 식별자';
COMMENT ON COLUMN daily_career.user_identity.subject IS 'OIDC subject';
COMMENT ON COLUMN daily_career.user_identity.last_login_at IS '최근 로그인 시각';
COMMENT ON COLUMN daily_career.user_identity.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.user_identity.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.user_identity.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.user_setting (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    timezone varchar(64) DEFAULT 'Asia/Seoul' NOT NULL,
    locale varchar(16) DEFAULT 'ko-KR' NOT NULL,
    target_role varchar(160),
    daily_goal_minutes smallint DEFAULT 60 NOT NULL,
    onboarding_completed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_user_setting PRIMARY KEY (id),
    CONSTRAINT uq_user_setting_01 UNIQUE (user_id),
    CONSTRAINT uq_user_setting_02 UNIQUE (id, user_id),
    CONSTRAINT ck_user_setting_01 CHECK (daily_goal_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_user_setting_02 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.user_setting IS '기존 과정의 타임존과 과거 날짜는 설정 변경으로 소급 수정하지 않는다.';
COMMENT ON COLUMN daily_career.user_setting.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.user_setting.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.user_setting.timezone IS 'IANA 타임존; 신규 과정·알림의 기본값';
COMMENT ON COLUMN daily_career.user_setting.locale IS '화면 언어';
COMMENT ON COLUMN daily_career.user_setting.target_role IS '목표 직무';
COMMENT ON COLUMN daily_career.user_setting.daily_goal_minutes IS '신규 일정 기본 학습 분';
COMMENT ON COLUMN daily_career.user_setting.onboarding_completed_at IS '온보딩 완료';
COMMENT ON COLUMN daily_career.user_setting.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.user_setting.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.user_setting.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.subject (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    code varchar(40) NOT NULL,
    name varchar(100) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_subject PRIMARY KEY (id),
    CONSTRAINT uq_subject_01 UNIQUE (code),
    CONSTRAINT ck_subject_01 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.subject IS '예: JAVA, SPRING, SQL, ALGORITHM, FINANCE, AI_PRACTICE, INTERVIEW. 숙련도는 분야별로 집계한다.';
COMMENT ON COLUMN daily_career.subject.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.subject.code IS '분야 코드';
COMMENT ON COLUMN daily_career.subject.name IS '분야명';
COMMENT ON COLUMN daily_career.subject.enabled IS '사용 여부';
COMMENT ON COLUMN daily_career.subject.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.subject.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.subject.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.curriculum (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    code varchar(64) NOT NULL,
    title varchar(200) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_curriculum PRIMARY KEY (id),
    CONSTRAINT uq_curriculum_01 UNIQUE (code),
    CONSTRAINT ck_curriculum_01 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.curriculum IS '';
COMMENT ON COLUMN daily_career.curriculum.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.curriculum.code IS '불변 커리큘럼 코드';
COMMENT ON COLUMN daily_career.curriculum.title IS '제목';
COMMENT ON COLUMN daily_career.curriculum.enabled IS '신규 등록 허용';
COMMENT ON COLUMN daily_career.curriculum.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.curriculum.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.curriculum.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.curriculum_version (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    curriculum_id bigint NOT NULL,
    version_no integer NOT NULL,
    duration_months smallint DEFAULT 6 NOT NULL,
    status varchar(32) DEFAULT 'DRAFT' NOT NULL,
    description text,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_curriculum_version PRIMARY KEY (id),
    CONSTRAINT uq_curriculum_version_01 UNIQUE (curriculum_id, version_no),
    CONSTRAINT ck_curriculum_version_01 CHECK (version_no > 0),
    CONSTRAINT ck_curriculum_version_02 CHECK (duration_months BETWEEN 1 AND 36),
    CONSTRAINT ck_curriculum_version_03 CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_curriculum_version_04 CHECK (revision >= 0),
    CONSTRAINT ck_curriculum_version_05 CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);
COMMENT ON TABLE daily_career.curriculum_version IS '발행된 버전의 구성·완료율 분모는 고정한다. 수정 시 새 버전을 생성한다.';
COMMENT ON COLUMN daily_career.curriculum_version.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.curriculum_version.curriculum_id IS '커리큘럼 원형';
COMMENT ON COLUMN daily_career.curriculum_version.version_no IS '발행 버전';
COMMENT ON COLUMN daily_career.curriculum_version.duration_months IS '계획 기간; 기본 6개월';
COMMENT ON COLUMN daily_career.curriculum_version.status IS '허용값: DRAFT, PUBLISHED, RETIRED';
COMMENT ON COLUMN daily_career.curriculum_version.description IS '개요';
COMMENT ON COLUMN daily_career.curriculum_version.published_at IS '발행 시각';
COMMENT ON COLUMN daily_career.curriculum_version.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.curriculum_version.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.curriculum_version.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.curriculum_unit (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    curriculum_version_id bigint NOT NULL,
    parent_id bigint,
    unit_type varchar(32) NOT NULL,
    order_no integer NOT NULL,
    title varchar(200) NOT NULL,
    subject_id bigint,
    activity_type varchar(24),
    estimated_minutes smallint,
    completion_weight numeric(8,3),
    content_version_id bigint,
    problem_version_id bigint,
    test_version_id bigint,
    required boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_curriculum_unit PRIMARY KEY (id),
    CONSTRAINT uq_curriculum_unit_01 UNIQUE (id, curriculum_version_id),
    CONSTRAINT ck_curriculum_unit_01 CHECK (order_no > 0),
    CONSTRAINT ck_curriculum_unit_02 CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_curriculum_unit_03 CHECK ((unit_type = 'MONTH') = (parent_id IS NULL)),
    CONSTRAINT ck_curriculum_unit_04 CHECK ((unit_type = 'ITEM' AND activity_type IS NOT NULL AND activity_type IN ('LEARN','PRACTICE','REVIEW','INTERVIEW','TEST') AND estimated_minutes IS NOT NULL AND estimated_minutes > 0 AND completion_weight IS NOT NULL AND completion_weight > 0) OR (unit_type <> 'ITEM' AND activity_type IS NULL AND estimated_minutes IS NULL AND completion_weight IS NULL)),
    CONSTRAINT ck_curriculum_unit_05 CHECK (num_nonnulls(content_version_id, problem_version_id, test_version_id) <= 1),
    CONSTRAINT ck_curriculum_unit_06 CHECK (revision >= 0),
    CONSTRAINT ck_curriculum_unit_07 CHECK (unit_type IN ('MONTH', 'WEEK', 'DAY', 'ITEM'))
);
COMMENT ON TABLE daily_career.curriculum_unit IS 'MONTH→WEEK→DAY→ITEM 구조·순환 금지·ITEM 타깃 종류·총 구성 수는 발행 서비스에서 검증한다. 월/주/일은 교육 차수이지 실제 달력 날짜가 아니다.';
COMMENT ON COLUMN daily_career.curriculum_unit.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.curriculum_unit.curriculum_version_id IS '소속 커리큘럼 버전';
COMMENT ON COLUMN daily_career.curriculum_unit.parent_id IS '상위 구성';
COMMENT ON COLUMN daily_career.curriculum_unit.unit_type IS '허용값: MONTH, WEEK, DAY, ITEM';
COMMENT ON COLUMN daily_career.curriculum_unit.order_no IS '같은 부모 내 순서';
COMMENT ON COLUMN daily_career.curriculum_unit.title IS '구성명';
COMMENT ON COLUMN daily_career.curriculum_unit.subject_id IS '학습 분야';
COMMENT ON COLUMN daily_career.curriculum_unit.activity_type IS 'ITEM만 사용: LEARN/PRACTICE/REVIEW/INTERVIEW/TEST';
COMMENT ON COLUMN daily_career.curriculum_unit.estimated_minutes IS '예상 분; ITEM만 사용';
COMMENT ON COLUMN daily_career.curriculum_unit.completion_weight IS '완료율 가중치; ITEM만 사용';
COMMENT ON COLUMN daily_career.curriculum_unit.content_version_id IS '기본 콘텐츠 버전';
COMMENT ON COLUMN daily_career.curriculum_unit.problem_version_id IS '기본 문제 버전';
COMMENT ON COLUMN daily_career.curriculum_unit.test_version_id IS '기본 시험 버전';
COMMENT ON COLUMN daily_career.curriculum_unit.required IS '완료율 필수 항목';
COMMENT ON COLUMN daily_career.curriculum_unit.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.curriculum_unit.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.curriculum_unit.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.user_curriculum (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    curriculum_version_id bigint NOT NULL,
    start_date date NOT NULL,
    target_end_date date NOT NULL,
    projected_end_date date NOT NULL,
    timezone varchar(64) DEFAULT 'Asia/Seoul' NOT NULL,
    status varchar(32) DEFAULT 'PLANNED' NOT NULL,
    schedule_revision bigint DEFAULT 0 NOT NULL,
    completed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_user_curriculum PRIMARY KEY (id),
    CONSTRAINT uq_user_curriculum_01 UNIQUE (id, user_id, curriculum_version_id),
    CONSTRAINT uq_user_curriculum_02 UNIQUE (id, user_id),
    CONSTRAINT ck_user_curriculum_01 CHECK (target_end_date >= start_date),
    CONSTRAINT ck_user_curriculum_02 CHECK (projected_end_date >= start_date),
    CONSTRAINT ck_user_curriculum_03 CHECK (schedule_revision >= 0),
    CONSTRAINT ck_user_curriculum_04 CHECK (revision >= 0),
    CONSTRAINT ck_user_curriculum_05 CHECK (status IN ('PLANNED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELED'))
);
COMMENT ON TABLE daily_career.user_curriculum IS '초기 제안은 사용자당 미종료 과정 1개. 완료·취소 이력은 여러 개 유지한다. 6개월은 달력 개월 단위로 산출하며 180일로 치환하지 않는다.';
COMMENT ON COLUMN daily_career.user_curriculum.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.user_curriculum.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.user_curriculum.curriculum_version_id IS '등록 시 고정한 버전';
COMMENT ON COLUMN daily_career.user_curriculum.start_date IS '과정 시작 지역 날짜';
COMMENT ON COLUMN daily_career.user_curriculum.target_end_date IS '최초 목표 종료일';
COMMENT ON COLUMN daily_career.user_curriculum.projected_end_date IS '재배치 후 예상 종료일';
COMMENT ON COLUMN daily_career.user_curriculum.timezone IS '과정 날짜 판정용 고정 타임존';
COMMENT ON COLUMN daily_career.user_curriculum.status IS '허용값: PLANNED, ACTIVE, PAUSED, COMPLETED, CANCELED';
COMMENT ON COLUMN daily_career.user_curriculum.schedule_revision IS '일정 전체 동시성 버전';
COMMENT ON COLUMN daily_career.user_curriculum.completed_at IS '실제 완료 시각';
COMMENT ON COLUMN daily_career.user_curriculum.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.user_curriculum.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.user_curriculum.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.schedule_policy (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    weekly_rest_count smallint DEFAULT 2 NOT NULL,
    study_minutes smallint DEFAULT 60 NOT NULL,
    reschedule_mode varchar(32) DEFAULT 'EXTEND_END_DATE' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_schedule_policy PRIMARY KEY (id),
    CONSTRAINT uq_schedule_policy_01 UNIQUE (user_curriculum_id, effective_from),
    CONSTRAINT uq_schedule_policy_02 UNIQUE (id, user_curriculum_id, user_id),
    CONSTRAINT uq_schedule_policy_03 UNIQUE (id, user_id),
    CONSTRAINT ck_schedule_policy_01 CHECK (weekly_rest_count BETWEEN 0 AND 7),
    CONSTRAINT ck_schedule_policy_02 CHECK (study_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_schedule_policy_03 CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_schedule_policy_04 CHECK (revision >= 0),
    CONSTRAINT ck_schedule_policy_05 CHECK (reschedule_mode IN ('EXTEND_END_DATE', 'KEEP_END_DATE'))
);
COMMENT ON TABLE daily_career.schedule_policy IS '기간 겹침, 요일 행 수=휴식 횟수는 과정 잠금 안에서 서비스 검증한다. 정책 적용 기간은 [from,to). 변경은 새 행 생성; 종료시각만 닫는다.';
COMMENT ON COLUMN daily_career.schedule_policy.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.schedule_policy.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.schedule_policy.user_curriculum_id IS '사용자 과정';
COMMENT ON COLUMN daily_career.schedule_policy.effective_from IS '적용 시작일';
COMMENT ON COLUMN daily_career.schedule_policy.effective_to IS '적용 종료일; 끝 날짜 제외';
COMMENT ON COLUMN daily_career.schedule_policy.weekly_rest_count IS '완전한 주의 기본 휴식일 수';
COMMENT ON COLUMN daily_career.schedule_policy.study_minutes IS '학습일 기본 시간';
COMMENT ON COLUMN daily_career.schedule_policy.reschedule_mode IS '허용값: EXTEND_END_DATE, KEEP_END_DATE';
COMMENT ON COLUMN daily_career.schedule_policy.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.schedule_policy.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.schedule_policy.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.schedule_rest_weekday (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    schedule_policy_id bigint NOT NULL,
    iso_weekday smallint NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_schedule_rest_weekday PRIMARY KEY (id),
    CONSTRAINT uq_schedule_rest_weekday_01 UNIQUE (schedule_policy_id, iso_weekday),
    CONSTRAINT ck_schedule_rest_weekday_01 CHECK (iso_weekday BETWEEN 1 AND 7)
);
COMMENT ON TABLE daily_career.schedule_rest_weekday IS '';
COMMENT ON COLUMN daily_career.schedule_rest_weekday.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.schedule_rest_weekday.schedule_policy_id IS '일정 정책';
COMMENT ON COLUMN daily_career.schedule_rest_weekday.iso_weekday IS '월=1 ~ 일=7';
COMMENT ON COLUMN daily_career.schedule_rest_weekday.created_at IS '생성 시각';

CREATE TABLE daily_career.learning_day (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    local_date date NOT NULL,
    day_type varchar(32) DEFAULT 'STUDY' NOT NULL,
    source varchar(32) DEFAULT 'POLICY' NOT NULL,
    schedule_policy_id bigint,
    planned_minutes smallint DEFAULT 60 NOT NULL,
    rest_reason text,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_learning_day PRIMARY KEY (id),
    CONSTRAINT uq_learning_day_01 UNIQUE (user_curriculum_id, local_date),
    CONSTRAINT uq_learning_day_02 UNIQUE (id, user_curriculum_id, user_id),
    CONSTRAINT uq_learning_day_03 UNIQUE (id, user_id),
    CONSTRAINT ck_learning_day_01 CHECK (planned_minutes BETWEEN 0 AND 1440),
    CONSTRAINT ck_learning_day_02 CHECK (day_type <> 'REST' OR planned_minutes = 0),
    CONSTRAINT ck_learning_day_03 CHECK (revision >= 0),
    CONSTRAINT ck_learning_day_04 CHECK (day_type IN ('STUDY', 'REST')),
    CONSTRAINT ck_learning_day_05 CHECK (source IN ('POLICY', 'MANUAL'))
);
COMMENT ON TABLE daily_career.learning_day IS '하루 달력 행은 1개이며 휴식일도 행으로 관리한다. 일정상 휴식과 실제 자율 학습은 별개다. 지각·미이행은 오늘 날짜 기준 조회 결과로 계산한다.';
COMMENT ON COLUMN daily_career.learning_day.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.learning_day.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.learning_day.user_curriculum_id IS '소속 사용자 과정';
COMMENT ON COLUMN daily_career.learning_day.local_date IS '과정 타임존 기준 날짜';
COMMENT ON COLUMN daily_career.learning_day.day_type IS '허용값: STUDY, REST';
COMMENT ON COLUMN daily_career.learning_day.source IS '허용값: POLICY, MANUAL';
COMMENT ON COLUMN daily_career.learning_day.schedule_policy_id IS '생성에 사용한 정책';
COMMENT ON COLUMN daily_career.learning_day.planned_minutes IS '해당 날짜 목표 분';
COMMENT ON COLUMN daily_career.learning_day.rest_reason IS '휴식 사유';
COMMENT ON COLUMN daily_career.learning_day.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.learning_day.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.learning_day.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.learning_day_item (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    curriculum_version_id bigint NOT NULL,
    curriculum_unit_id bigint,
    learning_day_id bigint NOT NULL,
    order_no integer NOT NULL,
    activity_type varchar(32) NOT NULL,
    content_version_id bigint,
    problem_version_id bigint,
    test_version_id bigint,
    content_status varchar(32) DEFAULT 'PENDING' NOT NULL,
    status varchar(32) DEFAULT 'NOT_STARTED' NOT NULL,
    required boolean DEFAULT true NOT NULL,
    completion_weight numeric(8,3) DEFAULT 1 NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_learning_day_item PRIMARY KEY (id),
    CONSTRAINT uq_learning_day_item_01 UNIQUE (user_curriculum_id, curriculum_unit_id),
    CONSTRAINT uq_learning_day_item_02 UNIQUE (id, user_id),
    CONSTRAINT ck_learning_day_item_01 CHECK (order_no > 0),
    CONSTRAINT ck_learning_day_item_02 CHECK (completion_weight > 0),
    CONSTRAINT ck_learning_day_item_03 CHECK (num_nonnulls(content_version_id, problem_version_id, test_version_id) <= 1),
    CONSTRAINT ck_learning_day_item_04 CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_learning_day_item_05 CHECK (content_status <> 'READY' OR num_nonnulls(content_version_id, problem_version_id, test_version_id) = 1),
    CONSTRAINT ck_learning_day_item_06 CHECK (revision >= 0),
    CONSTRAINT ck_learning_day_item_07 CHECK (activity_type IN ('LEARN', 'PRACTICE', 'REVIEW', 'INTERVIEW', 'TEST')),
    CONSTRAINT ck_learning_day_item_08 CHECK (content_status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT ck_learning_day_item_09 CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
);
COMMENT ON TABLE daily_career.learning_day_item IS '배정 항목 ID는 재배치해도 유지한다. 미시작 항목만 자동 이동한다. 콘텐츠 생성 상태와 학습 상태를 섞지 않는다. 같은 원본 ITEM은 과정당 한 번만 등록한다.';
COMMENT ON COLUMN daily_career.learning_day_item.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.learning_day_item.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.learning_day_item.user_curriculum_id IS '소속 사용자 과정';
COMMENT ON COLUMN daily_career.learning_day_item.curriculum_version_id IS '등록 버전; 복합 FK 검증용';
COMMENT ON COLUMN daily_career.learning_day_item.curriculum_unit_id IS '원본 ITEM; 보충 학습은 NULL';
COMMENT ON COLUMN daily_career.learning_day_item.learning_day_id IS '현재 배정된 달력 날짜';
COMMENT ON COLUMN daily_career.learning_day_item.order_no IS '날짜 내 표시 순서';
COMMENT ON COLUMN daily_career.learning_day_item.activity_type IS '허용값: LEARN, PRACTICE, REVIEW, INTERVIEW, TEST';
COMMENT ON COLUMN daily_career.learning_day_item.content_version_id IS '고정 콘텐츠 버전';
COMMENT ON COLUMN daily_career.learning_day_item.problem_version_id IS '고정 문제 버전';
COMMENT ON COLUMN daily_career.learning_day_item.test_version_id IS '고정 시험 버전';
COMMENT ON COLUMN daily_career.learning_day_item.content_status IS '허용값: PENDING, READY, FAILED';
COMMENT ON COLUMN daily_career.learning_day_item.status IS '허용값: NOT_STARTED, IN_PROGRESS, COMPLETED, SKIPPED';
COMMENT ON COLUMN daily_career.learning_day_item.required IS '커리큘럼 완료율 대상';
COMMENT ON COLUMN daily_career.learning_day_item.completion_weight IS '등록 시 고정 가중치';
COMMENT ON COLUMN daily_career.learning_day_item.started_at IS '최초 시작 시각';
COMMENT ON COLUMN daily_career.learning_day_item.completed_at IS '완료 시각';
COMMENT ON COLUMN daily_career.learning_day_item.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.learning_day_item.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.learning_day_item.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.schedule_change (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    from_revision bigint NOT NULL,
    to_revision bigint NOT NULL,
    reason_code varchar(40) NOT NULL,
    before_snapshot jsonb NOT NULL,
    after_snapshot jsonb NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_schedule_change PRIMARY KEY (id),
    CONSTRAINT uq_schedule_change_01 UNIQUE (user_curriculum_id, to_revision),
    CONSTRAINT uq_schedule_change_02 UNIQUE (id, user_id),
    CONSTRAINT ck_schedule_change_01 CHECK (from_revision >= 0),
    CONSTRAINT ck_schedule_change_02 CHECK (to_revision = from_revision + 1)
);
COMMENT ON TABLE daily_career.schedule_change IS '재배치 삭제 후 재생성으로 학습 이력을 끊지 않는다. 스냅샷은 변경된 범위만 저장하며 개인정보·토큰을 담지 않는다.';
COMMENT ON COLUMN daily_career.schedule_change.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.schedule_change.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.schedule_change.user_curriculum_id IS '변경 과정';
COMMENT ON COLUMN daily_career.schedule_change.from_revision IS '변경 전 버전';
COMMENT ON COLUMN daily_career.schedule_change.to_revision IS '변경 후 버전';
COMMENT ON COLUMN daily_career.schedule_change.reason_code IS '변경 사유 코드';
COMMENT ON COLUMN daily_career.schedule_change.before_snapshot IS '변경 날짜·항목 ID·예정일·정책의 변경 전 값';
COMMENT ON COLUMN daily_career.schedule_change.after_snapshot IS '변경 후 값';
COMMENT ON COLUMN daily_career.schedule_change.created_at IS '생성 시각';

CREATE TABLE daily_career.learning_session (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    learning_day_item_id bigint NOT NULL,
    activity_date date NOT NULL,
    started_at timestamptz NOT NULL,
    last_heartbeat_at timestamptz NOT NULL,
    ended_at timestamptz,
    active_seconds integer DEFAULT 0 NOT NULL,
    heartbeat_seq bigint DEFAULT 0 NOT NULL,
    end_reason varchar(32),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_learning_session PRIMARY KEY (id),
    CONSTRAINT uq_learning_session_01 UNIQUE (id, user_id),
    CONSTRAINT ck_learning_session_01 CHECK (active_seconds >= 0),
    CONSTRAINT ck_learning_session_02 CHECK (heartbeat_seq >= 0),
    CONSTRAINT ck_learning_session_03 CHECK (last_heartbeat_at >= started_at),
    CONSTRAINT ck_learning_session_04 CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_learning_session_05 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.learning_session IS '초기 제안: 사용자당 열린 세션 1개. 하루 경계를 넘으면 서버가 세션을 분할한다. 클라이언트 누적 시간을 그대로 신뢰하지 않는다.';
COMMENT ON COLUMN daily_career.learning_session.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.learning_session.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.learning_session.learning_day_item_id IS '배정 항목';
COMMENT ON COLUMN daily_career.learning_session.activity_date IS '활동 발생일; 과정 타임존 기준';
COMMENT ON COLUMN daily_career.learning_session.started_at IS '실제 시작 시각';
COMMENT ON COLUMN daily_career.learning_session.last_heartbeat_at IS '마지막 서버 수신';
COMMENT ON COLUMN daily_career.learning_session.ended_at IS '종료 시각';
COMMENT ON COLUMN daily_career.learning_session.active_seconds IS '검증된 활성 학습 초';
COMMENT ON COLUMN daily_career.learning_session.heartbeat_seq IS '중복 heartbeat 방지 순번';
COMMENT ON COLUMN daily_career.learning_session.end_reason IS 'COMPLETED/PAUSED/IDLE_TIMEOUT 등';
COMMENT ON COLUMN daily_career.learning_session.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.learning_session.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.learning_session.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.learning_content (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    owner_user_id bigint,
    subject_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_learning_content PRIMARY KEY (id),
    CONSTRAINT ck_learning_content_01 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.learning_content IS '개인 AI 생성 콘텐츠는 사용자 소유다. 공유 콘텐츠와 다른 사용자의 콘텐츠 접근을 서비스에서 구분한다.';
COMMENT ON COLUMN daily_career.learning_content.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.learning_content.owner_user_id IS 'NULL=공유 콘텐츠; 값 존재=개인 콘텐츠';
COMMENT ON COLUMN daily_career.learning_content.subject_id IS '주 분야';
COMMENT ON COLUMN daily_career.learning_content.title IS '제목';
COMMENT ON COLUMN daily_career.learning_content.enabled IS '신규 사용 허용';
COMMENT ON COLUMN daily_career.learning_content.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.learning_content.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.learning_content.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.learning_content_version (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    learning_content_id bigint NOT NULL,
    version_no integer NOT NULL,
    status varchar(32) DEFAULT 'DRAFT' NOT NULL,
    body_markdown text NOT NULL,
    source_references jsonb DEFAULT '[]'::jsonb NOT NULL,
    content_digest varchar(64) NOT NULL,
    ai_job_id bigint,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_learning_content_version PRIMARY KEY (id),
    CONSTRAINT uq_learning_content_version_01 UNIQUE (learning_content_id, version_no),
    CONSTRAINT ck_learning_content_version_01 CHECK (version_no > 0),
    CONSTRAINT ck_learning_content_version_02 CHECK (jsonb_typeof(source_references) = 'array'),
    CONSTRAINT ck_learning_content_version_03 CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_learning_content_version_04 CHECK (revision >= 0),
    CONSTRAINT ck_learning_content_version_05 CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);
COMMENT ON TABLE daily_career.learning_content_version IS '발행 후 본문은 불변. 새 버전을 발행해도 과거 항목은 이전 버전을 계속 참조한다. 생성 AI 작업과 소유자 일치는 서비스 검증한다.';
COMMENT ON COLUMN daily_career.learning_content_version.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.learning_content_version.learning_content_id IS '콘텐츠 원형';
COMMENT ON COLUMN daily_career.learning_content_version.version_no IS '버전';
COMMENT ON COLUMN daily_career.learning_content_version.status IS '허용값: DRAFT, PUBLISHED, RETIRED';
COMMENT ON COLUMN daily_career.learning_content_version.body_markdown IS '본문';
COMMENT ON COLUMN daily_career.learning_content_version.source_references IS '출처·검증일 등; URL 및 제목만';
COMMENT ON COLUMN daily_career.learning_content_version.content_digest IS '정규화 본문의 SHA-256';
COMMENT ON COLUMN daily_career.learning_content_version.ai_job_id IS '생성 AI 작업';
COMMENT ON COLUMN daily_career.learning_content_version.published_at IS '발행 시각';
COMMENT ON COLUMN daily_career.learning_content_version.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.learning_content_version.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.learning_content_version.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.problem (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    owner_user_id bigint,
    subject_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    parent_problem_id bigint,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_problem PRIMARY KEY (id),
    CONSTRAINT ck_problem_01 CHECK (parent_problem_id IS NULL OR parent_problem_id <> id),
    CONSTRAINT ck_problem_02 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.problem IS '면접과 꼬리질문도 문제로 관리한다. 부모 순환·소유자·주제의 일관성은 서비스 검증한다.';
COMMENT ON COLUMN daily_career.problem.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.problem.owner_user_id IS 'NULL=공유; 값 존재=개인';
COMMENT ON COLUMN daily_career.problem.subject_id IS '주 분야';
COMMENT ON COLUMN daily_career.problem.title IS '문제명';
COMMENT ON COLUMN daily_career.problem.parent_problem_id IS '면접 꼬리질문 원본';
COMMENT ON COLUMN daily_career.problem.enabled IS '출제 허용';
COMMENT ON COLUMN daily_career.problem.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.problem.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.problem.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.problem_version (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    problem_id bigint NOT NULL,
    version_no integer NOT NULL,
    status varchar(32) DEFAULT 'DRAFT' NOT NULL,
    question_type varchar(32) NOT NULL,
    difficulty smallint NOT NULL,
    prompt_markdown text NOT NULL,
    choices jsonb DEFAULT '[]'::jsonb NOT NULL,
    answer_key jsonb NOT NULL,
    rubric jsonb NOT NULL,
    explanation_markdown text NOT NULL,
    grading_rule_version varchar(64) NOT NULL,
    ai_job_id bigint,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_problem_version PRIMARY KEY (id),
    CONSTRAINT uq_problem_version_01 UNIQUE (problem_id, version_no),
    CONSTRAINT uq_problem_version_02 UNIQUE (id, problem_id),
    CONSTRAINT ck_problem_version_01 CHECK (version_no > 0),
    CONSTRAINT ck_problem_version_02 CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_problem_version_03 CHECK (jsonb_typeof(choices) = 'array'),
    CONSTRAINT ck_problem_version_04 CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_problem_version_05 CHECK (revision >= 0),
    CONSTRAINT ck_problem_version_06 CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_problem_version_07 CHECK (question_type IN ('SINGLE_CHOICE', 'MULTI_CHOICE', 'SHORT_ANSWER', 'ESSAY', 'CODE', 'INTERVIEW'))
);
COMMENT ON TABLE daily_career.problem_version IS '보기 key 중복·정답 key 존재·문제유형별 JSON 스키마를 발행 시 검증한다. 보기 JSON은 불변 문서 구조에 한정하며 핵심 관계·조회 조건은 FK와 컬럼으로 둔다.';
COMMENT ON COLUMN daily_career.problem_version.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.problem_version.problem_id IS '문제 원형';
COMMENT ON COLUMN daily_career.problem_version.version_no IS '버전';
COMMENT ON COLUMN daily_career.problem_version.status IS '허용값: DRAFT, PUBLISHED, RETIRED';
COMMENT ON COLUMN daily_career.problem_version.question_type IS '허용값: SINGLE_CHOICE, MULTI_CHOICE, SHORT_ANSWER, ESSAY, CODE, INTERVIEW';
COMMENT ON COLUMN daily_career.problem_version.difficulty IS '난도 1~5';
COMMENT ON COLUMN daily_career.problem_version.prompt_markdown IS '지문';
COMMENT ON COLUMN daily_career.problem_version.choices IS '보기 배열 [{key,text}]; 객관식 이외는 빈 배열';
COMMENT ON COLUMN daily_career.problem_version.answer_key IS '정답; 서버 전용';
COMMENT ON COLUMN daily_career.problem_version.rubric IS '채점 기준; 서버 전용';
COMMENT ON COLUMN daily_career.problem_version.explanation_markdown IS '해설; 공개 시점 제한';
COMMENT ON COLUMN daily_career.problem_version.grading_rule_version IS '평가 규칙 버전';
COMMENT ON COLUMN daily_career.problem_version.ai_job_id IS '생성 AI 작업';
COMMENT ON COLUMN daily_career.problem_version.published_at IS '발행 시각';
COMMENT ON COLUMN daily_career.problem_version.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.problem_version.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.problem_version.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.problem_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    problem_version_id bigint NOT NULL,
    learning_session_id bigint,
    context_type varchar(32) DEFAULT 'PRACTICE' NOT NULL,
    status varchar(32) DEFAULT 'DRAFT' NOT NULL,
    answer_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    submitted_at timestamptz,
    active_seconds integer DEFAULT 0 NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_problem_attempt PRIMARY KEY (id),
    CONSTRAINT uq_problem_attempt_01 UNIQUE (id, user_id),
    CONSTRAINT ck_problem_attempt_01 CHECK (active_seconds >= 0),
    CONSTRAINT ck_problem_attempt_02 CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL)),
    CONSTRAINT ck_problem_attempt_03 CHECK (revision >= 0),
    CONSTRAINT ck_problem_attempt_04 CHECK (context_type IN ('PRACTICE', 'REVIEW', 'INTERVIEW')),
    CONSTRAINT ck_problem_attempt_05 CHECK (status IN ('DRAFT', 'SUBMITTED'))
);
COMMENT ON TABLE daily_career.problem_attempt IS '재시도는 새 행. 제출 뒤 답안은 수정하지 않는다. 점수는 grading_result에서 조회하며 이 테이블에 중복 저장하지 않는다.';
COMMENT ON COLUMN daily_career.problem_attempt.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.problem_attempt.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.problem_attempt.user_curriculum_id IS '학습 과정';
COMMENT ON COLUMN daily_career.problem_attempt.problem_version_id IS '출제 당시 문제 버전';
COMMENT ON COLUMN daily_career.problem_attempt.learning_session_id IS '학습 세션';
COMMENT ON COLUMN daily_career.problem_attempt.context_type IS '허용값: PRACTICE, REVIEW, INTERVIEW';
COMMENT ON COLUMN daily_career.problem_attempt.status IS '허용값: DRAFT, SUBMITTED';
COMMENT ON COLUMN daily_career.problem_attempt.answer_payload IS '답안 JSON; 유형별 스키마 검증';
COMMENT ON COLUMN daily_career.problem_attempt.submitted_at IS '최초 제출 시각';
COMMENT ON COLUMN daily_career.problem_attempt.active_seconds IS '해당 문제 풀이 초';
COMMENT ON COLUMN daily_career.problem_attempt.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.problem_attempt.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.problem_attempt.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.wrong_answer (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    problem_id bigint NOT NULL,
    status varchar(32) DEFAULT 'OPEN' NOT NULL,
    user_note text,
    first_wrong_at timestamptz NOT NULL,
    mastered_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_wrong_answer PRIMARY KEY (id),
    CONSTRAINT uq_wrong_answer_01 UNIQUE (user_id, problem_id),
    CONSTRAINT uq_wrong_answer_02 UNIQUE (id, user_id),
    CONSTRAINT ck_wrong_answer_01 CHECK (revision >= 0),
    CONSTRAINT ck_wrong_answer_02 CHECK (status IN ('OPEN', 'MASTERED', 'ARCHIVED'))
);
COMMENT ON TABLE daily_career.wrong_answer IS '같은 문제의 오답을 노트 하나로 모으되 발생 이력은 별도 보존한다. 재채점으로 정답이 된 발생 건은 무효 상태로 전환한다.';
COMMENT ON COLUMN daily_career.wrong_answer.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.wrong_answer.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.wrong_answer.problem_id IS '문제 원형';
COMMENT ON COLUMN daily_career.wrong_answer.status IS '허용값: OPEN, MASTERED, ARCHIVED';
COMMENT ON COLUMN daily_career.wrong_answer.user_note IS '사용자 직접 작성 노트';
COMMENT ON COLUMN daily_career.wrong_answer.first_wrong_at IS '최초 오답 시각';
COMMENT ON COLUMN daily_career.wrong_answer.mastered_at IS '숙달 처리 시각';
COMMENT ON COLUMN daily_career.wrong_answer.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.wrong_answer.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.wrong_answer.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.wrong_answer_occurrence (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    wrong_answer_id bigint NOT NULL,
    grading_result_id bigint NOT NULL,
    status varchar(32) DEFAULT 'VALID' NOT NULL,
    error_type varchar(40),
    analysis_markdown text,
    invalidated_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_wrong_answer_occurrence PRIMARY KEY (id),
    CONSTRAINT uq_wrong_answer_occurrence_01 UNIQUE (grading_result_id),
    CONSTRAINT uq_wrong_answer_occurrence_02 UNIQUE (id, user_id),
    CONSTRAINT ck_wrong_answer_occurrence_01 CHECK ((status = 'INVALIDATED') = (invalidated_at IS NOT NULL)),
    CONSTRAINT ck_wrong_answer_occurrence_02 CHECK (revision >= 0),
    CONSTRAINT ck_wrong_answer_occurrence_03 CHECK (status IN ('VALID', 'INVALIDATED'))
);
COMMENT ON TABLE daily_career.wrong_answer_occurrence IS '노트 문제와 채점 결과 문제의 동일성, 점수가 오답 기준에 해당하는지는 서비스에서 검증한다.';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.wrong_answer_id IS '오답 노트';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.grading_result_id IS '오답 판정의 근거 채점 결과';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.status IS '허용값: VALID, INVALIDATED';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.error_type IS '개념/실수/시간/설명부족 등';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.analysis_markdown IS '심층 설명·응용·실무 예시';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.invalidated_at IS '재채점으로 무효화한 시각';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.wrong_answer_occurrence.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.review_task (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    wrong_answer_id bigint NOT NULL,
    due_at timestamptz NOT NULL,
    status varchar(32) DEFAULT 'PLANNED' NOT NULL,
    algorithm_version varchar(64) NOT NULL,
    interval_days integer NOT NULL,
    completed_attempt_id bigint,
    completed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_review_task PRIMARY KEY (id),
    CONSTRAINT uq_review_task_01 UNIQUE (id, user_id),
    CONSTRAINT ck_review_task_01 CHECK (interval_days >= 0),
    CONSTRAINT ck_review_task_02 CHECK ((status = 'COMPLETED') = (completed_attempt_id IS NOT NULL AND completed_at IS NOT NULL)),
    CONSTRAINT ck_review_task_03 CHECK (revision >= 0),
    CONSTRAINT ck_review_task_04 CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELED'))
);
COMMENT ON TABLE daily_career.review_task IS '복습 완료 시 기존 예약을 완료하고 다음 예약은 새 행으로 생성한다. 초기 복습 간격은 정책 제안이며 코드에 고정하지 않는다.';
COMMENT ON COLUMN daily_career.review_task.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.review_task.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.review_task.wrong_answer_id IS '복습할 오답';
COMMENT ON COLUMN daily_career.review_task.due_at IS '복습 예정 시각';
COMMENT ON COLUMN daily_career.review_task.status IS '허용값: PLANNED, COMPLETED, CANCELED';
COMMENT ON COLUMN daily_career.review_task.algorithm_version IS '복습 간격 규칙 버전';
COMMENT ON COLUMN daily_career.review_task.interval_days IS '이번 간격 일수';
COMMENT ON COLUMN daily_career.review_task.completed_attempt_id IS '복습 완료의 문제 풀이';
COMMENT ON COLUMN daily_career.review_task.completed_at IS '완료 시각';
COMMENT ON COLUMN daily_career.review_task.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.review_task.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.review_task.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.test (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    owner_user_id bigint,
    title varchar(200) NOT NULL,
    test_type varchar(32) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_test PRIMARY KEY (id),
    CONSTRAINT ck_test_01 CHECK (revision >= 0),
    CONSTRAINT ck_test_02 CHECK (test_type IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);
COMMENT ON TABLE daily_career.test IS '';
COMMENT ON COLUMN daily_career.test.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.test.owner_user_id IS 'NULL=공유 시험; 개인 AI 시험은 사용자 소유';
COMMENT ON COLUMN daily_career.test.title IS '시험명';
COMMENT ON COLUMN daily_career.test.test_type IS '허용값: DAILY, WEEKLY, MONTHLY';
COMMENT ON COLUMN daily_career.test.enabled IS '신규 응시 허용';
COMMENT ON COLUMN daily_career.test.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.test.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.test.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.test_version (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    test_id bigint NOT NULL,
    version_no integer NOT NULL,
    status varchar(32) DEFAULT 'DRAFT' NOT NULL,
    time_limit_seconds integer NOT NULL,
    attempt_limit smallint,
    pass_percent numeric(5,2) NOT NULL,
    rule_version varchar(64) NOT NULL,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_test_version PRIMARY KEY (id),
    CONSTRAINT uq_test_version_01 UNIQUE (test_id, version_no),
    CONSTRAINT ck_test_version_01 CHECK (version_no > 0),
    CONSTRAINT ck_test_version_02 CHECK (time_limit_seconds > 0),
    CONSTRAINT ck_test_version_03 CHECK (attempt_limit IS NULL OR attempt_limit > 0),
    CONSTRAINT ck_test_version_04 CHECK (pass_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_test_version_05 CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_test_version_06 CHECK (revision >= 0),
    CONSTRAINT ck_test_version_07 CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);
COMMENT ON TABLE daily_career.test_version IS '일간·주간·월간 문항 수·시간·합격선은 정책 데이터. 확정되지 않은 숫자를 DDL 기본값으로 임의 고정하지 않았다.';
COMMENT ON COLUMN daily_career.test_version.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.test_version.test_id IS '시험 원형';
COMMENT ON COLUMN daily_career.test_version.version_no IS '버전';
COMMENT ON COLUMN daily_career.test_version.status IS '허용값: DRAFT, PUBLISHED, RETIRED';
COMMENT ON COLUMN daily_career.test_version.time_limit_seconds IS '제한 시간 초';
COMMENT ON COLUMN daily_career.test_version.attempt_limit IS '허용 횟수; NULL=제한 없음';
COMMENT ON COLUMN daily_career.test_version.pass_percent IS '합격 기준 백분율';
COMMENT ON COLUMN daily_career.test_version.rule_version IS '출제·합격 규칙 버전';
COMMENT ON COLUMN daily_career.test_version.published_at IS '발행 시각';
COMMENT ON COLUMN daily_career.test_version.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.test_version.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.test_version.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.test_item (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    test_version_id bigint NOT NULL,
    problem_id bigint NOT NULL,
    problem_version_id bigint NOT NULL,
    item_no integer NOT NULL,
    max_score numeric(8,3) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_test_item PRIMARY KEY (id),
    CONSTRAINT uq_test_item_01 UNIQUE (test_version_id, item_no),
    CONSTRAINT uq_test_item_02 UNIQUE (test_version_id, problem_id),
    CONSTRAINT uq_test_item_03 UNIQUE (id, test_version_id),
    CONSTRAINT ck_test_item_01 CHECK (item_no > 0),
    CONSTRAINT ck_test_item_02 CHECK (max_score > 0),
    CONSTRAINT ck_test_item_03 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.test_item IS '같은 시험 버전에 같은 원형 문제를 중복 출제하지 않는다. 기존 /answers/{problemId} 경로와 모호하지 않게 대응한다.';
COMMENT ON COLUMN daily_career.test_item.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.test_item.test_version_id IS '시험 버전';
COMMENT ON COLUMN daily_career.test_item.problem_id IS '문제 원형; API problemId 대응';
COMMENT ON COLUMN daily_career.test_item.problem_version_id IS '고정 문제 버전';
COMMENT ON COLUMN daily_career.test_item.item_no IS '문항 순서';
COMMENT ON COLUMN daily_career.test_item.max_score IS '배점';
COMMENT ON COLUMN daily_career.test_item.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.test_item.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.test_item.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.test_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    test_version_id bigint NOT NULL,
    attempt_no integer NOT NULL,
    status varchar(32) DEFAULT 'IN_PROGRESS' NOT NULL,
    started_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    submitted_at timestamptz,
    submit_reason varchar(24),
    submission_digest varchar(64),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_test_attempt PRIMARY KEY (id),
    CONSTRAINT uq_test_attempt_01 UNIQUE (user_id, test_version_id, attempt_no),
    CONSTRAINT uq_test_attempt_02 UNIQUE (id, test_version_id, user_id),
    CONSTRAINT uq_test_attempt_03 UNIQUE (id, user_id),
    CONSTRAINT ck_test_attempt_01 CHECK (attempt_no > 0),
    CONSTRAINT ck_test_attempt_02 CHECK (deadline_at > started_at),
    CONSTRAINT ck_test_attempt_03 CHECK (status <> 'SUBMITTED' OR (submitted_at IS NOT NULL AND submission_digest IS NOT NULL)),
    CONSTRAINT ck_test_attempt_04 CHECK (revision >= 0),
    CONSTRAINT ck_test_attempt_05 CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'VOIDED'))
);
COMMENT ON TABLE daily_career.test_attempt IS '답안 저장과 제출 모두 이 행을 먼저 잠근다. 제출 중복은 최초 제출 결과를 반환한다. 채점 실패가 제출을 취소시키지 않는다. 동일 버전 응시 차수는 잠금 안에서 결정한다.';
COMMENT ON COLUMN daily_career.test_attempt.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.test_attempt.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.test_attempt.user_curriculum_id IS '응시 과정';
COMMENT ON COLUMN daily_career.test_attempt.test_version_id IS '응시 시험 버전';
COMMENT ON COLUMN daily_career.test_attempt.attempt_no IS '같은 사용자·시험 버전 내 응시 차수';
COMMENT ON COLUMN daily_career.test_attempt.status IS '허용값: IN_PROGRESS, SUBMITTED, VOIDED';
COMMENT ON COLUMN daily_career.test_attempt.started_at IS '서버 시작 시각';
COMMENT ON COLUMN daily_career.test_attempt.deadline_at IS '서버 마감 시각';
COMMENT ON COLUMN daily_career.test_attempt.submitted_at IS '최초 제출 시각';
COMMENT ON COLUMN daily_career.test_attempt.submit_reason IS 'USER/TIMEOUT 등';
COMMENT ON COLUMN daily_career.test_attempt.submission_digest IS '최종 답안 집합 해시';
COMMENT ON COLUMN daily_career.test_attempt.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.test_attempt.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.test_attempt.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.test_attempt_item (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    test_attempt_id bigint NOT NULL,
    test_version_id bigint NOT NULL,
    test_item_id bigint NOT NULL,
    display_order integer NOT NULL,
    question_snapshot jsonb NOT NULL,
    grading_snapshot jsonb NOT NULL,
    answer_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    answered_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_test_attempt_item PRIMARY KEY (id),
    CONSTRAINT uq_test_attempt_item_01 UNIQUE (test_attempt_id, test_item_id),
    CONSTRAINT uq_test_attempt_item_02 UNIQUE (test_attempt_id, display_order),
    CONSTRAINT uq_test_attempt_item_03 UNIQUE (id, test_attempt_id, user_id),
    CONSTRAINT uq_test_attempt_item_04 UNIQUE (id, user_id),
    CONSTRAINT ck_test_attempt_item_01 CHECK (display_order > 0),
    CONSTRAINT ck_test_attempt_item_02 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.test_attempt_item IS '시험 시작 시 문항 스냅샷을 만든다. 문항 수와 원본 구성 일치·배점 유효성은 시작 서비스가 검사한다. 제출 후 answer_payload는 불변이다.';
COMMENT ON COLUMN daily_career.test_attempt_item.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.test_attempt_item.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.test_attempt_item.test_attempt_id IS '시험 응시';
COMMENT ON COLUMN daily_career.test_attempt_item.test_version_id IS '시험 버전; 복합 FK용';
COMMENT ON COLUMN daily_career.test_attempt_item.test_item_id IS '원본 시험 문항';
COMMENT ON COLUMN daily_career.test_attempt_item.display_order IS '실제 노출 순서';
COMMENT ON COLUMN daily_career.test_attempt_item.question_snapshot IS '노출한 지문·보기·문제 버전·스키마 버전; 정답 제외';
COMMENT ON COLUMN daily_career.test_attempt_item.grading_snapshot IS '배점·정답·루브릭; 서버 전용';
COMMENT ON COLUMN daily_career.test_attempt_item.answer_payload IS '자동 저장 답안';
COMMENT ON COLUMN daily_career.test_attempt_item.answered_at IS '최종 답안 저장 시각';
COMMENT ON COLUMN daily_career.test_attempt_item.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.test_attempt_item.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.test_attempt_item.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.grading_run (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    problem_attempt_id bigint,
    test_attempt_id bigint,
    run_no integer NOT NULL,
    status varchar(32) DEFAULT 'QUEUED' NOT NULL,
    grader_type varchar(32) NOT NULL,
    rule_version varchar(64) NOT NULL,
    input_digest varchar(64) NOT NULL,
    is_current boolean DEFAULT false NOT NULL,
    ai_job_id bigint,
    finished_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_grading_run PRIMARY KEY (id),
    CONSTRAINT uq_grading_run_01 UNIQUE (problem_attempt_id, run_no),
    CONSTRAINT uq_grading_run_02 UNIQUE (test_attempt_id, run_no),
    CONSTRAINT uq_grading_run_03 UNIQUE (id, problem_attempt_id, user_id),
    CONSTRAINT uq_grading_run_04 UNIQUE (id, test_attempt_id, user_id),
    CONSTRAINT uq_grading_run_05 UNIQUE (id, user_id),
    CONSTRAINT ck_grading_run_01 CHECK (num_nonnulls(problem_attempt_id, test_attempt_id) = 1),
    CONSTRAINT ck_grading_run_02 CHECK (run_no > 0),
    CONSTRAINT ck_grading_run_03 CHECK (NOT is_current OR status = 'SUCCEEDED'),
    CONSTRAINT ck_grading_run_04 CHECK (revision >= 0),
    CONSTRAINT ck_grading_run_05 CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_grading_run_06 CHECK (grader_type IN ('RULE', 'AI', 'HUMAN'))
);
COMMENT ON TABLE daily_career.grading_run IS '재채점은 새 run이다. 성공 결과가 완성된 트랜잭션에서만 공식 run을 교체한다. 실패한 재채점 때문에 기존 성적을 숨기지 않는다.';
COMMENT ON COLUMN daily_career.grading_run.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.grading_run.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.grading_run.problem_attempt_id IS '개별 풀이 채점 대상';
COMMENT ON COLUMN daily_career.grading_run.test_attempt_id IS '시험 채점 대상';
COMMENT ON COLUMN daily_career.grading_run.run_no IS '대상별 채점 차수';
COMMENT ON COLUMN daily_career.grading_run.status IS '허용값: QUEUED, RUNNING, SUCCEEDED, FAILED';
COMMENT ON COLUMN daily_career.grading_run.grader_type IS '허용값: RULE, AI, HUMAN';
COMMENT ON COLUMN daily_career.grading_run.rule_version IS '채점 규칙 버전';
COMMENT ON COLUMN daily_career.grading_run.input_digest IS '입력 답안·문항 버전 해시';
COMMENT ON COLUMN daily_career.grading_run.is_current IS '현재 공식 성적 여부';
COMMENT ON COLUMN daily_career.grading_run.ai_job_id IS 'AI 작업';
COMMENT ON COLUMN daily_career.grading_run.finished_at IS '종료 시각';
COMMENT ON COLUMN daily_career.grading_run.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.grading_run.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.grading_run.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.grading_result (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    grading_run_id bigint NOT NULL,
    problem_attempt_id bigint,
    test_attempt_id bigint,
    test_attempt_item_id bigint,
    earned_score numeric(8,3) NOT NULL,
    max_score numeric(8,3) NOT NULL,
    is_correct boolean,
    rubric_result jsonb NOT NULL,
    feedback_markdown text NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_grading_result PRIMARY KEY (id),
    CONSTRAINT uq_grading_result_01 UNIQUE (grading_run_id, problem_attempt_id),
    CONSTRAINT uq_grading_result_02 UNIQUE (grading_run_id, test_attempt_item_id),
    CONSTRAINT uq_grading_result_03 UNIQUE (id, user_id),
    CONSTRAINT ck_grading_result_01 CHECK ((problem_attempt_id IS NOT NULL AND test_attempt_id IS NULL AND test_attempt_item_id IS NULL) OR (problem_attempt_id IS NULL AND test_attempt_id IS NOT NULL AND test_attempt_item_id IS NOT NULL)),
    CONSTRAINT ck_grading_result_02 CHECK (max_score > 0),
    CONSTRAINT ck_grading_result_03 CHECK (earned_score BETWEEN 0 AND max_score)
);
COMMENT ON TABLE daily_career.grading_result IS '실행 대상과 문항 소속은 복합 FK로 연결한다. 공식 점수는 is_current인 성공 run의 결과 합계다. 채점 결과 원문을 갱신하지 않는다.';
COMMENT ON COLUMN daily_career.grading_result.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.grading_result.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.grading_result.grading_run_id IS '채점 실행';
COMMENT ON COLUMN daily_career.grading_result.problem_attempt_id IS '개별 풀이인 경우';
COMMENT ON COLUMN daily_career.grading_result.test_attempt_id IS '시험인 경우';
COMMENT ON COLUMN daily_career.grading_result.test_attempt_item_id IS '시험 문항인 경우';
COMMENT ON COLUMN daily_career.grading_result.earned_score IS '획득 점수';
COMMENT ON COLUMN daily_career.grading_result.max_score IS '만점';
COMMENT ON COLUMN daily_career.grading_result.is_correct IS '객관식 정오; 서술형은 NULL 가능';
COMMENT ON COLUMN daily_career.grading_result.rubric_result IS '평가 항목별 점수·근거';
COMMENT ON COLUMN daily_career.grading_result.feedback_markdown IS '피드백';
COMMENT ON COLUMN daily_career.grading_result.created_at IS '생성 시각';

CREATE TABLE daily_career.monthly_evaluation (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint NOT NULL,
    month_start date NOT NULL,
    report_version integer NOT NULL,
    source_cutoff_at timestamptz NOT NULL,
    calculation_version varchar(64) NOT NULL,
    status varchar(32) DEFAULT 'GENERATING' NOT NULL,
    metrics_snapshot jsonb NOT NULL,
    report_payload jsonb,
    ai_job_id bigint,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_monthly_evaluation PRIMARY KEY (id),
    CONSTRAINT uq_monthly_evaluation_01 UNIQUE (user_curriculum_id, month_start, report_version),
    CONSTRAINT uq_monthly_evaluation_02 UNIQUE (id, user_id),
    CONSTRAINT ck_monthly_evaluation_01 CHECK (extract(day FROM month_start) = 1),
    CONSTRAINT ck_monthly_evaluation_02 CHECK (report_version > 0),
    CONSTRAINT ck_monthly_evaluation_03 CHECK (status <> 'READY' OR (report_payload IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT ck_monthly_evaluation_04 CHECK (revision >= 0),
    CONSTRAINT ck_monthly_evaluation_05 CHECK (status IN ('GENERATING', 'READY', 'FAILED'))
);
COMMENT ON TABLE daily_career.monthly_evaluation IS '월말 평가는 달력 월 기준 제안. 과정 중간 시작/종료 월은 교집합만 평가한다. READY 결과 변경은 새 report_version으로 발행한다.';
COMMENT ON COLUMN daily_career.monthly_evaluation.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.monthly_evaluation.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.monthly_evaluation.user_curriculum_id IS '평가 과정';
COMMENT ON COLUMN daily_career.monthly_evaluation.month_start IS '보고 대상 달의 1일';
COMMENT ON COLUMN daily_career.monthly_evaluation.report_version IS '보고서 버전';
COMMENT ON COLUMN daily_career.monthly_evaluation.source_cutoff_at IS '원천 집계 기준 시각';
COMMENT ON COLUMN daily_career.monthly_evaluation.calculation_version IS '지표 산정 규칙 버전';
COMMENT ON COLUMN daily_career.monthly_evaluation.status IS '허용값: GENERATING, READY, FAILED';
COMMENT ON COLUMN daily_career.monthly_evaluation.metrics_snapshot IS '지표 분자·분모·대상 ID·기간·가중치 스냅샷';
COMMENT ON COLUMN daily_career.monthly_evaluation.report_payload IS '강점/취약점/다음 달 계획';
COMMENT ON COLUMN daily_career.monthly_evaluation.ai_job_id IS '보고서 생성 AI 작업';
COMMENT ON COLUMN daily_career.monthly_evaluation.published_at IS '평가 공개 시각';
COMMENT ON COLUMN daily_career.monthly_evaluation.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.monthly_evaluation.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.monthly_evaluation.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.ai_conversation (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    user_curriculum_id bigint,
    purpose varchar(40) NOT NULL,
    title varchar(200),
    status varchar(32) DEFAULT 'OPEN' NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ai_conversation PRIMARY KEY (id),
    CONSTRAINT uq_ai_conversation_01 UNIQUE (id, user_id),
    CONSTRAINT ck_ai_conversation_01 CHECK (revision >= 0),
    CONSTRAINT ck_ai_conversation_02 CHECK (status IN ('OPEN', 'CLOSED'))
);
COMMENT ON TABLE daily_career.ai_conversation IS '';
COMMENT ON COLUMN daily_career.ai_conversation.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.ai_conversation.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.ai_conversation.user_curriculum_id IS '관련 과정';
COMMENT ON COLUMN daily_career.ai_conversation.purpose IS 'TUTOR/WRONG_ANSWER/INTERVIEW 등';
COMMENT ON COLUMN daily_career.ai_conversation.title IS '대화 제목';
COMMENT ON COLUMN daily_career.ai_conversation.status IS '허용값: OPEN, CLOSED';
COMMENT ON COLUMN daily_career.ai_conversation.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.ai_conversation.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.ai_conversation.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.ai_message (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    message_no integer NOT NULL,
    role varchar(32) NOT NULL,
    body_markdown text NOT NULL,
    ai_job_id bigint,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_ai_message PRIMARY KEY (id),
    CONSTRAINT uq_ai_message_01 UNIQUE (conversation_id, message_no),
    CONSTRAINT uq_ai_message_02 UNIQUE (id, user_id),
    CONSTRAINT ck_ai_message_01 CHECK (message_no > 0),
    CONSTRAINT ck_ai_message_02 CHECK (role IN ('USER', 'ASSISTANT'))
);
COMMENT ON TABLE daily_career.ai_message IS '시스템 프롬프트·자격증명을 화면 대화 이력에 저장하지 않는다.';
COMMENT ON COLUMN daily_career.ai_message.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.ai_message.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.ai_message.conversation_id IS '대화';
COMMENT ON COLUMN daily_career.ai_message.message_no IS '대화 내 순번';
COMMENT ON COLUMN daily_career.ai_message.role IS '허용값: USER, ASSISTANT';
COMMENT ON COLUMN daily_career.ai_message.body_markdown IS '화면 표시 대화 내용';
COMMENT ON COLUMN daily_career.ai_message.ai_job_id IS '응답 생성 작업';
COMMENT ON COLUMN daily_career.ai_message.created_at IS '생성 시각';

CREATE TABLE daily_career.ai_budget (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    currency varchar(3) DEFAULT 'KRW' NOT NULL,
    limit_amount numeric(18,6) NOT NULL,
    hard_limit_enabled boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ai_budget PRIMARY KEY (id),
    CONSTRAINT uq_ai_budget_01 UNIQUE (user_id, period_start, period_end),
    CONSTRAINT uq_ai_budget_02 UNIQUE (id, user_id),
    CONSTRAINT ck_ai_budget_01 CHECK (period_end > period_start),
    CONSTRAINT ck_ai_budget_02 CHECK (limit_amount >= 0),
    CONSTRAINT ck_ai_budget_03 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.ai_budget IS '기간 겹침 금지는 사용자 잠금 아래 검증한다. 예산 차감은 실사용액+미정산 예약액을 계산한다. 과거 논의한 6개월 10만원은 사용자 설정값으로 입력하며 DDL 고정값이 아니다.';
COMMENT ON COLUMN daily_career.ai_budget.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.ai_budget.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.ai_budget.period_start IS '예산 적용 시작일';
COMMENT ON COLUMN daily_career.ai_budget.period_end IS '종료일; 끝 날짜 제외';
COMMENT ON COLUMN daily_career.ai_budget.currency IS '예산 통화 코드';
COMMENT ON COLUMN daily_career.ai_budget.limit_amount IS '사용자 설정 예산 한도';
COMMENT ON COLUMN daily_career.ai_budget.hard_limit_enabled IS '한도 도달 시 신규 요청 차단';
COMMENT ON COLUMN daily_career.ai_budget.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.ai_budget.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.ai_budget.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.ai_job (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    budget_id bigint NOT NULL,
    conversation_id bigint,
    job_type varchar(40) NOT NULL,
    dedupe_key varchar(160) NOT NULL,
    status varchar(32) DEFAULT 'QUEUED' NOT NULL,
    provider varchar(40) NOT NULL,
    model varchar(128) NOT NULL,
    prompt_version varchar(64) NOT NULL,
    input_snapshot jsonb NOT NULL,
    result_payload jsonb,
    reserved_amount numeric(18,6) DEFAULT 0 NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz,
    lease_token varchar(64),
    lease_until timestamptz,
    finished_at timestamptz,
    error_code varchar(64),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ai_job PRIMARY KEY (id),
    CONSTRAINT uq_ai_job_01 UNIQUE (user_id, dedupe_key),
    CONSTRAINT uq_ai_job_02 UNIQUE (id, user_id),
    CONSTRAINT ck_ai_job_01 CHECK (reserved_amount >= 0),
    CONSTRAINT ck_ai_job_02 CHECK (attempt_count >= 0),
    CONSTRAINT ck_ai_job_03 CHECK (revision >= 0),
    CONSTRAINT ck_ai_job_04 CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'UNKNOWN'))
);
COMMENT ON TABLE daily_career.ai_job IS 'UNKNOWN은 외부 처리/과금 여부 미확인. 무조건 재시도하지 않는다. 예산 행을 잠그고 예약하며, 작업 재시도마다 ai_usage를 남긴다.';
COMMENT ON COLUMN daily_career.ai_job.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.ai_job.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.ai_job.budget_id IS '적용 예산';
COMMENT ON COLUMN daily_career.ai_job.conversation_id IS '관련 대화';
COMMENT ON COLUMN daily_career.ai_job.job_type IS 'CONTENT/EXPLANATION/GRADING/INTERVIEW/REPORT';
COMMENT ON COLUMN daily_career.ai_job.dedupe_key IS '같은 목적의 논리 작업 키';
COMMENT ON COLUMN daily_career.ai_job.status IS '허용값: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELED, UNKNOWN';
COMMENT ON COLUMN daily_career.ai_job.provider IS 'AI 제공자';
COMMENT ON COLUMN daily_career.ai_job.model IS '실제 요청 모델 식별자';
COMMENT ON COLUMN daily_career.ai_job.prompt_version IS '프롬프트 버전';
COMMENT ON COLUMN daily_career.ai_job.input_snapshot IS '허용된 학습 입력만; 자격증명 제외';
COMMENT ON COLUMN daily_career.ai_job.result_payload IS '정규화된 작업 결과';
COMMENT ON COLUMN daily_career.ai_job.reserved_amount IS '아직 정산하지 않은 예약액; 예산 통화 기준';
COMMENT ON COLUMN daily_career.ai_job.attempt_count IS '물리 요청 횟수';
COMMENT ON COLUMN daily_career.ai_job.next_attempt_at IS '다음 시도 시각';
COMMENT ON COLUMN daily_career.ai_job.lease_token IS '작업 점유 토큰';
COMMENT ON COLUMN daily_career.ai_job.lease_until IS '점유 만료';
COMMENT ON COLUMN daily_career.ai_job.finished_at IS '완료 시각';
COMMENT ON COLUMN daily_career.ai_job.error_code IS '정규화 오류 코드';
COMMENT ON COLUMN daily_career.ai_job.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.ai_job.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.ai_job.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.ai_usage (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    ai_job_id bigint NOT NULL,
    attempt_no integer NOT NULL,
    provider varchar(40) NOT NULL,
    provider_request_id varchar(200),
    status varchar(32) DEFAULT 'PENDING' NOT NULL,
    input_tokens bigint,
    cached_input_tokens bigint,
    output_tokens bigint,
    reasoning_tokens bigint,
    provider_cost_usd numeric(18,8),
    budget_cost_amount numeric(18,6),
    fx_rate numeric(18,8),
    pricing_snapshot jsonb,
    reconciled_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ai_usage PRIMARY KEY (id),
    CONSTRAINT uq_ai_usage_01 UNIQUE (ai_job_id, attempt_no),
    CONSTRAINT uq_ai_usage_02 UNIQUE (id, user_id),
    CONSTRAINT ck_ai_usage_01 CHECK (attempt_no > 0),
    CONSTRAINT ck_ai_usage_02 CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_ai_usage_03 CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_ai_usage_04 CHECK (cached_input_tokens IS NULL OR (input_tokens IS NOT NULL AND cached_input_tokens BETWEEN 0 AND input_tokens)),
    CONSTRAINT ck_ai_usage_05 CHECK (reasoning_tokens IS NULL OR (output_tokens IS NOT NULL AND reasoning_tokens BETWEEN 0 AND output_tokens)),
    CONSTRAINT ck_ai_usage_06 CHECK (provider_cost_usd IS NULL OR provider_cost_usd >= 0),
    CONSTRAINT ck_ai_usage_07 CHECK (budget_cost_amount IS NULL OR budget_cost_amount >= 0),
    CONSTRAINT ck_ai_usage_08 CHECK (fx_rate IS NULL OR fx_rate > 0),
    CONSTRAINT ck_ai_usage_09 CHECK (status <> 'KNOWN' OR (input_tokens IS NOT NULL AND output_tokens IS NOT NULL AND budget_cost_amount IS NOT NULL AND pricing_snapshot IS NOT NULL AND reconciled_at IS NOT NULL)),
    CONSTRAINT ck_ai_usage_10 CHECK (revision >= 0),
    CONSTRAINT ck_ai_usage_11 CHECK (status IN ('PENDING', 'KNOWN', 'UNAVAILABLE'))
);
COMMENT ON TABLE daily_career.ai_usage IS '캐시/추론 토큰을 전체 토큰에 중복 합산하지 않는다. 미확인 비용은 0으로 계산하지 않고 예약액을 유지한다. 정산 후 값 변경은 감사 기록을 남긴다.';
COMMENT ON COLUMN daily_career.ai_usage.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.ai_usage.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.ai_usage.ai_job_id IS '논리 AI 작업';
COMMENT ON COLUMN daily_career.ai_usage.attempt_no IS '물리 호출 차수';
COMMENT ON COLUMN daily_career.ai_usage.provider IS '제공자';
COMMENT ON COLUMN daily_career.ai_usage.provider_request_id IS '제공자 요청 식별자';
COMMENT ON COLUMN daily_career.ai_usage.status IS '허용값: PENDING, KNOWN, UNAVAILABLE';
COMMENT ON COLUMN daily_career.ai_usage.input_tokens IS '전체 입력 토큰';
COMMENT ON COLUMN daily_career.ai_usage.cached_input_tokens IS '입력 토큰의 캐시 부분집합';
COMMENT ON COLUMN daily_career.ai_usage.output_tokens IS '전체 출력 토큰';
COMMENT ON COLUMN daily_career.ai_usage.reasoning_tokens IS '출력 토큰의 추론 부분집합';
COMMENT ON COLUMN daily_career.ai_usage.provider_cost_usd IS '제공자 기준 비용; 미확인은 NULL';
COMMENT ON COLUMN daily_career.ai_usage.budget_cost_amount IS '고정 환산한 예산 통화 비용';
COMMENT ON COLUMN daily_career.ai_usage.fx_rate IS 'USD→예산통화 환산율';
COMMENT ON COLUMN daily_career.ai_usage.pricing_snapshot IS '실제 적용 단가·계산 기준·확인일';
COMMENT ON COLUMN daily_career.ai_usage.reconciled_at IS '사용량 정산 시각';
COMMENT ON COLUMN daily_career.ai_usage.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.ai_usage.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.ai_usage.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.notification_preference (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    channel varchar(32) NOT NULL,
    event_type varchar(40) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    local_time time,
    send_on_rest_day boolean DEFAULT false NOT NULL,
    quiet_start time,
    quiet_end time,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_notification_preference PRIMARY KEY (id),
    CONSTRAINT uq_notification_preference_01 UNIQUE (user_id, channel, event_type),
    CONSTRAINT uq_notification_preference_02 UNIQUE (id, user_id),
    CONSTRAINT ck_notification_preference_01 CHECK ((quiet_start IS NULL) = (quiet_end IS NULL)),
    CONSTRAINT ck_notification_preference_02 CHECK (revision >= 0),
    CONSTRAINT ck_notification_preference_03 CHECK (channel IN ('IN_APP', 'WEB_PUSH', 'SLACK'))
);
COMMENT ON TABLE daily_career.notification_preference IS '자정을 넘는 방해 금지 구간은 서비스에서 해석한다. 웹 푸시/Slack은 명시적 활성화 전까지 발송하지 않는다.';
COMMENT ON COLUMN daily_career.notification_preference.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.notification_preference.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.notification_preference.channel IS '허용값: IN_APP, WEB_PUSH, SLACK';
COMMENT ON COLUMN daily_career.notification_preference.event_type IS 'DAILY/REVIEW/TEST/MONTHLY 등';
COMMENT ON COLUMN daily_career.notification_preference.enabled IS '수신 허용';
COMMENT ON COLUMN daily_career.notification_preference.local_time IS '정기 발송 지역 시간';
COMMENT ON COLUMN daily_career.notification_preference.send_on_rest_day IS '휴식일 발송 허용';
COMMENT ON COLUMN daily_career.notification_preference.quiet_start IS '방해 금지 시작';
COMMENT ON COLUMN daily_career.notification_preference.quiet_end IS '방해 금지 종료';
COMMENT ON COLUMN daily_career.notification_preference.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.notification_preference.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.notification_preference.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.push_subscription (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    endpoint_hash varchar(64) NOT NULL,
    endpoint_ciphertext bytea NOT NULL,
    auth_ciphertext bytea NOT NULL,
    key_id varchar(100) NOT NULL,
    p256dh text NOT NULL,
    device_label varchar(100),
    status varchar(32) DEFAULT 'ACTIVE' NOT NULL,
    last_success_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_push_subscription PRIMARY KEY (id),
    CONSTRAINT uq_push_subscription_01 UNIQUE (endpoint_hash),
    CONSTRAINT uq_push_subscription_02 UNIQUE (id, user_id),
    CONSTRAINT ck_push_subscription_01 CHECK (revision >= 0),
    CONSTRAINT ck_push_subscription_02 CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);
COMMENT ON TABLE daily_career.push_subscription IS 'PWA 웹 푸시 대상. 네이티브 FCM/APNs 앱을 출시하는 경우 별도 디바이스 토큰 설계를 추가한다.';
COMMENT ON COLUMN daily_career.push_subscription.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.push_subscription.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.push_subscription.endpoint_hash IS 'endpoint 정규화 SHA-256; 로그 원문 금지';
COMMENT ON COLUMN daily_career.push_subscription.endpoint_ciphertext IS '암호화된 endpoint';
COMMENT ON COLUMN daily_career.push_subscription.auth_ciphertext IS '암호화된 auth secret';
COMMENT ON COLUMN daily_career.push_subscription.key_id IS '암호화 키 식별자; 실제 키는 외부 보관';
COMMENT ON COLUMN daily_career.push_subscription.p256dh IS '클라이언트 공개키';
COMMENT ON COLUMN daily_career.push_subscription.device_label IS '사용자 기기명';
COMMENT ON COLUMN daily_career.push_subscription.status IS '허용값: ACTIVE, REVOKED, EXPIRED';
COMMENT ON COLUMN daily_career.push_subscription.last_success_at IS '최근 제공자 수락 시각';
COMMENT ON COLUMN daily_career.push_subscription.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.push_subscription.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.push_subscription.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.notification (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    event_type varchar(40) NOT NULL,
    title varchar(200) NOT NULL,
    body text NOT NULL,
    deep_link varchar(500),
    dedupe_key varchar(160) NOT NULL,
    scheduled_at timestamptz NOT NULL,
    event_local_date date,
    timezone varchar(64) NOT NULL,
    read_at timestamptz,
    canceled_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT uq_notification_01 UNIQUE (user_id, dedupe_key),
    CONSTRAINT uq_notification_02 UNIQUE (id, user_id),
    CONSTRAINT ck_notification_01 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.notification IS '';
COMMENT ON COLUMN daily_career.notification.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.notification.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.notification.event_type IS '알림 유형';
COMMENT ON COLUMN daily_career.notification.title IS '제목';
COMMENT ON COLUMN daily_career.notification.body IS '본문';
COMMENT ON COLUMN daily_career.notification.deep_link IS '허용된 앱 내부 이동 경로';
COMMENT ON COLUMN daily_career.notification.dedupe_key IS '동일 이벤트 중복 생성 방지';
COMMENT ON COLUMN daily_career.notification.scheduled_at IS '발송 예정 시각';
COMMENT ON COLUMN daily_career.notification.event_local_date IS '알림 기준 지역 날짜';
COMMENT ON COLUMN daily_career.notification.timezone IS '날짜/시각 판정 타임존 스냅샷';
COMMENT ON COLUMN daily_career.notification.read_at IS '앱에서 읽은 시각';
COMMENT ON COLUMN daily_career.notification.canceled_at IS '일정 변경 등으로 취소';
COMMENT ON COLUMN daily_career.notification.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.notification.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.notification.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.notification_delivery (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    notification_id bigint NOT NULL,
    channel varchar(32) NOT NULL,
    push_subscription_id bigint,
    integration_connection_id bigint,
    status varchar(32) DEFAULT 'QUEUED' NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz,
    lease_token varchar(64),
    lease_until timestamptz,
    sent_at timestamptz,
    last_error_code varchar(64),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
    CONSTRAINT uq_notification_delivery_01 UNIQUE (id, user_id),
    CONSTRAINT ck_notification_delivery_01 CHECK ((channel = 'WEB_PUSH' AND push_subscription_id IS NOT NULL AND integration_connection_id IS NULL) OR (channel = 'SLACK' AND push_subscription_id IS NULL AND integration_connection_id IS NOT NULL)),
    CONSTRAINT ck_notification_delivery_02 CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_delivery_03 CHECK (revision >= 0),
    CONSTRAINT ck_notification_delivery_04 CHECK (channel IN ('WEB_PUSH', 'SLACK')),
    CONSTRAINT ck_notification_delivery_05 CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED', 'CANCELED', 'UNKNOWN'))
);
COMMENT ON TABLE daily_career.notification_delivery IS 'SENT는 실제 화면 도달 보장이 아니라 전송 제공자 수락을 뜻한다. 알림 재시도 중복 표시는 논리 이벤트 키와 클라이언트 tag 등으로 완화한다.';
COMMENT ON COLUMN daily_career.notification_delivery.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.notification_delivery.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.notification_delivery.notification_id IS '알림';
COMMENT ON COLUMN daily_career.notification_delivery.channel IS '허용값: WEB_PUSH, SLACK';
COMMENT ON COLUMN daily_career.notification_delivery.push_subscription_id IS '웹 푸시 대상';
COMMENT ON COLUMN daily_career.notification_delivery.integration_connection_id IS 'Slack 연결';
COMMENT ON COLUMN daily_career.notification_delivery.status IS '허용값: QUEUED, SENDING, SENT, FAILED, CANCELED, UNKNOWN';
COMMENT ON COLUMN daily_career.notification_delivery.attempt_count IS '발송 시도 수';
COMMENT ON COLUMN daily_career.notification_delivery.next_attempt_at IS '재시도 시각';
COMMENT ON COLUMN daily_career.notification_delivery.lease_token IS '점유 토큰';
COMMENT ON COLUMN daily_career.notification_delivery.lease_until IS '점유 만료';
COMMENT ON COLUMN daily_career.notification_delivery.sent_at IS '제공자가 요청을 수락한 시각';
COMMENT ON COLUMN daily_career.notification_delivery.last_error_code IS '마지막 오류';
COMMENT ON COLUMN daily_career.notification_delivery.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.notification_delivery.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.notification_delivery.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.integration_connection (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    provider varchar(32) NOT NULL,
    status varchar(32) DEFAULT 'ACTIVE' NOT NULL,
    credential_ref varchar(240),
    external_workspace_id varchar(200),
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    api_version varchar(40),
    disconnected_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_integration_connection PRIMARY KEY (id),
    CONSTRAINT uq_integration_connection_01 UNIQUE (id, user_id),
    CONSTRAINT ck_integration_connection_01 CHECK (revision >= 0),
    CONSTRAINT ck_integration_connection_02 CHECK (provider IN ('NOTION', 'SLACK')),
    CONSTRAINT ck_integration_connection_03 CHECK (status IN ('ACTIVE', 'DISCONNECTED', 'ERROR'))
);
COMMENT ON TABLE daily_career.integration_connection IS '앱 DB가 원본이며 Notion은 단방향 기록·복습용이다. 연결 해제 시 비밀 참조를 제거하고 대기 전송을 취소한다. config에 토큰/Webhook URL을 넣지 않는다.';
COMMENT ON COLUMN daily_career.integration_connection.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.integration_connection.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.integration_connection.provider IS '허용값: NOTION, SLACK';
COMMENT ON COLUMN daily_career.integration_connection.status IS '허용값: ACTIVE, DISCONNECTED, ERROR';
COMMENT ON COLUMN daily_career.integration_connection.credential_ref IS '외부 비밀 저장소 자격증명 참조';
COMMENT ON COLUMN daily_career.integration_connection.external_workspace_id IS '외부 워크스페이스';
COMMENT ON COLUMN daily_career.integration_connection.config IS '비민감 외부 DB/채널/템플릿 ID 및 선택 동기화 설정';
COMMENT ON COLUMN daily_career.integration_connection.api_version IS '연동 구현이 고정한 API 버전';
COMMENT ON COLUMN daily_career.integration_connection.disconnected_at IS '연결 해제 시각';
COMMENT ON COLUMN daily_career.integration_connection.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.integration_connection.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.integration_connection.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.integration_mapping (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    connection_id bigint NOT NULL,
    content_version_id bigint,
    problem_version_id bigint,
    problem_attempt_id bigint,
    wrong_answer_id bigint,
    test_attempt_id bigint,
    monthly_evaluation_id bigint,
    learning_session_id bigint,
    external_object_id varchar(200),
    local_revision bigint DEFAULT 0 NOT NULL,
    synced_revision bigint DEFAULT 0 NOT NULL,
    synced_digest varchar(64),
    template_version varchar(64) NOT NULL,
    last_synced_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_integration_mapping PRIMARY KEY (id),
    CONSTRAINT uq_integration_mapping_01 UNIQUE (connection_id, external_object_id),
    CONSTRAINT uq_integration_mapping_02 UNIQUE (connection_id, content_version_id),
    CONSTRAINT uq_integration_mapping_03 UNIQUE (connection_id, problem_version_id),
    CONSTRAINT uq_integration_mapping_04 UNIQUE (connection_id, problem_attempt_id),
    CONSTRAINT uq_integration_mapping_05 UNIQUE (connection_id, wrong_answer_id),
    CONSTRAINT uq_integration_mapping_06 UNIQUE (connection_id, test_attempt_id),
    CONSTRAINT uq_integration_mapping_07 UNIQUE (connection_id, monthly_evaluation_id),
    CONSTRAINT uq_integration_mapping_08 UNIQUE (connection_id, learning_session_id),
    CONSTRAINT uq_integration_mapping_09 UNIQUE (id, user_id),
    CONSTRAINT ck_integration_mapping_01 CHECK (num_nonnulls(content_version_id, problem_version_id, problem_attempt_id, wrong_answer_id, test_attempt_id, monthly_evaluation_id, learning_session_id) = 1),
    CONSTRAINT ck_integration_mapping_02 CHECK (local_revision >= 0),
    CONSTRAINT ck_integration_mapping_03 CHECK (synced_revision BETWEEN 0 AND local_revision),
    CONSTRAINT ck_integration_mapping_04 CHECK (revision >= 0)
);
COMMENT ON TABLE daily_career.integration_mapping IS '다형성 entity_type+entity_id만 두지 않고 실제 FK 7개 중 1개를 사용한다. 공유 콘텐츠에 대한 사용자별 복제는 허용하되 소유권은 서버가 검증한다.';
COMMENT ON COLUMN daily_career.integration_mapping.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.integration_mapping.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.integration_mapping.connection_id IS '외부 연결';
COMMENT ON COLUMN daily_career.integration_mapping.content_version_id IS '학습 콘텐츠 버전';
COMMENT ON COLUMN daily_career.integration_mapping.problem_version_id IS '문제 버전';
COMMENT ON COLUMN daily_career.integration_mapping.problem_attempt_id IS '개별 풀이';
COMMENT ON COLUMN daily_career.integration_mapping.wrong_answer_id IS '오답 노트';
COMMENT ON COLUMN daily_career.integration_mapping.test_attempt_id IS '시험 응시';
COMMENT ON COLUMN daily_career.integration_mapping.monthly_evaluation_id IS '월말 평가';
COMMENT ON COLUMN daily_career.integration_mapping.learning_session_id IS '학습 기록';
COMMENT ON COLUMN daily_career.integration_mapping.external_object_id IS '외부 페이지 식별자; 생성 전 NULL';
COMMENT ON COLUMN daily_career.integration_mapping.local_revision IS '외부 전송용 단조 증가 버전';
COMMENT ON COLUMN daily_career.integration_mapping.synced_revision IS '마지막으로 성공한 버전';
COMMENT ON COLUMN daily_career.integration_mapping.synced_digest IS '성공 페이로드 해시';
COMMENT ON COLUMN daily_career.integration_mapping.template_version IS '적용 템플릿 버전';
COMMENT ON COLUMN daily_career.integration_mapping.last_synced_at IS '성공 시각';
COMMENT ON COLUMN daily_career.integration_mapping.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.integration_mapping.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.integration_mapping.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.sync_job (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    mapping_id bigint NOT NULL,
    source_revision bigint NOT NULL,
    operation varchar(32) DEFAULT 'UPSERT' NOT NULL,
    status varchar(32) DEFAULT 'QUEUED' NOT NULL,
    payload_snapshot jsonb NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz,
    lease_token varchar(64),
    lease_until timestamptz,
    finished_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_sync_job PRIMARY KEY (id),
    CONSTRAINT uq_sync_job_01 UNIQUE (mapping_id, source_revision, operation),
    CONSTRAINT uq_sync_job_02 UNIQUE (id, user_id),
    CONSTRAINT ck_sync_job_01 CHECK (source_revision > 0),
    CONSTRAINT ck_sync_job_02 CHECK (attempt_count >= 0),
    CONSTRAINT ck_sync_job_03 CHECK (revision >= 0),
    CONSTRAINT ck_sync_job_04 CHECK (operation IN ('UPSERT', 'ARCHIVE')),
    CONSTRAINT ck_sync_job_05 CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED', 'CONFLICT', 'UNKNOWN'))
);
COMMENT ON TABLE daily_career.sync_job IS '같은 매핑은 순차 처리한다. 이미 성공한 버전보다 오래된 작업은 SUPERSEDED. 원격 생성 결과 미확인 때 source key로 확인 후 재시도한다.';
COMMENT ON COLUMN daily_career.sync_job.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.sync_job.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.sync_job.mapping_id IS '대상 매핑';
COMMENT ON COLUMN daily_career.sync_job.source_revision IS '대상 앱 전송 버전';
COMMENT ON COLUMN daily_career.sync_job.operation IS '허용값: UPSERT, ARCHIVE';
COMMENT ON COLUMN daily_career.sync_job.status IS '허용값: QUEUED, RUNNING, SUCCEEDED, FAILED, SUPERSEDED, CONFLICT, UNKNOWN';
COMMENT ON COLUMN daily_career.sync_job.payload_snapshot IS '전송할 확정 스냅샷; 자격증명 제외';
COMMENT ON COLUMN daily_career.sync_job.attempt_count IS '시도 수';
COMMENT ON COLUMN daily_career.sync_job.next_attempt_at IS '다음 시도';
COMMENT ON COLUMN daily_career.sync_job.lease_token IS '점유 토큰';
COMMENT ON COLUMN daily_career.sync_job.lease_until IS '점유 만료';
COMMENT ON COLUMN daily_career.sync_job.finished_at IS '종료 시각';
COMMENT ON COLUMN daily_career.sync_job.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.sync_job.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.sync_job.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.sync_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    sync_job_id bigint NOT NULL,
    attempt_no integer NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    http_status smallint,
    provider_request_id varchar(200),
    error_code varchar(64),
    outcome varchar(24) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_sync_attempt PRIMARY KEY (id),
    CONSTRAINT uq_sync_attempt_01 UNIQUE (sync_job_id, attempt_no),
    CONSTRAINT uq_sync_attempt_02 UNIQUE (id, user_id),
    CONSTRAINT ck_sync_attempt_01 CHECK (attempt_no > 0),
    CONSTRAINT ck_sync_attempt_02 CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_sync_attempt_03 CHECK (finished_at IS NULL OR finished_at >= started_at)
);
COMMENT ON TABLE daily_career.sync_attempt IS '';
COMMENT ON COLUMN daily_career.sync_attempt.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.sync_attempt.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.sync_attempt.sync_job_id IS '동기화 작업';
COMMENT ON COLUMN daily_career.sync_attempt.attempt_no IS '시도 순번';
COMMENT ON COLUMN daily_career.sync_attempt.started_at IS '시도 시작';
COMMENT ON COLUMN daily_career.sync_attempt.finished_at IS '시도 종료';
COMMENT ON COLUMN daily_career.sync_attempt.http_status IS '응답 코드; 응답 없으면 NULL';
COMMENT ON COLUMN daily_career.sync_attempt.provider_request_id IS '외부 추적 ID';
COMMENT ON COLUMN daily_career.sync_attempt.error_code IS '비민감 정규화 오류';
COMMENT ON COLUMN daily_career.sync_attempt.outcome IS 'SUCCESS/RETRYABLE/PERMANENT/UNKNOWN';
COMMENT ON COLUMN daily_career.sync_attempt.created_at IS '생성 시각';

CREATE TABLE daily_career.outbox_event (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint,
    event_key varchar(160) NOT NULL,
    event_type varchar(80) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id bigint NOT NULL,
    aggregate_revision bigint NOT NULL,
    payload jsonb NOT NULL,
    status varchar(32) DEFAULT 'PENDING' NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    available_at timestamptz NOT NULL,
    lease_token varchar(64),
    lease_until timestamptz,
    published_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_01 UNIQUE (event_key),
    CONSTRAINT ck_outbox_event_01 CHECK (aggregate_revision >= 0),
    CONSTRAINT ck_outbox_event_02 CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_event_03 CHECK (revision >= 0),
    CONSTRAINT ck_outbox_event_04 CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD'))
);
COMMENT ON TABLE daily_career.outbox_event IS '학습/시험 변경과 동일 트랜잭션으로 INSERT한다. 외부 통신은 커밋 뒤 작업자가 처리한다. 최소 1회 처리 전제이며 소비자 업무 키로 중복 효과를 막는다.';
COMMENT ON COLUMN daily_career.outbox_event.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.outbox_event.user_id IS '관련 사용자; 시스템 이벤트는 NULL';
COMMENT ON COLUMN daily_career.outbox_event.event_key IS '이벤트 고유키';
COMMENT ON COLUMN daily_career.outbox_event.event_type IS '이벤트 유형';
COMMENT ON COLUMN daily_career.outbox_event.aggregate_type IS '원본 루트 종류; 라우팅 메타데이터';
COMMENT ON COLUMN daily_career.outbox_event.aggregate_id IS '원본 루트 ID; 업무 FK 대체 용도 아님';
COMMENT ON COLUMN daily_career.outbox_event.aggregate_revision IS '원본 확정 버전';
COMMENT ON COLUMN daily_career.outbox_event.payload IS '식별자·이벤트 메타데이터 중심';
COMMENT ON COLUMN daily_career.outbox_event.status IS '허용값: PENDING, PROCESSING, PUBLISHED, DEAD';
COMMENT ON COLUMN daily_career.outbox_event.attempt_count IS '시도 수';
COMMENT ON COLUMN daily_career.outbox_event.available_at IS '처리 가능 시각';
COMMENT ON COLUMN daily_career.outbox_event.lease_token IS '점유 토큰';
COMMENT ON COLUMN daily_career.outbox_event.lease_until IS '점유 만료';
COMMENT ON COLUMN daily_career.outbox_event.published_at IS '하위 작업 등록 완료';
COMMENT ON COLUMN daily_career.outbox_event.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.outbox_event.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.outbox_event.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.idempotency_record (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    operation_scope varchar(200) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_digest varchar(64) NOT NULL,
    status varchar(32) DEFAULT 'PROCESSING' NOT NULL,
    response_status smallint,
    response_payload jsonb,
    expires_at timestamptz NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_record_01 UNIQUE (user_id, operation_scope, idempotency_key),
    CONSTRAINT uq_idempotency_record_02 UNIQUE (id, user_id),
    CONSTRAINT ck_idempotency_record_01 CHECK (response_status IS NULL OR response_status BETWEEN 100 AND 599),
    CONSTRAINT ck_idempotency_record_02 CHECK (revision >= 0),
    CONSTRAINT ck_idempotency_record_03 CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED'))
);
COMMENT ON TABLE daily_career.idempotency_record IS '같은 키+다른 본문은 409. 만료 후에도 시험 제출/동기화 업무 유니크 제약이 중복 부작용을 막아야 한다. AI/동기화는 enqueue 결과까지만 같은 트랜잭션에서 확정한다.';
COMMENT ON COLUMN daily_career.idempotency_record.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.idempotency_record.user_id IS '소유 사용자';
COMMENT ON COLUMN daily_career.idempotency_record.operation_scope IS '버전·메서드·라우트·대상 ID를 포함한 연산 키';
COMMENT ON COLUMN daily_career.idempotency_record.idempotency_key IS '클라이언트 재시도 키';
COMMENT ON COLUMN daily_career.idempotency_record.request_digest IS '정규화 본문+대상 해시';
COMMENT ON COLUMN daily_career.idempotency_record.status IS '허용값: PROCESSING, SUCCEEDED, FAILED';
COMMENT ON COLUMN daily_career.idempotency_record.response_status IS '기존 결과 HTTP 상태';
COMMENT ON COLUMN daily_career.idempotency_record.response_payload IS '재현할 결과; 민감정보 최소화';
COMMENT ON COLUMN daily_career.idempotency_record.expires_at IS '중복 방지 레코드 만료';
COMMENT ON COLUMN daily_career.idempotency_record.created_at IS '생성 시각';
COMMENT ON COLUMN daily_career.idempotency_record.updated_at IS '수정 시각; 서비스가 갱신';
COMMENT ON COLUMN daily_career.idempotency_record.revision IS '낙관적 잠금 버전; 서비스가 증가';

CREATE TABLE daily_career.audit_event (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    actor_user_id bigint,
    action varchar(80) NOT NULL,
    resource_type varchar(64) NOT NULL,
    resource_id bigint,
    request_id varchar(100),
    change_summary jsonb NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);
COMMENT ON TABLE daily_career.audit_event IS '토큰·세션·정답 원문·주민번호·건강 등 불필요한 개인정보는 저장하지 않는다. 논리 참조는 감사 맥락 보존 목적이며 업무 무결성의 기준은 아니다.';
COMMENT ON COLUMN daily_career.audit_event.id IS '내부 식별자; API 표현은 본문 ID 계약 참조';
COMMENT ON COLUMN daily_career.audit_event.actor_user_id IS '사용자 행위자; 시스템은 NULL';
COMMENT ON COLUMN daily_career.audit_event.action IS '행위 코드';
COMMENT ON COLUMN daily_career.audit_event.resource_type IS '대상 종류';
COMMENT ON COLUMN daily_career.audit_event.resource_id IS '대상 ID';
COMMENT ON COLUMN daily_career.audit_event.request_id IS '요청 추적 ID';
COMMENT ON COLUMN daily_career.audit_event.change_summary IS '허용 필드만 기록한 변경 요약';
COMMENT ON COLUMN daily_career.audit_event.created_at IS '생성 시각';

-- Resolve cycles only after every referenced key exists.
ALTER TABLE daily_career.user_identity ADD CONSTRAINT fk_user_identity_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.user_setting ADD CONSTRAINT fk_user_setting_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_version ADD CONSTRAINT fk_curriculum_version_01 FOREIGN KEY (curriculum_id) REFERENCES daily_career.curriculum (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_01 FOREIGN KEY (curriculum_version_id) REFERENCES daily_career.curriculum_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_02 FOREIGN KEY (parent_id, curriculum_version_id) REFERENCES daily_career.curriculum_unit (id, curriculum_version_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_03 FOREIGN KEY (subject_id) REFERENCES daily_career.subject (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_04 FOREIGN KEY (content_version_id) REFERENCES daily_career.learning_content_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_05 FOREIGN KEY (problem_version_id) REFERENCES daily_career.problem_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.curriculum_unit ADD CONSTRAINT fk_curriculum_unit_06 FOREIGN KEY (test_version_id) REFERENCES daily_career.test_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.user_curriculum ADD CONSTRAINT fk_user_curriculum_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.user_curriculum ADD CONSTRAINT fk_user_curriculum_02 FOREIGN KEY (curriculum_version_id) REFERENCES daily_career.curriculum_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.schedule_policy ADD CONSTRAINT fk_schedule_policy_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.schedule_policy ADD CONSTRAINT fk_schedule_policy_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.schedule_rest_weekday ADD CONSTRAINT fk_schedule_rest_weekday_01 FOREIGN KEY (schedule_policy_id) REFERENCES daily_career.schedule_policy (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day ADD CONSTRAINT fk_learning_day_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day ADD CONSTRAINT fk_learning_day_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day ADD CONSTRAINT fk_learning_day_03 FOREIGN KEY (schedule_policy_id, user_curriculum_id, user_id) REFERENCES daily_career.schedule_policy (id, user_curriculum_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_02 FOREIGN KEY (user_curriculum_id, user_id, curriculum_version_id) REFERENCES daily_career.user_curriculum (id, user_id, curriculum_version_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_03 FOREIGN KEY (curriculum_unit_id, curriculum_version_id) REFERENCES daily_career.curriculum_unit (id, curriculum_version_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_04 FOREIGN KEY (learning_day_id, user_curriculum_id, user_id) REFERENCES daily_career.learning_day (id, user_curriculum_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_05 FOREIGN KEY (content_version_id) REFERENCES daily_career.learning_content_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_06 FOREIGN KEY (problem_version_id) REFERENCES daily_career.problem_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_day_item ADD CONSTRAINT fk_learning_day_item_07 FOREIGN KEY (test_version_id) REFERENCES daily_career.test_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.schedule_change ADD CONSTRAINT fk_schedule_change_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.schedule_change ADD CONSTRAINT fk_schedule_change_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_session ADD CONSTRAINT fk_learning_session_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_session ADD CONSTRAINT fk_learning_session_02 FOREIGN KEY (learning_day_item_id, user_id) REFERENCES daily_career.learning_day_item (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_content ADD CONSTRAINT fk_learning_content_01 FOREIGN KEY (owner_user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_content ADD CONSTRAINT fk_learning_content_02 FOREIGN KEY (subject_id) REFERENCES daily_career.subject (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_content_version ADD CONSTRAINT fk_learning_content_version_01 FOREIGN KEY (learning_content_id) REFERENCES daily_career.learning_content (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.learning_content_version ADD CONSTRAINT fk_learning_content_version_02 FOREIGN KEY (ai_job_id) REFERENCES daily_career.ai_job (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem ADD CONSTRAINT fk_problem_01 FOREIGN KEY (owner_user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem ADD CONSTRAINT fk_problem_02 FOREIGN KEY (subject_id) REFERENCES daily_career.subject (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem ADD CONSTRAINT fk_problem_03 FOREIGN KEY (parent_problem_id) REFERENCES daily_career.problem (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_version ADD CONSTRAINT fk_problem_version_01 FOREIGN KEY (problem_id) REFERENCES daily_career.problem (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_version ADD CONSTRAINT fk_problem_version_02 FOREIGN KEY (ai_job_id) REFERENCES daily_career.ai_job (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_attempt ADD CONSTRAINT fk_problem_attempt_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_attempt ADD CONSTRAINT fk_problem_attempt_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_attempt ADD CONSTRAINT fk_problem_attempt_03 FOREIGN KEY (problem_version_id) REFERENCES daily_career.problem_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.problem_attempt ADD CONSTRAINT fk_problem_attempt_04 FOREIGN KEY (learning_session_id, user_id) REFERENCES daily_career.learning_session (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT fk_wrong_answer_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.wrong_answer ADD CONSTRAINT fk_wrong_answer_02 FOREIGN KEY (problem_id) REFERENCES daily_career.problem (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.wrong_answer_occurrence ADD CONSTRAINT fk_wrong_answer_occurrence_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.wrong_answer_occurrence ADD CONSTRAINT fk_wrong_answer_occurrence_02 FOREIGN KEY (wrong_answer_id, user_id) REFERENCES daily_career.wrong_answer (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.wrong_answer_occurrence ADD CONSTRAINT fk_wrong_answer_occurrence_03 FOREIGN KEY (grading_result_id, user_id) REFERENCES daily_career.grading_result (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.review_task ADD CONSTRAINT fk_review_task_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.review_task ADD CONSTRAINT fk_review_task_02 FOREIGN KEY (wrong_answer_id, user_id) REFERENCES daily_career.wrong_answer (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.review_task ADD CONSTRAINT fk_review_task_03 FOREIGN KEY (completed_attempt_id, user_id) REFERENCES daily_career.problem_attempt (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test ADD CONSTRAINT fk_test_01 FOREIGN KEY (owner_user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_version ADD CONSTRAINT fk_test_version_01 FOREIGN KEY (test_id) REFERENCES daily_career.test (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_item ADD CONSTRAINT fk_test_item_01 FOREIGN KEY (test_version_id) REFERENCES daily_career.test_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_item ADD CONSTRAINT fk_test_item_02 FOREIGN KEY (problem_version_id, problem_id) REFERENCES daily_career.problem_version (id, problem_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt ADD CONSTRAINT fk_test_attempt_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt ADD CONSTRAINT fk_test_attempt_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt ADD CONSTRAINT fk_test_attempt_03 FOREIGN KEY (test_version_id) REFERENCES daily_career.test_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt_item ADD CONSTRAINT fk_test_attempt_item_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt_item ADD CONSTRAINT fk_test_attempt_item_02 FOREIGN KEY (test_attempt_id, test_version_id, user_id) REFERENCES daily_career.test_attempt (id, test_version_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.test_attempt_item ADD CONSTRAINT fk_test_attempt_item_03 FOREIGN KEY (test_item_id, test_version_id) REFERENCES daily_career.test_item (id, test_version_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_run ADD CONSTRAINT fk_grading_run_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_run ADD CONSTRAINT fk_grading_run_02 FOREIGN KEY (problem_attempt_id, user_id) REFERENCES daily_career.problem_attempt (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_run ADD CONSTRAINT fk_grading_run_03 FOREIGN KEY (test_attempt_id, user_id) REFERENCES daily_career.test_attempt (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_run ADD CONSTRAINT fk_grading_run_04 FOREIGN KEY (ai_job_id, user_id) REFERENCES daily_career.ai_job (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_result ADD CONSTRAINT fk_grading_result_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_result ADD CONSTRAINT fk_grading_result_02 FOREIGN KEY (grading_run_id, problem_attempt_id, user_id) REFERENCES daily_career.grading_run (id, problem_attempt_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_result ADD CONSTRAINT fk_grading_result_03 FOREIGN KEY (grading_run_id, test_attempt_id, user_id) REFERENCES daily_career.grading_run (id, test_attempt_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.grading_result ADD CONSTRAINT fk_grading_result_04 FOREIGN KEY (test_attempt_item_id, test_attempt_id, user_id) REFERENCES daily_career.test_attempt_item (id, test_attempt_id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.monthly_evaluation ADD CONSTRAINT fk_monthly_evaluation_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.monthly_evaluation ADD CONSTRAINT fk_monthly_evaluation_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.monthly_evaluation ADD CONSTRAINT fk_monthly_evaluation_03 FOREIGN KEY (ai_job_id, user_id) REFERENCES daily_career.ai_job (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_conversation ADD CONSTRAINT fk_ai_conversation_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_conversation ADD CONSTRAINT fk_ai_conversation_02 FOREIGN KEY (user_curriculum_id, user_id) REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_message ADD CONSTRAINT fk_ai_message_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_message ADD CONSTRAINT fk_ai_message_02 FOREIGN KEY (conversation_id, user_id) REFERENCES daily_career.ai_conversation (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_message ADD CONSTRAINT fk_ai_message_03 FOREIGN KEY (ai_job_id, user_id) REFERENCES daily_career.ai_job (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_budget ADD CONSTRAINT fk_ai_budget_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_job ADD CONSTRAINT fk_ai_job_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_job ADD CONSTRAINT fk_ai_job_02 FOREIGN KEY (budget_id, user_id) REFERENCES daily_career.ai_budget (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_job ADD CONSTRAINT fk_ai_job_03 FOREIGN KEY (conversation_id, user_id) REFERENCES daily_career.ai_conversation (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_usage ADD CONSTRAINT fk_ai_usage_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.ai_usage ADD CONSTRAINT fk_ai_usage_02 FOREIGN KEY (ai_job_id, user_id) REFERENCES daily_career.ai_job (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification_preference ADD CONSTRAINT fk_notification_preference_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.push_subscription ADD CONSTRAINT fk_push_subscription_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification ADD CONSTRAINT fk_notification_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification_delivery ADD CONSTRAINT fk_notification_delivery_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification_delivery ADD CONSTRAINT fk_notification_delivery_02 FOREIGN KEY (notification_id, user_id) REFERENCES daily_career.notification (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification_delivery ADD CONSTRAINT fk_notification_delivery_03 FOREIGN KEY (push_subscription_id, user_id) REFERENCES daily_career.push_subscription (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.notification_delivery ADD CONSTRAINT fk_notification_delivery_04 FOREIGN KEY (integration_connection_id, user_id) REFERENCES daily_career.integration_connection (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_connection ADD CONSTRAINT fk_integration_connection_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_02 FOREIGN KEY (connection_id, user_id) REFERENCES daily_career.integration_connection (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_03 FOREIGN KEY (content_version_id) REFERENCES daily_career.learning_content_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_04 FOREIGN KEY (problem_version_id) REFERENCES daily_career.problem_version (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_05 FOREIGN KEY (problem_attempt_id, user_id) REFERENCES daily_career.problem_attempt (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_06 FOREIGN KEY (wrong_answer_id, user_id) REFERENCES daily_career.wrong_answer (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_07 FOREIGN KEY (test_attempt_id, user_id) REFERENCES daily_career.test_attempt (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_08 FOREIGN KEY (monthly_evaluation_id, user_id) REFERENCES daily_career.monthly_evaluation (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.integration_mapping ADD CONSTRAINT fk_integration_mapping_09 FOREIGN KEY (learning_session_id, user_id) REFERENCES daily_career.learning_session (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.sync_job ADD CONSTRAINT fk_sync_job_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.sync_job ADD CONSTRAINT fk_sync_job_02 FOREIGN KEY (mapping_id, user_id) REFERENCES daily_career.integration_mapping (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.sync_attempt ADD CONSTRAINT fk_sync_attempt_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.sync_attempt ADD CONSTRAINT fk_sync_attempt_02 FOREIGN KEY (sync_job_id, user_id) REFERENCES daily_career.sync_job (id, user_id) ON DELETE RESTRICT;
ALTER TABLE daily_career.outbox_event ADD CONSTRAINT fk_outbox_event_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.idempotency_record ADD CONSTRAINT fk_idempotency_record_01 FOREIGN KEY (user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;
ALTER TABLE daily_career.audit_event ADD CONSTRAINT fk_audit_event_01 FOREIGN KEY (actor_user_id) REFERENCES daily_career.app_user (id) ON DELETE RESTRICT;

-- Adopt all source candidates: uniqueness invariants and initial access paths.
CREATE INDEX ix_fk_user_identity_001 ON daily_career.user_identity (user_id);
CREATE UNIQUE INDEX uq_curriculum_unit_order ON daily_career.curriculum_unit (curriculum_version_id, COALESCE(parent_id, 0), order_no);
CREATE INDEX ix_fk_curriculum_unit_005 ON daily_career.curriculum_unit (parent_id, curriculum_version_id);
CREATE INDEX ix_fk_curriculum_unit_006 ON daily_career.curriculum_unit (subject_id);
CREATE INDEX ix_fk_curriculum_unit_094 ON daily_career.curriculum_unit (content_version_id);
CREATE INDEX ix_fk_curriculum_unit_095 ON daily_career.curriculum_unit (problem_version_id);
CREATE INDEX ix_fk_curriculum_unit_096 ON daily_career.curriculum_unit (test_version_id);
CREATE UNIQUE INDEX uq_user_curriculum_live ON daily_career.user_curriculum (user_id) WHERE status IN ('PLANNED','ACTIVE','PAUSED');
CREATE INDEX ix_fk_user_curriculum_007 ON daily_career.user_curriculum (user_id);
CREATE INDEX ix_fk_user_curriculum_008 ON daily_career.user_curriculum (curriculum_version_id);
CREATE INDEX ix_fk_schedule_policy_009 ON daily_career.schedule_policy (user_id);
CREATE INDEX ix_fk_schedule_policy_010 ON daily_career.schedule_policy (user_curriculum_id, user_id);
CREATE INDEX ix_learning_day_user_date ON daily_career.learning_day (user_id, local_date);
CREATE INDEX ix_fk_learning_day_013 ON daily_career.learning_day (user_curriculum_id, user_id);
CREATE INDEX ix_fk_learning_day_014 ON daily_career.learning_day (schedule_policy_id, user_curriculum_id, user_id);
CREATE INDEX ix_learning_day_item_day ON daily_career.learning_day_item (learning_day_id, order_no);
CREATE INDEX ix_fk_learning_day_item_015 ON daily_career.learning_day_item (user_id);
CREATE INDEX ix_fk_learning_day_item_016 ON daily_career.learning_day_item (user_curriculum_id, user_id, curriculum_version_id);
CREATE INDEX ix_fk_learning_day_item_017 ON daily_career.learning_day_item (curriculum_unit_id, curriculum_version_id);
CREATE INDEX ix_fk_learning_day_item_018 ON daily_career.learning_day_item (learning_day_id, user_curriculum_id, user_id);
CREATE INDEX ix_fk_learning_day_item_097 ON daily_career.learning_day_item (content_version_id);
CREATE INDEX ix_fk_learning_day_item_098 ON daily_career.learning_day_item (problem_version_id);
CREATE INDEX ix_fk_learning_day_item_099 ON daily_career.learning_day_item (test_version_id);
CREATE INDEX ix_fk_schedule_change_019 ON daily_career.schedule_change (user_id);
CREATE INDEX ix_fk_schedule_change_020 ON daily_career.schedule_change (user_curriculum_id, user_id);
CREATE UNIQUE INDEX uq_learning_session_open ON daily_career.learning_session (user_id) WHERE ended_at IS NULL;
CREATE INDEX ix_learning_session_activity ON daily_career.learning_session (user_id, activity_date);
CREATE INDEX ix_fk_learning_session_022 ON daily_career.learning_session (learning_day_item_id, user_id);
CREATE INDEX ix_fk_learning_content_023 ON daily_career.learning_content (owner_user_id);
CREATE INDEX ix_fk_learning_content_024 ON daily_career.learning_content (subject_id);
CREATE INDEX ix_fk_learning_content_version_100 ON daily_career.learning_content_version (ai_job_id);
CREATE INDEX ix_fk_problem_026 ON daily_career.problem (owner_user_id);
CREATE INDEX ix_fk_problem_027 ON daily_career.problem (subject_id);
CREATE INDEX ix_fk_problem_028 ON daily_career.problem (parent_problem_id);
CREATE INDEX ix_fk_problem_version_101 ON daily_career.problem_version (ai_job_id);
CREATE INDEX ix_problem_attempt_recent ON daily_career.problem_attempt (user_id, created_at DESC, id DESC);
CREATE INDEX ix_fk_problem_attempt_031 ON daily_career.problem_attempt (user_curriculum_id, user_id);
CREATE INDEX ix_fk_problem_attempt_032 ON daily_career.problem_attempt (problem_version_id);
CREATE INDEX ix_fk_problem_attempt_033 ON daily_career.problem_attempt (learning_session_id, user_id);
CREATE INDEX ix_fk_wrong_answer_035 ON daily_career.wrong_answer (problem_id);
CREATE INDEX ix_fk_wrong_answer_occurrence_036 ON daily_career.wrong_answer_occurrence (user_id);
CREATE INDEX ix_fk_wrong_answer_occurrence_037 ON daily_career.wrong_answer_occurrence (wrong_answer_id, user_id);
CREATE INDEX ix_fk_wrong_answer_occurrence_058 ON daily_career.wrong_answer_occurrence (grading_result_id, user_id);
CREATE UNIQUE INDEX uq_review_task_planned ON daily_career.review_task (wrong_answer_id) WHERE status = 'PLANNED';
CREATE INDEX ix_review_task_due ON daily_career.review_task (user_id, due_at) WHERE status = 'PLANNED';
CREATE INDEX ix_fk_review_task_038 ON daily_career.review_task (user_id);
CREATE INDEX ix_fk_review_task_039 ON daily_career.review_task (wrong_answer_id, user_id);
CREATE INDEX ix_fk_review_task_040 ON daily_career.review_task (completed_attempt_id, user_id);
CREATE INDEX ix_fk_test_041 ON daily_career.test (owner_user_id);
CREATE INDEX ix_fk_test_item_044 ON daily_career.test_item (problem_version_id, problem_id);
CREATE UNIQUE INDEX uq_test_attempt_open ON daily_career.test_attempt (user_id, test_version_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_test_attempt_deadline ON daily_career.test_attempt (deadline_at) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_fk_test_attempt_046 ON daily_career.test_attempt (user_curriculum_id, user_id);
CREATE INDEX ix_fk_test_attempt_047 ON daily_career.test_attempt (test_version_id);
CREATE INDEX ix_fk_test_attempt_item_048 ON daily_career.test_attempt_item (user_id);
CREATE INDEX ix_fk_test_attempt_item_049 ON daily_career.test_attempt_item (test_attempt_id, test_version_id, user_id);
CREATE INDEX ix_fk_test_attempt_item_050 ON daily_career.test_attempt_item (test_item_id, test_version_id);
CREATE UNIQUE INDEX uq_grading_current_problem ON daily_career.grading_run (problem_attempt_id) WHERE is_current AND problem_attempt_id IS NOT NULL;
CREATE UNIQUE INDEX uq_grading_current_test ON daily_career.grading_run (test_attempt_id) WHERE is_current AND test_attempt_id IS NOT NULL;
CREATE INDEX ix_fk_grading_run_051 ON daily_career.grading_run (user_id);
CREATE INDEX ix_fk_grading_run_052 ON daily_career.grading_run (problem_attempt_id, user_id);
CREATE INDEX ix_fk_grading_run_053 ON daily_career.grading_run (test_attempt_id, user_id);
CREATE INDEX ix_fk_grading_run_102 ON daily_career.grading_run (ai_job_id, user_id);
CREATE INDEX ix_fk_grading_result_054 ON daily_career.grading_result (user_id);
CREATE INDEX ix_fk_grading_result_055 ON daily_career.grading_result (grading_run_id, problem_attempt_id, user_id);
CREATE INDEX ix_fk_grading_result_056 ON daily_career.grading_result (grading_run_id, test_attempt_id, user_id);
CREATE INDEX ix_fk_grading_result_057 ON daily_career.grading_result (test_attempt_item_id, test_attempt_id, user_id);
CREATE INDEX ix_fk_monthly_evaluation_059 ON daily_career.monthly_evaluation (user_id);
CREATE INDEX ix_fk_monthly_evaluation_060 ON daily_career.monthly_evaluation (user_curriculum_id, user_id);
CREATE INDEX ix_fk_monthly_evaluation_103 ON daily_career.monthly_evaluation (ai_job_id, user_id);
CREATE INDEX ix_fk_ai_conversation_061 ON daily_career.ai_conversation (user_id);
CREATE INDEX ix_fk_ai_conversation_062 ON daily_career.ai_conversation (user_curriculum_id, user_id);
CREATE INDEX ix_fk_ai_message_063 ON daily_career.ai_message (user_id);
CREATE INDEX ix_fk_ai_message_064 ON daily_career.ai_message (conversation_id, user_id);
CREATE INDEX ix_fk_ai_message_104 ON daily_career.ai_message (ai_job_id, user_id);
CREATE INDEX ix_ai_job_claim ON daily_career.ai_job (status, next_attempt_at, id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX ix_fk_ai_job_067 ON daily_career.ai_job (budget_id, user_id);
CREATE INDEX ix_fk_ai_job_068 ON daily_career.ai_job (conversation_id, user_id);
CREATE UNIQUE INDEX uq_ai_usage_provider_request ON daily_career.ai_usage (provider, provider_request_id) WHERE provider_request_id IS NOT NULL;
CREATE INDEX ix_fk_ai_usage_069 ON daily_career.ai_usage (user_id);
CREATE INDEX ix_fk_ai_usage_070 ON daily_career.ai_usage (ai_job_id, user_id);
CREATE INDEX ix_fk_push_subscription_072 ON daily_career.push_subscription (user_id);
CREATE INDEX ix_notification_inbox ON daily_career.notification (user_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX uq_delivery_push ON daily_career.notification_delivery (notification_id, push_subscription_id) WHERE push_subscription_id IS NOT NULL;
CREATE UNIQUE INDEX uq_delivery_slack ON daily_career.notification_delivery (notification_id, integration_connection_id) WHERE integration_connection_id IS NOT NULL;
CREATE INDEX ix_delivery_claim ON daily_career.notification_delivery (status, next_attempt_at, id);
CREATE INDEX ix_fk_notification_delivery_074 ON daily_career.notification_delivery (user_id);
CREATE INDEX ix_fk_notification_delivery_075 ON daily_career.notification_delivery (notification_id, user_id);
CREATE INDEX ix_fk_notification_delivery_076 ON daily_career.notification_delivery (push_subscription_id, user_id);
CREATE INDEX ix_fk_notification_delivery_105 ON daily_career.notification_delivery (integration_connection_id, user_id);
CREATE UNIQUE INDEX uq_integration_active ON daily_career.integration_connection (user_id, provider) WHERE status = 'ACTIVE';
CREATE INDEX ix_fk_integration_connection_077 ON daily_career.integration_connection (user_id);
CREATE INDEX ix_fk_integration_mapping_078 ON daily_career.integration_mapping (user_id);
CREATE INDEX ix_fk_integration_mapping_079 ON daily_career.integration_mapping (connection_id, user_id);
CREATE INDEX ix_fk_integration_mapping_080 ON daily_career.integration_mapping (content_version_id);
CREATE INDEX ix_fk_integration_mapping_081 ON daily_career.integration_mapping (problem_version_id);
CREATE INDEX ix_fk_integration_mapping_082 ON daily_career.integration_mapping (problem_attempt_id, user_id);
CREATE INDEX ix_fk_integration_mapping_083 ON daily_career.integration_mapping (wrong_answer_id, user_id);
CREATE INDEX ix_fk_integration_mapping_084 ON daily_career.integration_mapping (test_attempt_id, user_id);
CREATE INDEX ix_fk_integration_mapping_085 ON daily_career.integration_mapping (monthly_evaluation_id, user_id);
CREATE INDEX ix_fk_integration_mapping_086 ON daily_career.integration_mapping (learning_session_id, user_id);
CREATE INDEX ix_sync_job_claim ON daily_career.sync_job (status, next_attempt_at, id);
CREATE INDEX ix_fk_sync_job_087 ON daily_career.sync_job (user_id);
CREATE INDEX ix_fk_sync_job_088 ON daily_career.sync_job (mapping_id, user_id);
CREATE INDEX ix_fk_sync_attempt_089 ON daily_career.sync_attempt (user_id);
CREATE INDEX ix_fk_sync_attempt_090 ON daily_career.sync_attempt (sync_job_id, user_id);
CREATE INDEX ix_outbox_claim ON daily_career.outbox_event (status, available_at, id) WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX ix_fk_outbox_event_091 ON daily_career.outbox_event (user_id);
CREATE INDEX ix_idempotency_expiry ON daily_career.idempotency_record (expires_at);
CREATE INDEX ix_audit_resource ON daily_career.audit_event (resource_type, resource_id, created_at);
CREATE INDEX ix_fk_audit_event_093 ON daily_career.audit_event (actor_user_id);
