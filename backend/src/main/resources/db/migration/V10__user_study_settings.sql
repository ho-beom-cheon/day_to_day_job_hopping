ALTER TABLE daily_career.user_setting
    DROP CONSTRAINT ck_user_setting_01,
    ADD COLUMN rest_days_per_week smallint NOT NULL DEFAULT 2,
    ADD COLUMN rest_weekdays varchar(16)[] NOT NULL DEFAULT ARRAY['SATURDAY','SUNDAY']::varchar(16)[],
    ADD COLUMN weekday_study_start_time varchar(5) NOT NULL DEFAULT '19:30',
    ADD COLUMN weekend_study_start_time varchar(5) NOT NULL DEFAULT '10:00',
    ADD COLUMN theme varchar(16) NOT NULL DEFAULT 'SYSTEM',
    ADD CONSTRAINT ck_user_setting_01 CHECK (daily_goal_minutes BETWEEN 15 AND 480),
    ADD CONSTRAINT ck_user_setting_04 CHECK (rest_days_per_week BETWEEN 0 AND 6),
    ADD CONSTRAINT ck_user_setting_05 CHECK (cardinality(rest_weekdays) = rest_days_per_week),
    ADD CONSTRAINT ck_user_setting_06 CHECK (rest_weekdays <@ ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']::varchar(16)[]),
    ADD CONSTRAINT ck_user_setting_07 CHECK (weekday_study_start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    ADD CONSTRAINT ck_user_setting_08 CHECK (weekend_study_start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    ADD CONSTRAINT ck_user_setting_09 CHECK (theme IN ('LIGHT','DARK','SYSTEM'));

COMMENT ON COLUMN daily_career.user_setting.rest_days_per_week IS '다음 과정 생성에 적용할 주당 정기 휴식일 수.';
COMMENT ON COLUMN daily_career.user_setting.rest_weekdays IS '다음 과정 생성에 적용할 정기 휴식 요일. 서비스가 중복을 금지한다.';
COMMENT ON COLUMN daily_career.user_setting.weekday_study_start_time IS '한국 시간 기준 평일 기본 학습 시작 HH:mm.';
COMMENT ON COLUMN daily_career.user_setting.weekend_study_start_time IS '한국 시간 기준 주말 기본 학습 시작 HH:mm.';
COMMENT ON COLUMN daily_career.user_setting.theme IS '서버에 동기화하는 표시 테마.';
