CREATE TABLE daily_career.notion_oauth_state (
    state_hash char(64) NOT NULL,
    user_id bigint NOT NULL,
    session_hash char(64) NOT NULL,
    connection_revision bigint NOT NULL,
    status varchar(16) DEFAULT 'READY' NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_notion_oauth_state PRIMARY KEY (state_hash),
    CONSTRAINT ck_notion_oauth_state_revision CHECK (connection_revision >= 0),
    CONSTRAINT ck_notion_oauth_state_status CHECK (status IN ('READY', 'PROCESSING', 'CONSUMED')),
    CONSTRAINT fk_notion_oauth_state_user FOREIGN KEY (user_id)
        REFERENCES daily_career.app_user (id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.notion_credential (
    credential_ref uuid NOT NULL,
    user_id bigint NOT NULL,
    key_id varchar(80) NOT NULL,
    access_token_ciphertext text NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_notion_credential PRIMARY KEY (credential_ref),
    CONSTRAINT uq_notion_credential_owner UNIQUE (credential_ref, user_id),
    CONSTRAINT fk_notion_credential_user FOREIGN KEY (user_id)
        REFERENCES daily_career.app_user (id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.notion_revocation (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    credential_ref uuid NOT NULL,
    status varchar(16) DEFAULT 'PENDING' NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz DEFAULT now() NOT NULL,
    completed_at timestamptz,
    last_error_code varchar(80),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_notion_revocation PRIMARY KEY (id),
    CONSTRAINT uq_notion_revocation_credential UNIQUE (credential_ref),
    CONSTRAINT ck_notion_revocation_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_notion_revocation_attempt CHECK (attempt_count >= 0),
    CONSTRAINT fk_notion_revocation_credential FOREIGN KEY (credential_ref, user_id)
        REFERENCES daily_career.notion_credential (credential_ref, user_id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.notion_target_set (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    connection_id bigint NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_notion_target_set PRIMARY KEY (id),
    CONSTRAINT uq_notion_target_set_connection UNIQUE (connection_id),
    CONSTRAINT uq_notion_target_set_owner UNIQUE (id, user_id),
    CONSTRAINT ck_notion_target_set_revision CHECK (revision >= 0),
    CONSTRAINT fk_notion_target_set_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES daily_career.integration_connection (id, user_id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.notion_target (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    target_set_id bigint NOT NULL,
    user_id bigint NOT NULL,
    entity_type varchar(32) NOT NULL,
    database_id uuid NOT NULL,
    data_source_id uuid NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_notion_target PRIMARY KEY (id),
    CONSTRAINT uq_notion_target_entity UNIQUE (target_set_id, entity_type),
    CONSTRAINT ck_notion_target_entity CHECK (entity_type IN (
        'LEARNING_RESULT', 'PROBLEM', 'TEST_RESULT', 'WRONG_ANSWER', 'WEEKLY_REPORT', 'MONTHLY_REPORT'
    )),
    CONSTRAINT fk_notion_target_set FOREIGN KEY (target_set_id, user_id)
        REFERENCES daily_career.notion_target_set (id, user_id) ON DELETE CASCADE
);

CREATE TABLE daily_career.notion_sync_batch (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id bigint NOT NULL,
    curriculum_id bigint NOT NULL,
    connection_id bigint NOT NULL,
    connection_revision bigint NOT NULL,
    target_set_id bigint NOT NULL,
    targets_revision bigint NOT NULL,
    entity_types jsonb NOT NULL,
    date_from date NOT NULL,
    date_to date NOT NULL,
    status varchar(24) DEFAULT 'READY' NOT NULL,
    created_count integer DEFAULT 0 NOT NULL,
    updated_count integer DEFAULT 0 NOT NULL,
    skipped_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    total_count integer DEFAULT 0 NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    original_batch_id bigint,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_notion_sync_batch PRIMARY KEY (id),
    CONSTRAINT uq_notion_sync_batch_owner UNIQUE (id, user_id),
    CONSTRAINT ck_notion_sync_batch_dates CHECK (date_to >= date_from),
    CONSTRAINT ck_notion_sync_batch_status CHECK (status IN ('READY', 'PROCESSING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    CONSTRAINT ck_notion_sync_batch_counts CHECK (
        created_count >= 0 AND updated_count >= 0 AND skipped_count >= 0 AND failed_count >= 0 AND total_count >= 0
    ),
    CONSTRAINT ck_notion_sync_batch_revisions CHECK (connection_revision >= 0 AND targets_revision >= 0 AND revision >= 0),
    CONSTRAINT ck_notion_sync_batch_entities CHECK (jsonb_typeof(entity_types) = 'array'),
    CONSTRAINT fk_notion_sync_batch_user FOREIGN KEY (user_id)
        REFERENCES daily_career.app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notion_sync_batch_curriculum FOREIGN KEY (curriculum_id, user_id)
        REFERENCES daily_career.user_curriculum (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_notion_sync_batch_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES daily_career.integration_connection (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_notion_sync_batch_target_set FOREIGN KEY (target_set_id, user_id)
        REFERENCES daily_career.notion_target_set (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_notion_sync_batch_original FOREIGN KEY (original_batch_id, user_id)
        REFERENCES daily_career.notion_sync_batch (id, user_id) ON DELETE RESTRICT
);

CREATE TABLE daily_career.notion_sync_item (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    batch_id bigint NOT NULL,
    user_id bigint NOT NULL,
    entity_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_revision bigint NOT NULL,
    source_key varchar(160) NOT NULL,
    database_id uuid NOT NULL,
    data_source_id uuid NOT NULL,
    payload_snapshot jsonb NOT NULL,
    status varchar(24) DEFAULT 'READY' NOT NULL,
    retryable boolean DEFAULT false NOT NULL,
    external_object_id varchar(200),
    attempt_count integer DEFAULT 0 NOT NULL,
    error_code varchar(80),
    error_message varchar(500),
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_notion_sync_item PRIMARY KEY (id),
    CONSTRAINT uq_notion_sync_item_source UNIQUE (batch_id, entity_type, source_id),
    CONSTRAINT ck_notion_sync_item_entity CHECK (entity_type IN (
        'LEARNING_RESULT', 'PROBLEM', 'TEST_RESULT', 'WRONG_ANSWER', 'WEEKLY_REPORT', 'MONTHLY_REPORT'
    )),
    CONSTRAINT ck_notion_sync_item_status CHECK (status IN ('READY', 'PROCESSING', 'CREATED', 'UPDATED', 'SKIPPED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_notion_sync_item_values CHECK (source_id > 0 AND source_revision > 0 AND attempt_count >= 0 AND revision >= 0),
    CONSTRAINT fk_notion_sync_item_batch FOREIGN KEY (batch_id, user_id)
        REFERENCES daily_career.notion_sync_batch (id, user_id) ON DELETE RESTRICT
);

CREATE INDEX ix_notion_oauth_state_expiry ON daily_career.notion_oauth_state (expires_at);
CREATE INDEX ix_notion_revocation_claim ON daily_career.notion_revocation (status, next_attempt_at, id);
CREATE INDEX ix_notion_sync_batch_history ON daily_career.notion_sync_batch (user_id, created_at DESC, id DESC);
CREATE INDEX ix_notion_sync_batch_claim ON daily_career.notion_sync_batch (status, id);
CREATE INDEX ix_notion_sync_item_batch ON daily_career.notion_sync_item (batch_id, id);

COMMENT ON TABLE daily_career.notion_oauth_state IS '10분 유효한 세션·사용자·연결 revision 결합 OAuth state. 원문 state는 저장하지 않는다.';
COMMENT ON TABLE daily_career.notion_credential IS '로컬 개발용 암호화 credential vault. 운영에서는 외부 secret manager adapter로 교체한다.';
COMMENT ON TABLE daily_career.notion_target_set IS 'Notion 6종 대상의 독립 낙관적 잠금 aggregate.';
COMMENT ON TABLE daily_career.notion_sync_batch IS 'NOTION-004/005/010/011 배치 root. 공개 상태와 항목 집계를 보존한다.';
COMMENT ON TABLE daily_career.notion_sync_item IS '고정 target·source revision·payload snapshot을 가진 외부 전송 항목.';
