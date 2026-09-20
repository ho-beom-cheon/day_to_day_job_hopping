# 작업 1 — 프로젝트·설계 현황 정리

기준일: 2026-09-20 / 기준 커밋: `c61c999` / 브랜치: `main` / 상태: **현황 정리 완료**

## 1. 결과와 범위

기존에 제공받은 OpenAPI와 DB 상세 HTML을 Downloads에서 찾아 저장소에 보관했다. 설계 버전과 실제 규모, 소스의 구현 범위, 후속 작업의 차이와 채택 기준을 정리했다. 자료 부재 때문에 전체 개발이 대기할 필요는 없어졌으며, DB/API의 세부 정합성은 작업 3·4에서 보완할 수 있다.

이번 작업은 사용자 요청의 **1번만** 수행했다. 원본 사본·정적 집계·문서를 추가했으며 애플리케이션, 테스트, Compose, DB schema를 변경하지 않았다. 작업 2의 IT 수정, 이슈 생성, 커밋/푸시/PR은 수행하지 않았다.

## 2. 자료 위치·버전과 보관

| 자료 | 실제 확인 위치 | 상태와 채택 범위 |
|---|---|---|
| 기존 통합 패키지 | `design-package/daily_career_codex_implementation_20260919_full/` | 42파일. 분야별 설계 요약·복원본과 PNG 포함. 원본 유지 |
| OpenAPI | 원본 `%USERPROFILE%\Downloads\openapi.yaml` → [저장소 사본](../design-package/supplemental-20260920/openapi.yaml) | OpenAPI 3.1.1 / 문서 1.2.1. 공개 계약 기준 |
| DB 상세 HTML | 원본 Downloads → [저장소 사본](../design-package/supplemental-20260920/데일리_이직_DB_ERD_설계서_v0.1_20260918.html) | v0.1, 2026-09-18 검토본. 컬럼 사전·FK·제약·인덱스 후보·관계도 포함 |
| DB v0.2 | 기존 패키지 `01_design_docs/04_DB_ERD/` | 복원 요약만 존재. v0.2 상세 원본은 이번에 발견하지 못함 |
| UI/UX v0.3 | 기존 패키지 `01_design_docs/03_UIUX/` | 43화면+22오버레이 규모와 상태 원칙의 요약. 개별 상세명세는 미확인 |
| 실제 시안 | 기존 패키지 `02_assets/images/` | 핵심 11 PNG(결과/오답 분리), 추가 3 PNG, 캐릭터 1 PNG: 총 15개 |
| 실제 학습 콘텐츠·문항 | 저장소 및 관련 원본 파일 목록 | 운영용 콘텐츠 세트는 발견하지 못함. API의 예시는 콘텐츠 원본과 구분 |

두 추가 파일의 SHA-256은 Downloads 원본과 일치했다. 크기·해시·보관 경로는 [원본 보관 기록](../design-package/supplemental-20260920/README.md)과 [기계 판독 목록](audit/design-baseline-20260920.json)에 기록했다.

검색 범위는 현재 저장소 및 사용자의 Downloads/Documents와 존재하는 Desktop 경로였다. HTML이 참조하는 `schema_metadata.json`, `schema_draft.sql`, `table_dictionary.md`, `design.md`, `validation.json`, `erd_all.mmd`는 저장소와 Downloads에서 별도 파일로 발견하지 못했다. 모든 저장장치나 외부 계정에 존재하지 않는다는 뜻은 아니다. 작업에 첨부된 아티팩트 목록은 비어 있었다.

## 3. 원본을 직접 집계한 결과

### API

| 항목 | 이번 정적 확인 |
|---|---:|
| paths 경로 수 | 81 |
| method별 인터페이스 수 | 91 |
| JSON 인터페이스 / 302 전용 인터페이스 | 88 / 3 |
| `/api/v1/` 아래 인터페이스 | 89 |
| components.schemas | 217 |
| `$ref` 출현 수 | 1,036 |
| 존재하지 않는 로컬 `$ref` 대상 / 외부 `$ref` | 0 / 0 |
| 중복 operationId | 0 |
| `/cancel` 포함 경로 / `CANCELED` 포함 enum | 0 / 0 |

302 전용 3개는 `OIDC-001`, `OIDC-002`, `NOTION-007`이다. Notion callback은 `/api/v1/integrations/notion/callback`이므로 `/api/v1/` 경로 수와 JSON 인터페이스 수는 다르다.

YAML은 현재 Frontend 컨테이너에 이미 있는 `js-yaml`로 읽었다. 수집한 JSON에서 경로·operation·schema와 JSON Pointer 참조 대상을 확인했다. 예시 633개 등의 기존 정적 보고서 수치를 재실행했다고 주장하지 않는다. 이번 확인은 완전한 OpenAPI/JSON Schema 검증이나 API 런타임 검증이 아니다.

### DB

HTML의 `details.entity`마다 컬럼 사전의 실제 행과 외래키 표의 실제 행을 집계했다. 제목에 적힌 통계를 그대로 복사하지 않았다.

| 항목 | 이번 정적 확인 |
|---|---:|
| 업무·운영 테이블 | 46 |
| 컬럼 | 557 |
| FK 관계(복합 FK는 관계 1개로 집계) | 105 |
| FK 원본/대상 테이블·컬럼 누락 및 대응 타입 차이 | 0 |

| 영역 | 테이블 수 |
|---|---:|
| 사용자·인증 | 3 |
| 커리큘럼 | 4 |
| 개인 과정·일정 | 7 |
| 콘텐츠·문제 | 5 |
| 오답·복습 | 3 |
| 시험·평가 | 8 |
| AI·사용량 | 5 |
| 알림 | 4 |
| 외부 연동 | 4 |
| 운영·신뢰성 | 3 |

Spring Session 2테이블과 Flyway 이력은 이 46개에 포함하지 않는다. 복원 요약의 558과 **1컬럼 차이**가 있지만 v0.2 전체 사전이 없어 어떤 컬럼의 추가/삭제인지 입증할 수 없다. 557개를 현재 상세자료의 비교 출발점으로 삼고 향후 실제 보완은 별도 변경 목록으로 관리한다.

기계 판독 목록에는 46개 테이블의 컬럼·타입·NULL·기본값·설명과 FK를 수록했다. HTML에서 추출한 참고 데이터이며 PK/UNIQUE/CHECK/인덱스를 모두 추출한 실행 메타데이터가 아니다. 제약식의 유효성, FK 대상 키의 유일성, PostgreSQL 실행과 Flyway 적용은 작업 3의 검증 대상이다.

## 4. 현재 소스와 실행환경

| 영역 | 소스/상태 근거 | 현재 범위 및 남은 일 |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5.16, main Java 6파일·test Java 5파일 | 진입점·보안·health·ID·trace 기반. 업무 Controller/Entity 없음 |
| 인증 | [SecurityConfiguration](../backend/src/main/java/dev/dailycareer/infrastructure/security/SecurityConfiguration.java) | 내부 health 외 기본 차단. Google 로그인, CSRF 조회, 앱 로그아웃은 미연결 |
| DB | [migration](../backend/src/main/resources/db/migration), [설정](../backend/src/main/resources/application.yml) | V1 Session SQL 1개, JPA validate, Flyway clean 금지. 업무 DDL/seed 없음 |
| Frontend | [client](../frontend/src/api/client.ts), [시작 화면](../frontend/src/app/page.tsx) | same-origin, CSRF/ETag/멱등성 전달 기반과 준비 화면. 업무 DTO/decoder/화면 없음 |
| 프록시 | [Nginx](../infra/nginx/default.conf) | `/api/`는 Backend, 나머지는 Frontend. Google OAuth 두 경로는 후속 라우팅 필요 |
| 테스트 | [SessionMigrationIT](../backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java) | 9090 고정 관리 URL 2곳. migration 개수=1 단정은 업무 migration 추가 시 보완 필요 |
| CI | [.github/workflows/verify.yml](../.github/workflows/verify.yml) | Backend IT / Frontend / Compose 정의 존재. 이번 작업에서 원격 실행 결과 확인 안 함 |
| Docker 현재 조회 | Engine 29.6.1, `docker compose config --quiet`, `docker compose ps --format json` | config 성공, 4서비스 running/healthy. Nginx만 `127.0.0.1:8080`에 공개 |

Docker 조회는 처음 sandbox 권한 제한으로 실패했고 읽기 전용 조회를 허용된 실행으로 재시도해 성공했다. Docker 미설치나 서비스 장애로 분류하지 않는다. 기존 컨테이너를 재시작하거나 새로 만들지 않았다.

build/up·smoke·HMR·volume 보존과 IT 실패의 기존 실행 근거는 [집 PC 보고서](docker-home-verification-20260920.md)다. 이번에는 현재 상태만 조회했으며 IT·빌드·브라우저·DB migration을 다시 실행하지 않았다.

## 5. 확인된 차이와 처리 방침

전체 API/DB 필드 매핑을 끝낸 목록은 아니다. 작업 1에서 직접 확인한 주요 차이와 필요한 후속 분석이다.

| ID | 차이 / 근거 | 채택 기준과 처리 | 해소 작업 |
|---|---|---|---|
| S1-01 | 이전 문서는 OpenAPI/상세 DB 부재로 기록 | 사본·해시 확보로 자료 부재 해소. '상세설계 미검증'과 구분 | 이번에 해소 |
| S1-02 | DB v0.2 요약 558 vs HTML v0.1 실제 557 | HTML을 상세 비교 출발점으로 채택. 수치 맞추기용 컬럼 금지 | 3의 변경 목록 |
| S1-03 | HTML `ai_job.status`: QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED/UNKNOWN vs API AiJob: READY/PROCESSING/SUCCESS/FAILED | 내부·공개 상태를 구분. UNKNOWN/중단 상황을 포함한 변환·복구 정책 필요, cancel 공개 금지 | 3·12 |
| S1-04 | HTML job_type 설명과 공개 AiPurpose의 목적 목록이 다름 | 물리 작업 종류와 사용자 요청 목적의 매핑·저장 구조를 명시. 단순 enum 복사 금지 | 3·12 |
| S1-05 | API User.email은 필수 문자열, DB app_user.email은 nullable. 표시명 길이는 API 30, DB 80. API profileImageUrl/role/current curriculum은 저장 출처 결정 필요 | 허용 OIDC 계정 규칙·조회 조합·보관 정책을 대조. nullable/길이를 API에서 임의 확대하지 않음 | 3·5 |
| S1-06 | API Settings에 평일/주말 시작 시각·theme·studyPolicy가 있고 user_setting 사전만으로 전체 매핑이 명확하지 않음 | 관련 schedule_policy·notification_preference까지 비교해 매핑/보완 결정. API KO↔DB ko-KR 변환 구분 | 3·21 |
| S1-07 | DB bigint ID/revision, API ID는 양수 문자열·revision은 최대 2147483647. 현재 ID decoder는 타입 변환 기반 | 작업 4에서 입력 범위·오류 응답 검증. ID와 revision을 동일 타입으로 일괄 변경하지 않음 | 3·4 |
| S1-08 | OpenAPI에 CSRF/로그아웃/ErrorEnvelope가 있으나 코드에는 연결되지 않음 | 원본 경로·shape를 그대로 연결할 수 있음. 부재 blocker 대신 미구현 backlog로 전환 | 4·5·15 |
| S1-09 | Google OAuth 경로는 `/api/` 밖이지만 현재 Nginx는 Frontend로 전달 | 두 경로의 Backend 전달·보안·redirect를 인증 작업과 함께 구현 | 5·16 |
| S1-10 | 숙련도 산식 미정, 전체 진척 응답의 4비율은 null 허용 | 임의 가중치·0% 대체 금지. 분자/분모/집계기간과 버전 정책 결정 | 10·11 |
| S1-11 | API OverallProgress.totalRequiredSessions minimum=1이나 completionRate 설명에는 분모=0 처리도 명시 | DTO가 항상 과정 배정 후만 반환되는지 검토하고 빈 과정의 응답 규칙을 명시. 현 단계에서 minimum을 변경하지 않음 | 4·10 |
| S1-12 | 현재 IT의 고정 관리 포트, migration 1개 단정 | 관리 포트는 2번에서 수정·재검증. migration 검증 확장은 3번 | 2·3 |
| S1-13 | 실제 콘텐츠·문항·상세 65 UI 명세는 확보 자료에서 미확인 | 해당 기능 시작 시 범위와 출처를 정리. DTO 예시/시안을 실제 데이터로 표시하지 않음 | 6·8·9·16–22 |
| S1-14 | HTML은 외부 호출 없는 문서라고 쓰지만 외부 script src 1개 포함 | 원본은 보존하고 이번에는 실행 없이 정적 파싱. 앱 공개 자산으로 사용하지 않음 | 이번 보관 기준 반영 |

세부 일자 경계/마감 처리와 모든 상태·제약의 의미 비교는 후속 작업에 남는다. 다만 초기 시간대 `Asia/Seoul`, 응답 `+09:00`, 공개 회원가입 없음은 원본에 있으므로 같은 내용을 사용자에게 다시 결정해 달라고 묻지 않는다.

## 6. 채택 기준과 후속 진행

- 공개 API 기준: 확보한 OpenAPI v1.2.1. 모든 공개 계약을 추정해야 하는 상황은 해소됐다.
- DB 기준: HTML v0.1 상세 사전 + 통합 무결성 원칙을 출발점으로 OpenAPI와 차이를 대조한다. v0.2 완성본 보유나 그대로 배포 가능하다는 판정은 하지 않는다.
- 기존 기술스택·폴더·개발 Compose·V1은 유지한다. 업무 DB schema 제안은 `daily_career`, 기존 public의 Session/Flyway와 분리하는 안을 작업 3에서 검증한다.
- 원본 보존과 작업용 계약의 구분은 [계약 자료 안내](contracts/README.md)를 따른다.
- 사용자가 작업을 하나씩 시작하기로 했으므로 이번에는 1번을 완료하고 종료한다. 다음 요청 대상은 **2번 Docker 개발환경 보완**이다.

2번은 [기존 테스트 이슈 초안](docker-home-verification-20260920.md)의 범위를 이어받아 관련 이슈를 확인한 뒤 관리 포트를 수정하고 실제 PostgreSQL IT를 검증한다. 이번 작업에서는 이슈를 조회/등록하지 않았으며 기존의 '이슈 없음' 기록을 현재 사실로 재사용하지 않는다.

## 7. 이번 검증과 완료 체크

- [x] 자료 위치·파일 내부 버전과 원본/사본 SHA-256 일치 확인.
- [x] API 91개·217스키마·1,036참조 집계 및 참조 대상 존재 확인.
- [x] DB 46테이블·557컬럼·105FK 집계 및 FK 참조·타입 정적 대조.
- [x] 현재 소스와 설계의 주요 차이, 채택/대기 범위 기록.
- [x] Docker config 및 현재 4서비스 상태 조회.
- [x] 문서 링크, JSON 판독, diff 공백 검사 및 기존 소스·원본 패키지 보존 확인.
- [ ] 전체 OpenAPI 스키마·예시 검증, DB/API 전수 의미 대조 — 각 구현 단계에서 수행.
- [ ] Backend/Frontend 재빌드·IT·DB 적용·브라우저 E2E — 이번 현황 정리 범위에서 실행하지 않음.

미실행 항목은 이후 단계의 완료 조건이며 이번 현황 정리의 실행 성공으로 보고하지 않는다.
