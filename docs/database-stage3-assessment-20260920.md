# DB 구현 3단계 사전 검증 결과 — 2026-09-20

## 1. 작업 결과

**P0 차단: DB/ERD v0.2 상세 정의가 없어 업무 스키마 구현은 보류했다. 3단계 완료가 아니다.**

`design-package/daily_career_codex_implementation_20260919_full/01_design_docs/04_DB_ERD/데일리_이직_DB_ERD_v0.2_복원_최종기준.md`에는 46테이블/558컬럼/105FK라는 규모, 도메인, 무결성 원칙만 있다. 테이블별 이름·컬럼·타입·제약·인덱스·생성 전략이 없다. 저장소 SQL은 기존 V1 한 개뿐이다. GAP-002 및 원본현황 문서도 상세 원본 부재를 명시한다.

기능, API, Backend DTO/Controller, Frontend TS/API Client, 통합 기준선, 정적 검증 보고서, 원본현황 및 1·2단계 결과 문서를 함께 확인했다. 이를 근거로 상세 DDL을 추정하지 않았다. Backend/Frontend/설정/기존 migration은 변경하지 않았다. 기존 미커밋 README, WORK_HANDOFF, Docker 보고서 변경을 보존했다.

## 2. 사용한 Migration 방식

기존 Flyway를 유지한다. `flyway-core`와 `flyway-database-postgresql` 버전은 Spring Boot 3.5.16 BOM 관리이며 이번 확인에서 정확한 해석 버전은 추출하지 않았다. 이미 실제 PostgreSQL에서 V1 적용과 시작 시 validation이 정상 동작하므로 도구 교체가 필요하지 않다.

- PostgreSQL: 실제 서버 17.11, DB `daily_career`, 현재 schema `public`.
- JDBC: `org.postgresql:postgresql`, BOM 관리. MyBatis/Liquibase 의존성 없음.
- JPA 사용, `ddl-auto: validate`; Flyway 활성화 및 clean 금지.
- Spring Session 자동 schema 초기화 `never`; V1이 생성 책임을 가진다.
- Datasource: `jdbc:postgresql://postgres:5432/daily_career`, 사용자/비밀번호 환경변수 주입.
- Compose: PostgreSQL health 통과 후 Backend 시작, named volume 기본명 `daily-career-dev-postgres`. 볼륨 삭제/교체 없음.
- 테스트: 기존 Testcontainers `SessionMigrationIT`, `postgres-it` Maven profile. 운영 DB와 분리된 컨테이너에서 실행하도록 구성됨.
- 필수 코드성 seed 정의 없음. dev-seed 디렉터리에 안내 문서만 있으며 실제 업무 seed 없음.

## 3. 구현된 테이블

이번에 생성한 테이블은 없다. 실제 `public`의 전체 테이블 목록:

| 영역 | 테이블 |
|---|---|
| 세션 인프라 | `spring_session`, `spring_session_attributes` |
| Migration 관리 | `flyway_schema_history` |
| 업무 | 없음: 설계상 46개 모두 미구현 |

## 4. 핵심 관계

실제 FK는 `spring_session_attributes.session_primary_id → spring_session.primary_id` 한 개이며 `ON DELETE CASCADE`다. 기존 공식 Spring Session 스키마 정책이며 업무 삭제 정책으로 확대하지 않는다. 업무 105FK 및 1:1/N:M/자기참조/nullable 관계는 상세 정의 부재로 검증할 수 없다.

## 5. ID 계약 검증

업무 bigint/Java Long/JSON string/TypeScript string 계약은 문서 간 일치한다. 그러나 업무 PK/FK가 없으므로 실제 적용 전수 검증은 불가하다. 세션 ID의 CHAR(36)은 프레임워크 인프라 계약으로 업무 bigint 대상과 분리한다. Flyway 이력 PK도 인프라용 integer다. 업무 ID 생성 전략 및 revision 타입/기본값/nullable은 확정할 근거가 없다.

## 6. 주요 Constraint

실제 catalog 확인: PK 3개(세션 2개, Flyway 1개), FK 1개, 별도 UNIQUE constraint 0개, CHECK 0개. `session_id` 유일성은 unique index로 보장한다. 세션 10컬럼 중 `principal_name`만 nullable이고 나머지 9컬럼은 NOT NULL이다. 세션 컬럼 default는 없다. 신규 업무 제약은 추가하지 않았다.

## 7. Index

실제 전체 인덱스 7개:

- `spring_session_pk`: primary_id, unique
- `spring_session_attributes_pk`: session_primary_id + attribute_name, unique
- `spring_session_ix1`: session_id, unique
- `spring_session_ix2`: expiry_time
- `spring_session_ix3`: principal_name
- `flyway_schema_history_pk`: installed_rank, unique
- `flyway_schema_history_s_idx`: success

권고: 상세 업무 스키마/조회 조건 부재로 구체적 추가 인덱스는 제안하지 않는다.

## 8. Migration 파일

1. 기존 `backend/src/main/resources/db/migration/V1__spring_session.sql` 유지. 새 파일 없음.

원본 확보 후 V1을 변경하지 않고 후속 버전부터 도메인 의존 순서에 따라 분리한다. 구체적 파일명/순서는 전체 FK를 확인한 뒤 정한다.

## 9. 기타 변경 파일

이번 작업은 이 보고서만 추가했다. dependency, 애플리케이션 설정, 테스트, Frontend 변경 없음. 원본 설계 패키지도 보존했다. secret, .env, dump, DB 데이터 파일을 추가하지 않았다.

## 10. 실제 검증 결과

| 이번 실행 명령/확인 | 결과 |
|---|---|
| `docker compose config --quiet` | PASS |
| `docker compose ps` | 4서비스 healthy |
| `docker compose exec -T backend java -cp /app/probe dev.dailycareer.infrastructure.HealthProbe`에 해당하는 컨테이너 shell 호출 | exit 0, DB health 성공 |
| psql `version(), current_database(), current_schema()` | PostgreSQL 17.11 / daily_career / public |
| `pg_tables`, `information_schema.columns`, `pg_constraint`, `pg_indexes` 조회 | 3테이블/20컬럼/4제약/7인덱스 확인 |
| `flyway_schema_history` 조회 | version 1, spring session, success=true |
| Backend 시작 로그 확인 | migration 1개 validation 성공, public version 1, up to date, 앱 시작 성공 |
| 세션/속성 row count | 각각 0, 실제 값은 조회하지 않음 |
| Backend build/test | 이번 실행하지 않음: P0로 구현 보류, 코드 변경 없음 |
| 새 DB에서 업무 migration 재현 | 미실행: 업무 migration 없음 |
| PostgreSQL/Backend 새 기동 | 이번에는 기존 healthy 서비스를 읽기 검증, 재기동하지 않음 |

초기 제한 환경에서는 Docker 접근이 거부되었으나 승인된 권한으로 읽기 검증을 재실행하여 성공했다. 비밀번호가 펼쳐지는 Compose config 출력은 사용하지 않았다.

이전 2단계 보고서에는 일반 Backend build/test 성공, postgres-it 2개 중 health 테스트 1개 오류가 기록되어 있다. 현재 테스트 코드에도 9090 하드코딩이 남아 있다. 이번에 재실행하거나 수정하지 않았고, 과거 결과를 이번 PASS로 계산하지 않는다.

## 11. DB/ERD 정합성

| 항목 | 결과 |
|---|---|
| 업무 테이블 | 기대 46, 실제 0 |
| 업무 컬럼/FK | 기대 558/105, 실제 0/0 |
| 추가 테이블 | Session 2개 및 Flyway 1개는 설계 문서에 별도 인프라로 명시 |
| 컬럼명·타입·nullable·default·PK·UNIQUE·CHECK·index·상태값 | 원본 정의 부재로 비교 불가 |
| 일정 revision | 의미만 확정, 실제 컬럼 매핑/타입/기본값 미확정 |
| 시험 제출/답안/채점 분리 | 원칙은 일치, 물리 구조 및 보존 보장 미검증 |
| 진척도 4지표 | 의미는 일치, 저장/집계 컬럼과 계산 방식 미확정 |
| 날짜/시간 | Backend 표현만 있음. DB 개별 타입·시간대 정책 미확정 |

“불일치 없음”으로 판정할 수 없다. 업무 상세 원본을 받아 항목별 기대 목록과 실제 catalog를 대조해야 한다.

## 12. 설계 충돌

- **P0 / GAP-002:** 확정 DB 설계가 있다는 요청 전제와 저장소의 복원 요약본 사이에 정보 공백이 있다. DB 문서의 규모/원칙 및 `99_원본현황/ORIGINAL_FILE_STATUS.md`의 v0.2 원본 미회수 기록이 근거다. 전체 업무 DDL 구현을 막는다.
- **GAP-001:** API 문서가 참조하는 OpenAPI v1.2.1 원본도 없다. enum/nullable 등 전체 계약 비교를 할 수 없다. 보유 문서의 ID, revision, 시험 상태 분리, 진척도 의미에는 확인된 상호 모순이 없다.
- DB 내부 상태와 공개 API 상태는 별개다. API/통합 문서는 공개 CANCELED 및 사용자 cancel API/UI를 금지한다. 내부 상태값 목록은 없으므로 CANCELED를 임의 생성하지 않는다.
- 2단계 완료 전제와 최신 인수인계의 postgres-it 미해결 기록도 다르다. Compose 정상 운영과 통합 테스트 전체 통과를 구분한다.

### 재개에 필요한 자료 및 이슈 초안

DB/ERD v0.2 원본 MD 또는 SQL을 제공받아야 한다. 전체 46테이블의 컬럼/타입/PK/FK/nullability/default/unique/check/index/삭제·수정 정책을 포함해야 한다. API 정합성 전수 확인에는 OpenAPI v1.2.1 YAML도 필요하다.

관련 확정 이슈 번호가 이번 요청에 없으므로 저장소의 이슈 기반 구현 규칙에 따라 다음 초안을 제안한다. 이슈를 외부에 생성하거나 커밋/PR을 만들지는 않았다.

- 제목: `기능: DB v0.2 업무 스키마와 Flyway 마이그레이션 구현`
- 선행 조건: DB v0.2 원본 확보, API 계약 원본 확보, 관련 이슈 확정.
- 범위: 기존 V1 유지, 후속 업무 DDL migration, 최소 설정, 기존 Testcontainers 활용 검증, 재현 가이드.
- 완료 조건: 신규 별도 DB에서 자동 migration 성공, 46테이블/558컬럼/105FK 및 모든 설계 속성 전수 대조, bigint/revision/상태 분리 확인, Backend build/test 및 Compose 검증.
- 제외: 업무 Java 로직/API/Frontend 기능, 임의 seed/설계 변경.
