# 구현 Gap Log

## 최신 갱신 — 작업 8 완료 (2026-09-21)

- S6-02의 문제 제출·TRUE_FALSE 저장 매핑을 해결했다. API는 기본 문제 ID, 시도·채점·신고는 불변 문제 버전을 사용한다.
- ATTEMPT-001/002와 PROBLEM-001/002, WRONG-001~005를 연결해 first/retry, 오답 누적·메모·해결·재개와 원자적 rollback을 실제 PostgreSQL로 검증했다.
- V6로 `problem_report`와 오답 해결 메타데이터를 추가했다. [작업 8 결과](problem-stage8-verification-20260921.md), [채택 기준](contracts/problem-stage8-alignment.md).
- GAP-006 운영 콘텐츠는 사용자 결정에 따라 계속 비워 둔다. 시험 제출·채점은 작업 9, AI 직접 생성 문제는 작업 12, 화면은 작업 19에 남아 있다.

## 최신 갱신 — 작업 7 완료 (2026-09-21)

- S6-03 해결: CURR-009/007과 REST-001/002의 preview/confirm을 구현했다. 시작·완료 학습 및 생성된 시험 응시를 고정하고 미래 미시작 항목의 ID·완료 이력을 보존한다.
- 특정 날짜 휴식 추가/해제는 `learning_day.source=MANUAL`, 정기 요일은 schedule_policy로 구분한다. 정책 교체 후에도 날짜 예외를 유지한다.
- V2의 schedule_policy/schedule_change와 V3 schedule_preview로 충분해 migration을 추가하지 않았다. 실제 PostgreSQL 동시성·rollback 검증 결과는 [작업 7 결과](schedule-stage7-verification-20260921.md)에 기록했다.
- 문제 제출 API/저장 매핑은 작업 8, 시험 실행은 작업 9로 남아 있다.

## 이전 갱신 — 작업 6 완료 (2026-09-21)

- 커리큘럼/학습 관련 GAP-008 매핑을 [채택 기준](contracts/curriculum-stage6-alignment.md)으로 확정했다. API 세션은 배정 항목, contentId는 개인 인스턴스이며 V5 확장 관계로 저장한다.
- S6-01: GAP-006의 운영 콘텐츠는 이번에 적재하지 않고 테스트 전용 데이터로 검증하기로 사용자가 결정했다. 운영 템플릿 0건을 유지하며 실제 콘텐츠 검토·적재는 별도 작업이다. 기능 완료와 운영 콘텐츠 준비를 구분한다.
- S6-02: 문제 제출 및 공개 TRUE_FALSE 등 문제 유형의 저장 매핑은 작업 8, 시험 응시·채점은 작업 9다. 이번에는 발행된 SINGLE_CHOICE 문제를 MULTIPLE_CHOICE 요약으로 연결하고, 본인 제출 이력에 따른 콘텐츠 완료를 검증했다.
- S6-03: 작업 7에서 해결. 위 최신 기록을 따른다.
- 일반/계약 38 + PostgreSQL IT 44 통과, 실제 서버의 빈 상태·로그아웃 후 차단, 기존 DB/계정 보존을 [결과](curriculum-stage6-verification-20260921.md)에 기록했다.

## 이전 갱신 — 작업 5 완료 (2026-09-20)

- S1-08/09 및 GAP-009의 OIDC/CSRF/로그아웃·OAuth 프록시 연결을 구현하고 자동 HTTP 및 실제 개발 Nginx로 검증했다.
- S3-01 사용자 프로필 URL 저장은 V4로 보완했다. OIDC 사용자는 필수 verified email/1..30자 닉네임을 적용하고 기존 nullable email/긴 표시명 데이터는 보존했다.
- S5-01 해결: 사용자 승인으로 Google 콘솔 및 추적 제외 `.env` 설정을 완료했다. 실제 Google 로그인·내 정보·CSRF·닉네임 무변경 PATCH·로그아웃·로그아웃 후 401을 Chrome에서 확인했다. [설정 안내](google-login-local-setup.md), [검증 결과](auth-stage5-verification-20260920.md).
- 로그인·오류·온보딩 UI는 작업 16, 학습 API별 소유권 검증은 각 구현 단계에 남아 있다.

## 최신 갱신 — 작업 4 완료 (2026-09-20)

- GAP-001/GAP-009 중 ErrorEnvelope 및 공통 응답 연결은 해결했다. 보안/MVC 오류, validation, trace·ID/revision·멱등성 기반을 구현하고 실제 HTTP 및 PostgreSQL로 검증했다. [결과](backend-stage4-completion-20260920.md).
- S1-07의 ID 입력 형식/실제 bigint 범위와 revision 범위를 구현했다. S1-08의 응답/오류 연결은 해결했지만 OIDC·CSRF 발급·로그아웃은 작업 5에 남아 있다.
- S4-01 (작업 15): Frontend 공통 decoder와 `400 VALIDATION_FAILED`/details 처리. 현재 status만 보는 mapper를 원본 오류 코드와 함께 연결해야 한다.
- S4-02 (worker 단계): 멱등성 레코드의 주기적 batch 정리와 기존 비완료 레코드 복구 정책. API 유효기간 24시간 및 수동 호출 가능한 `purgeExpired`는 구현했다.
- S4-03 (각 업무 단계): 소유권 검사 → 명시적 fingerprint → 공통 멱등성 호출, 그리고 revision 잠금/조건부 UPDATE를 실제 업무와 연결한다. 공통 테스트를 모든 업무 기능의 완료로 간주하지 않는다.
- GAP-003/006의 산식·실제 콘텐츠, GAP-008의 도메인별 API/DB 매핑은 남아 있다.

## 이전 갱신 — 작업 3 완료 (2026-09-20)

- GAP-002의 업무 DB 기준선/실행 검증은 해결했다. V2/V3로 47테이블·567컬럼·107FK를 구현하고 기존 개발 DB에 적용했다. [검증 결과](database-stage3-completion-20260920.md).
- S1-12의 migration 개수=1 단정은 V1/V2/V3 목록 검증으로 교체했다. 일반 11개 + 실제 PostgreSQL IT 8개 모두 통과했다.
- GAP-008은 부분 해결: FINAL/preview 저장, schema 분리, ID/revision 범위 및 내부/공개 상태 구분의 채택안을 기록했다. 모든 API 저장 매핑이 완료된 것은 아니다.
- S3-01 (작업 5): 필수 email·닉네임 길이·프로필 URL 저장과 검증된 OIDC 사용자 매핑.
- S3-02 (작업 22): 다음 과정 기본 휴식요일, 테마, 평일/주말 시각 저장과 초기값. 현재 과정 schedule_policy와 분리.
- S3-03 (작업 16/22): 알림 설정 집합 revision, 주간 요일, 시험 전 분 저장 보완.
- S3-04 (작업 14/15): AI 공개 목적/상태 매핑, UNKNOWN 복구 및 CANCELED 비노출.
- 위 항목과 서비스의 일정/답안 불변식은 [DB 채택 기준](contracts/database-stage3-alignment.md)에 명시했다. 이미 적용한 V1/V2/V3는 수정하지 않는다.

## 이전 갱신 — 작업 2 완료 (2026-09-20)

**GAP-005 해결.** [이슈 #1](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1)로 관리 포트 오류를 수정했고 실제 PostgreSQL IT 2개 및 일반 테스트 11개가 모두 통과했다. Compose smoke·4서비스 healthy·기존 개발 DB 이력 보존도 확인했다. [검증 보고서](docker-stage2-completion-20260920.md).

S1-12의 고정 포트 문제는 해결했으며 migration 개수=1 assertion의 업무 schema 대응은 계획대로 작업 3에 남긴다. 그 외 API/DB/콘텐츠의 미해결 항목은 유지한다.

## 작업 1에서 확인한 상태 (2026-09-20)

근거: [작업 1 결과](project-stage1-assessment-20260920.md), [원본 보관](../design-package/supplemental-20260920/README.md), [정적 집계](audit/design-baseline-20260920.json). 아래의 과거 기록을 현재 blocker로 그대로 사용하지 않는다.

| ID | 현재 판정 | 다음 행동 / 영향 작업 |
|---|---|---|
| GAP-001 | 자료 부재 해소. 실제 OpenAPI v1.2.1 확보, 91개 인터페이스·217스키마 확인 | 작업 4·5·15에서 공개 계약 연결. 전체 스키마/예시 검증과 API 구현은 아직 남음 |
| GAP-002 | 상세자료 부재 해소, 설계 정합성은 미해결. HTML v0.1의 46테이블·557컬럼·105FK 확인. v0.2 전체 원본은 미확보 | 작업 3에서 API와 상세 사전 대조 및 필요한 보완. 558 맞추기용 컬럼 추가 금지 |
| GAP-003 | 숙련도 산식 미확정 유지. OverallProgress의 네 비율은 required이며 null 허용 확인 | 작업 10·11에서 산식/분모/버전 결정. 임의 0 또는 첫 풀이 정답률 대체 금지 |
| GAP-004 | 해결 유지. 실제 PNG 15개 존재 확인 | 화면 구현 때 해당 시안과 실제 렌더링 비교 |
| GAP-005 | 작업 2에서 해결. Docker config·4서비스 healthy 및 일반 11개/IT 2개 통과 | 업무 migration에 따른 검증 확장은 작업 3 |
| GAP-006 | 운영용 콘텐츠/문항 세트 미확인 유지 | 작업 6·8·9 전에 출처·버전·배정 규칙 정리 |
| GAP-007 | 원본 패키지 자체 체크섬 문제의 과거 기록 유지 | 기존 패키지 보존. 추가 원본 2파일은 별도 해시로 동일성 확인 |
| GAP-008 | API↔DB 상세 매핑 필요 | 작업 1 결과 S1-03~07: AI 상태/목적, User/Settings 필드 출처, ID/revision 범위. 작업 3·4에서 해결 |
| GAP-009 | OAuth 프록시 및 공통 API 미연결 | S1-08~09: ErrorEnvelope·인증 경로 구현 및 Nginx OAuth 전달. 작업 4·5·15·16 |
| GAP-010 | 일부 공개 계약 설명·경계 정책 확인 필요 | S1-11의 분모 minimum=1/0 설명과 시간 경계. 작업 4·6·7·9·10에서 해소 |
| GAP-011 | 상세 UI 전체 명세 미확인 | 작업 16–22에서 화면/오버레이 목록과 핵심 시안 대조, 미정 흐름 보완 |

## 이전 감사 기록 — 당시 상태 보존

| ID | 근거 / 누락 | 영향 | 처리 |
|---|---|---|---|
| GAP-001 | API 요약과 ORIGINAL_FILE_STATUS는 File Library의 openapi.yaml을 참조하지만 ZIP/저장소에는 없음 | Phase 1 ErrorEnvelope 정확한 shape, Phase 2 인증/CSRF/로그아웃 경로, Phase 3–9 모든 공개 API/DTO/enum/required/nullable/status | 공개 엔드포인트와 schema를 추정하지 않는다. transport는 payload unknown 및 decoder 경계만 준비. 실제 v1.2.1 원본 필요 |
| GAP-002 | DB v0.2는 46테이블/558컬럼/105FK 규모·원칙만 제공 | 업무 JPA entity, repository, Flyway, outbox/idempotency 영속 구현 | 업무 테이블 생성 보류. Spring 공식 Session JDBC 인프라 스키마만 별도로 적용 가능. DB v0.2 원본 필요 |
| GAP-003 | 정적 검증 보고서: mastery 가중치/결합 산식 미확정 | Phase 7 계산 | 임의 산식/0% 대체 금지. 공개 null 표현도 원본 nullable 확인 후 적용 |
| GAP-004 | 이미지 매니페스트/시작 README는 바이너리 미포함 주장, 실제 15 PNG 존재 | 문서 충돌 | 해결: PACKAGE_STATUS와 실제 PNG를 우선. 원본 문서는 수정하지 않음 |
| GAP-005 | 현재 Docker CLI/실행 환경 없음, PostgreSQL CLI 없음 | clean PostgreSQL/Flyway/Testcontainers 및 Compose 실행 | `docker compose config` 명령 미발견. `mvnw -Ppostgres-it verify`에서 Docker 환경 미발견으로 IT 1개 ERROR. 단위 테스트/일반 build PASS와 구분. Docker 지원 환경에서 재검증 필요 |
| GAP-006 | 기능 요약은 6개월 과정만 설명하며 실제 콘텐츠/배정 규칙/문항 원본 없음 | Phase 3 이후 실제 학습 데이터 | 데모 데이터를 실데이터처럼 만들지 않는다 |
| GAP-007 | 패키지 CHECKSUMS.sha256이 자기 자신을 체크섬 대상으로 포함 | 패키지 무결성 감사 | 자기 참조 1건 불일치, 나머지 41건 일치. 원본 ZIP/문서 보존. manifest 자체 해시로 전체 실패를 판단하지 않음 |

사용자에게 문서에 이미 있는 내용을 재질문하지 않는다. 위 누락은 패키지의 과거 완료 선언과 별개로 실제 파일 부재를 근거로 기록했다.

## 개발환경 운영 기준 반영 후 추가 기록

- GAP-005 추가 확인: WSL 실행 환경도 설치되어 있지 않다. 시스템 설정 변경/설치/재부팅은 수행하지 않았다. 공식 Docker Compose 5.5.1 독립 CLI를 `.tools/`에 받아 배포 SHA256 검증 후 개발/운영 변수 조합의 `config` 검증은 통과했다. `up -d --build`는 `docker_engine` named pipe 부재로 실패했다. PostgreSQL을 호스트에 설치하는 우회 방식은 사용하지 않는다.
- Health 확인 요구는 외부 비공개 management 9090 `/actuator/health`와 Frontend 컨테이너 실행 스크립트로 구현했다. Nginx는 management 경로를 차단한다. 새 공개 업무 API를 만들지 않았으며 브라우저용 공개 Health 계약은 GAP-001의 원본 확인 대상이다.
- GAP-002/GAP-006은 그대로 유지한다. `dev-seed` profile과 migration 디렉터리만 준비했으며 미확정 업무 테이블이나 seed 콘텐츠는 생성하지 않았다.
