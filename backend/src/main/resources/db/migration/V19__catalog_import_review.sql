CREATE TABLE daily_career.catalog_import (
    curriculum_version_id bigint PRIMARY KEY REFERENCES daily_career.curriculum_version(id) ON DELETE RESTRICT,
    release_key varchar(120) NOT NULL UNIQUE,
    source_digest varchar(64) NOT NULL UNIQUE CHECK (source_digest ~ '^[0-9a-f]{64}$'),
    source_json jsonb NOT NULL CHECK (jsonb_typeof(source_json)='object'),
    imported_ids jsonb NOT NULL CHECK (jsonb_typeof(imported_ids)='object'),
    database_digest varchar(64) NOT NULL CHECK (database_digest ~ '^[0-9a-f]{64}$'),
    imported_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    reviewed_at timestamptz,
    review_note varchar(2000),
    CHECK ((reviewed_at IS NULL) = (review_note IS NULL))
);
COMMENT ON TABLE daily_career.catalog_import IS '명시적 운영자 원고 적재와 검수 이력. 자동 seed 또는 사용자 재배정을 수행하지 않는다.';
COMMENT ON COLUMN daily_career.catalog_import.curriculum_version_id IS '적재한 교육 버전; API와 운영 로그에서는 문자열 ID';
COMMENT ON COLUMN daily_career.catalog_import.release_key IS '원고 코드와 버전의 외부 식별자';
COMMENT ON COLUMN daily_career.catalog_import.source_digest IS '키 정렬된 원고 JSON의 SHA-256';
COMMENT ON COLUMN daily_career.catalog_import.source_json IS '검수 대상 원고 원본; 정답 포함, 운영자 전용';
COMMENT ON COLUMN daily_career.catalog_import.imported_ids IS '외부 ID와 생성한 DB ID 문자열의 대응';
COMMENT ON COLUMN daily_career.catalog_import.database_digest IS '적재한 본문, 정답, 편성의 DB 스냅샷 SHA-256';
COMMENT ON COLUMN daily_career.catalog_import.imported_at IS '초안 적재 시각';
COMMENT ON COLUMN daily_career.catalog_import.reviewed_at IS '원고 digest 확인 후 발행 검수를 기록한 시각';
COMMENT ON COLUMN daily_career.catalog_import.review_note IS '운영자 검수 근거; 사람의 검수를 자동으로 주장하지 않는다';
