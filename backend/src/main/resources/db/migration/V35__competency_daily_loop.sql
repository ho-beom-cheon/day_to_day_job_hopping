-- Competency daily loop S1 (docs/competency-daily-loop-design-20261024.md §7.2, §7.3, §8).

create table daily_career.ai_model_price (
    id bigint generated always as identity,
    provider varchar(40) not null,
    model varchar(128) not null,
    input_usd_per_1m numeric(12,6) not null,
    cached_input_usd_per_1m numeric(12,6) not null,
    output_usd_per_1m numeric(12,6) not null,
    effective_from date not null,
    source_url varchar(500) not null,
    verified_at date not null,
    created_at timestamptz not null default now(),
    constraint pk_ai_model_price primary key (id),
    constraint uq_ai_model_price_01 unique (provider, model, effective_from),
    constraint ck_ai_model_price_01 check (input_usd_per_1m >= 0 and cached_input_usd_per_1m >= 0 and output_usd_per_1m >= 0)
);
comment on table daily_career.ai_model_price is '비용 계산 단가. 공식 단가 페이지로 확인한 값만 넣는다.';

insert into daily_career.ai_model_price
    (provider, model, input_usd_per_1m, cached_input_usd_per_1m, output_usd_per_1m, effective_from, source_url, verified_at)
values ('OPENAI', 'gpt-6-luna', 0.10, 0.01, 0.50, date '2026-10-24', 'https://developers.openai.com/api/docs/pricing', date '2026-10-24');

-- reconciled_at now means "the user compared this month with the provider dashboard" (§7.3), not "cost known".
alter table daily_career.ai_usage
    drop constraint ck_ai_usage_09,
    add constraint ck_ai_usage_09 check (status <> 'KNOWN' or (input_tokens is not null and output_tokens is not null
        and budget_cost_amount is not null and pricing_snapshot is not null));

alter table daily_career.user_setting
    add column weekly_task_target smallint not null default 4,
    add column default_time_mode varchar(16) not null default 'STANDARD',
    add column default_grading_mode varchar(8) not null default 'AI',
    add column ai_monthly_limit_krw numeric(12,2) not null default 15000,
    add column ai_fx_rate_krw numeric(12,4) not null default 1500,
    add constraint ck_user_setting_10 check (weekly_task_target between 2 and 7),
    add constraint ck_user_setting_11 check (default_time_mode in ('SHORT','STANDARD','DEEP')),
    add constraint ck_user_setting_12 check (default_grading_mode in ('AI','SELF')),
    add constraint ck_user_setting_13 check (ai_monthly_limit_krw > 0 and ai_monthly_limit_krw <= 100000),
    add constraint ck_user_setting_14 check (ai_fx_rate_krw > 0);

create table daily_career.competency_map_version (
    id bigint generated always as identity,
    code varchar(40) not null,
    version_no integer not null,
    status varchar(16) not null default 'PUBLISHED',
    published_at timestamptz not null default now(),
    constraint pk_competency_map_version primary key (id),
    constraint uq_competency_map_version_01 unique (code, version_no),
    constraint ck_competency_map_version_01 check (status in ('PUBLISHED','RETIRED'))
);

create table daily_career.competency (
    id bigint generated always as identity,
    map_version_id bigint not null references daily_career.competency_map_version(id),
    code varchar(8) not null,
    area varchar(8) not null,
    area_title varchar(60) not null,
    title varchar(200) not null,
    priority varchar(2) not null,
    target_level smallint not null,
    order_no integer not null,
    constraint pk_competency primary key (id),
    constraint uq_competency_01 unique (map_version_id, code),
    constraint ck_competency_01 check (priority in ('P1','P2','P3')),
    constraint ck_competency_02 check (target_level between 1 and 4)
);

create table daily_career.competency_sheet_version (
    id bigint generated always as identity,
    competency_id bigint not null references daily_career.competency(id),
    version_no integer not null,
    status varchar(16) not null default 'PUBLISHED',
    body_markdown text not null,
    criteria_ids jsonb not null,
    source_references jsonb not null,
    content_digest varchar(64) not null,
    review_label varchar(80) not null,
    published_at timestamptz not null default now(),
    constraint pk_competency_sheet_version primary key (id),
    constraint uq_competency_sheet_version_01 unique (competency_id, version_no),
    constraint uq_competency_sheet_version_02 unique (competency_id, content_digest),
    constraint ck_competency_sheet_version_01 check (status in ('PUBLISHED','RETIRED')),
    constraint ck_competency_sheet_version_02 check (jsonb_typeof(criteria_ids) = 'array')
);

create table daily_career.career_fact (
    id bigint generated always as identity,
    user_id bigint not null references daily_career.app_user(id),
    project_name varchar(200) not null,
    client_name varchar(120),
    period_from varchar(7) not null,
    period_to varchar(7),
    role_text varchar(2000) not null default '',
    tech_text varchar(500) not null default '',
    confirmed_scope varchar(4000) not null default '',
    caution_text varchar(2000) not null default '',
    status varchar(16) not null default 'CANDIDATE',
    confirmed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    revision bigint not null default 0,
    constraint pk_career_fact primary key (id),
    constraint uq_career_fact_01 unique (id, user_id),
    constraint ck_career_fact_01 check (status in ('CANDIDATE','CONFIRMED')),
    constraint ck_career_fact_02 check (period_from ~ '^[0-9]{4}-[0-9]{2}$' and (period_to is null or period_to ~ '^[0-9]{4}-[0-9]{2}$')),
    constraint ck_career_fact_03 check ((status = 'CONFIRMED') = (confirmed_at is not null))
);
comment on table daily_career.career_fact is '경력 사실. CONFIRMED만 AI 입력에 쓴다. 이력서 원본과 개인정보는 저장하지 않는다.';

create table daily_career.resume_claim (
    id bigint generated always as identity,
    user_id bigint not null references daily_career.app_user(id),
    career_fact_id bigint,
    claim_text varchar(2000) not null,
    metric_confirmed boolean not null default false,
    review_status varchar(16),
    review_payload jsonb,
    review_job_id bigint,
    revised_text varchar(2000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    revision bigint not null default 0,
    constraint pk_resume_claim primary key (id),
    constraint uq_resume_claim_01 unique (id, user_id),
    constraint fk_resume_claim_01 foreign key (career_fact_id, user_id) references daily_career.career_fact(id, user_id),
    constraint fk_resume_claim_02 foreign key (review_job_id, user_id) references daily_career.ai_job(id, user_id),
    constraint ck_resume_claim_01 check (review_status is null or review_status in ('NEEDS_FIX','NEEDS_EVIDENCE','OK'))
);

create table daily_career.user_competency (
    user_id bigint not null references daily_career.app_user(id),
    competency_id bigint not null references daily_career.competency(id),
    self_level smallint,
    level_estimate numeric(3,2),
    evidence_count integer not null default 0,
    last_score smallint,
    next_due_at timestamptz,
    swap_count integer not null default 0,
    updated_at timestamptz not null default now(),
    revision bigint not null default 0,
    constraint pk_user_competency primary key (user_id, competency_id),
    constraint ck_user_competency_01 check (self_level is null or self_level between 1 and 4),
    constraint ck_user_competency_02 check (level_estimate is null or level_estimate between 1 and 4),
    constraint ck_user_competency_03 check (last_score is null or last_score between 0 and 100)
);

create table daily_career.task_bank_item (
    id bigint generated always as identity,
    competency_id bigint not null references daily_career.competency(id),
    sheet_version_id bigint not null references daily_career.competency_sheet_version(id),
    owner_user_id bigint references daily_career.app_user(id),
    resume_claim_id bigint,
    format varchar(16) not null,
    difficulty smallint not null,
    task_payload jsonb not null,
    rubric_payload jsonb not null,
    reserve_followups jsonb not null,
    fingerprint varchar(64) not null,
    generation_job_id bigint references daily_career.ai_job(id),
    status varchar(16) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint pk_task_bank_item primary key (id),
    constraint fk_task_bank_item_01 foreign key (resume_claim_id, owner_user_id) references daily_career.resume_claim(id, user_id),
    constraint ck_task_bank_item_01 check (format in ('CASE','REVIEW','INTERVIEW')),
    constraint ck_task_bank_item_02 check (difficulty between 3 and 5),
    constraint ck_task_bank_item_03 check (status in ('ACTIVE','SUSPENDED','RETIRED')),
    constraint ck_task_bank_item_04 check (resume_claim_id is null or owner_user_id is not null)
);
create index ix_task_bank_item_pick on daily_career.task_bank_item (competency_id, format, status, id);
comment on column daily_career.task_bank_item.rubric_payload is '서버 전용. 제출 전 클라이언트에 노출하지 않는다.';

create table daily_career.daily_task (
    id bigint generated always as identity,
    user_id bigint not null references daily_career.app_user(id),
    competency_id bigint not null references daily_career.competency(id),
    bank_item_id bigint references daily_career.task_bank_item(id),
    resume_claim_id bigint,
    format varchar(16) not null,
    time_mode varchar(16) not null,
    status varchar(16) not null,
    plan_reason varchar(500) not null,
    grading_mode varchar(8) not null,
    generation_job_id bigint,
    failure_code varchar(64),
    started_at timestamptz,
    submitted_at timestamptz,
    completed_at timestamptz,
    completed_on date,
    swapped_from_task_id bigint references daily_career.daily_task(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    revision bigint not null default 0,
    constraint pk_daily_task primary key (id),
    constraint uq_daily_task_01 unique (id, user_id),
    constraint fk_daily_task_01 foreign key (generation_job_id, user_id) references daily_career.ai_job(id, user_id),
    constraint fk_daily_task_02 foreign key (resume_claim_id, user_id) references daily_career.resume_claim(id, user_id),
    constraint ck_daily_task_01 check (status in ('PREPARING','READY','IN_PROGRESS','GRADING','FEEDBACK','COMPLETED','SWAPPED','FAILED')),
    constraint ck_daily_task_02 check (time_mode in ('SHORT','STANDARD','DEEP')),
    constraint ck_daily_task_03 check (grading_mode in ('AI','SELF')),
    constraint ck_daily_task_04 check (format in ('CASE','REVIEW','INTERVIEW')),
    constraint ck_daily_task_05 check (status in ('PREPARING','FAILED','SWAPPED') or bank_item_id is not null),
    constraint ck_daily_task_06 check ((status = 'COMPLETED') = (completed_at is not null))
);
create index ix_daily_task_user on daily_career.daily_task (user_id, status, id);
create unique index uq_daily_task_open on daily_career.daily_task (user_id)
    where status in ('PREPARING','READY','IN_PROGRESS','GRADING','FEEDBACK');
comment on index daily_career.uq_daily_task_open is '사용자당 진행 중인 과제는 하나.';

create table daily_career.daily_task_turn (
    id bigint generated always as identity,
    task_id bigint not null,
    user_id bigint not null,
    turn_no smallint not null,
    kind varchar(16) not null,
    question_payload jsonb not null,
    check_payload jsonb not null,
    answer_text text,
    sheet_opened boolean not null default false,
    result_payload jsonb,
    score_delta smallint,
    ai_job_id bigint,
    saved_at timestamptz,
    submitted_at timestamptz,
    created_at timestamptz not null default now(),
    constraint pk_daily_task_turn primary key (id),
    constraint uq_daily_task_turn_01 unique (task_id, turn_no),
    constraint fk_daily_task_turn_01 foreign key (task_id, user_id) references daily_career.daily_task(id, user_id),
    constraint fk_daily_task_turn_02 foreign key (ai_job_id, user_id) references daily_career.ai_job(id, user_id),
    constraint ck_daily_task_turn_01 check (kind in ('MAIN','FOLLOWUP')),
    constraint ck_daily_task_turn_02 check ((turn_no = 1) = (kind = 'MAIN')),
    constraint ck_daily_task_turn_03 check (score_delta is null or score_delta between -15 and 15)
);
comment on column daily_career.daily_task_turn.check_payload is '서버 전용 확인 포인트. 답하기 전에는 노출하지 않는다.';

create table daily_career.daily_task_result (
    task_id bigint not null,
    user_id bigint not null,
    status varchar(16) not null,
    base_score smallint,
    final_score smallint,
    criteria jsonb not null,
    feedback text not null default '',
    missing_points jsonb not null default '[]'::jsonb,
    improved_answer text not null default '',
    hold_reason text not null default '',
    card_draft jsonb,
    score_excluded boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pk_daily_task_result primary key (task_id),
    constraint fk_daily_task_result_01 foreign key (task_id, user_id) references daily_career.daily_task(id, user_id),
    constraint ck_daily_task_result_01 check (status in ('GRADED','HOLD','SELF_GRADED')),
    constraint ck_daily_task_result_02 check (base_score is null or base_score between 0 and 100),
    constraint ck_daily_task_result_03 check (final_score is null or final_score between 0 and 100),
    constraint ck_daily_task_result_04 check (status = 'HOLD' or (base_score is not null and final_score is not null))
);

create table daily_career.answer_card (
    id bigint generated always as identity,
    user_id bigint not null references daily_career.app_user(id),
    competency_id bigint not null references daily_career.competency(id),
    task_id bigint not null,
    title varchar(200) not null,
    body_markdown varchar(4000) not null,
    interview_line varchar(500) not null,
    review_prompt varchar(500) not null,
    next_review_at timestamptz,
    review_step smallint not null default 0,
    pinned boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    revision bigint not null default 0,
    constraint pk_answer_card primary key (id),
    constraint uq_answer_card_01 unique (task_id),
    constraint fk_answer_card_01 foreign key (task_id, user_id) references daily_career.daily_task(id, user_id)
);

create table daily_career.task_feedback (
    task_id bigint not null,
    user_id bigint not null,
    usefulness smallint not null,
    note varchar(1000) not null default '',
    created_at timestamptz not null default now(),
    constraint pk_task_feedback primary key (task_id),
    constraint fk_task_feedback_01 foreign key (task_id, user_id) references daily_career.daily_task(id, user_id),
    constraint ck_task_feedback_01 check (usefulness between 1 and 5)
);

create table daily_career.task_issue_report (
    id bigint generated always as identity,
    user_id bigint not null,
    task_id bigint not null,
    bank_item_id bigint references daily_career.task_bank_item(id),
    target varchar(16) not null,
    reason varchar(24) not null,
    note varchar(1000) not null default '',
    exclude_score boolean not null default false,
    status varchar(16) not null default 'OPEN',
    resolution_note varchar(1000),
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    constraint pk_task_issue_report primary key (id),
    constraint fk_task_issue_report_01 foreign key (task_id, user_id) references daily_career.daily_task(id, user_id),
    constraint ck_task_issue_report_01 check (target in ('MATERIAL','FEEDBACK','FOLLOWUP')),
    constraint ck_task_issue_report_02 check (reason in ('FACT_ERROR','GRADING_ERROR','AMBIGUOUS','OUTSIDE_SHEET','OTHER')),
    constraint ck_task_issue_report_03 check (status in ('OPEN','CONFIRMED','REJECTED'))
);
