# 구현 결정

## 최신 채택 기준 — 작업 8 (2026-09-21)

관련 이슈 #7. [API/DB 대조](contracts/problem-stage8-alignment.md), [검증 결과](problem-stage8-verification-20260921.md).

- DEC-20260921-27: 공개 problemId는 안정적인 `problem.id`, 시도·채점·신고의 근거는 제출 당시 불변 `problem_version.id`로 분리한다.
- DEC-20260921-28: SINGLE_CHOICE와 TRUE_FALSE는 동기 RULE 채점하며 시도·run·결과·오답 발생·복습 작업·멱등성 결과를 한 트랜잭션으로 확정한다.
- DEC-20260921-29: 오답은 사용자+기본 문제 하나로 유지한다. 여러 버전의 발생 횟수를 합산하고 최근 오답 당시 문제 버전과 답안을 상세에 사용한다.
- DEC-20260921-30: 수동 확인과 정답 재시도를 별도 해결 근거로 저장한다. 정답 재시도는 마지막 오답 뒤의 본인 정답 결과만 허용하고 재개 시 복습 작업을 생성한다.
- DEC-20260921-31: 운영 문항은 적재하지 않고 테스트 fixture로 기능만 검증한다. AI 문제와 비동기 채점은 작업 12로 미룬다.

## 최신 채택 기준 — 작업 7 (2026-09-21)

관련 이슈 #6. [API/DB 대조](contracts/schedule-stage7-alignment.md), [검증 결과](schedule-stage7-verification-20260921.md).

- DEC-20260921-23: preview는 일정 변경 없이 10분 보관하고 confirm은 사용자·과정·scheduleRevision·If-Match·action·요청 값을 다시 검증한 뒤 한 번만 소비한다.
- DEC-20260921-24: 시작·완료된 학습과 시험 응시가 생성된 학습일은 고정한다. 나머지는 dayNo 순서로 가장 이른 허용 날짜에 배치하며 learning_day와 하위 세션·콘텐츠 ID를 보존한다.
- DEC-20260921-25: 특정 날짜 휴식/학습 예외는 learning_day의 MANUAL source로, 정기 휴식요일은 기간형 schedule_policy로 구분한다. 정책 변경 후에도 날짜 예외를 날짜 기준으로 다시 적용한다.
- DEC-20260921-26: 기존 V2/V3 구조가 공개 계약과 원자적 적용에 충분하므로 migration을 추가하지 않는다. 적용·schedule_change·revision 증가·preview 소비·멱등성 결과를 같은 트랜잭션으로 처리한다.

## 최신 채택 기준 — 작업 6 (2026-09-21)

관련 이슈 #5. [API/DB 대조](contracts/curriculum-stage6-alignment.md), [검증 결과](curriculum-stage6-verification-20260921.md).

- DEC-20260921-18: 사용자 선택에 따라 운영 콘텐츠를 적재하지 않고 테스트 전용 데이터로 기능을 검증한다. 빈 템플릿 목록을 실제 준비 상태로 반환한다.
- DEC-20260921-19: templateId는 불변 curriculum_version.id, API sessionId는 learning_day_item.id다. 시간 측정 learning_session과 자기보고 분을 구분한다. 콘텐츠는 배정 인스턴스와 공유 버전을 분리한다.
- DEC-20260921-20: V5 확장 테이블과 발행 후 변경 방지 trigger를 추가하고 V1–V4를 보존한다. 명시적인 운영 검증/발행 서비스로 전체 교육 구성을 검증한다.
- DEC-20260921-21: 완료는 고정 필수 세션 분모를 조회 집계하며 반복 요청으로 시간을 더하지 않는다. 업무 변경·이벤트·멱등성 기록을 같은 트랜잭션으로 저장한다.
- DEC-20260921-22: 실제 PostgreSQL 경합에서 멱등성 FK KEY SHARE와 사용자 FOR UPDATE의 잠금 업그레이드 교착을 확인했다. 사용자 FOR NO KEY UPDATE → 과정/세션 잠금으로 직렬화하면서 FK 잠금과 양립하도록 수정했다.

## 이전 채택 기준 — 작업 5 (2026-09-20)

관련 이슈 #4. [인증·회원 계약](contracts/auth-stage5-alignment.md)과 [검증 결과](auth-stage5-verification-20260920.md)를 따른다.

- DEC-20260920-14: 허용 이메일과 verified email을 확인하되 계정 키는 issuer+subject로 고정한다. 같은 이메일의 다른 subject를 자동 연결하거나 기존 닉네임을 로그인 때 덮어쓰지 않는다.
- DEC-20260920-15: V4로 프로필 URL과 state hash 소비 기록을 추가한다. nonce/PKCE는 JDBC 세션, state 만료·동시 일회성은 DB DELETE로 보장한다. 완료 세션에는 내부 사용자 ID만 저장한다.
- DEC-20260920-16: 고정 APP_ORIGIN callback 및 고정 공급자/완료/실패 경로, 인증 쿼리 비노출 Nginx 로그를 채택한다. 운영 __Host-CAREER_SESSION과 기존 로컬 HTTP용 CAREER_SESSION을 구분한다.
- DEC-20260920-17: 모의 공급자·실제 PostgreSQL 자동 검증과 Google 실계정 검증을 구분한다. 사용자 요청에 따라 Google 콘솔 설정과 실제 로그인·내 정보·CSRF·닉네임 무변경 PATCH·로그아웃·401까지 별도로 확인한 뒤 작업 5를 완료 처리했다. OAuth 앱은 외부/테스트 상태이며 자격증명과 허용 이메일은 추적 제외 `.env`에만 설정한다.

## 최신 채택 기준 — 작업 4 (2026-09-20)

관련 이슈 #3. [공통 계약](contracts/backend-stage4-alignment.md)과 [검증 보고서](backend-stage4-completion-20260920.md)를 따른다.

- DEC-20260920-10: OpenAPI의 Envelope/Error/Meta, 32자리 서버 trace·+09:00·no-store를 MVC와 Security 계층에 일관되게 적용한다. 업무 route의 접근 차단은 유지한다.
- DEC-20260920-11: ID는 양수 Long 범위의 십진 문자열, revision은 0..2147483647 숫자로 구분한다. 미정의 필드/중복 JSON/묵시적 scalar 변환을 거부한다.
- DEC-20260920-12: 멱등성 scope는 사용자+API ID를 채택하고 대상/본문/query/전제조건은 fingerprint로 구분한다. 원본 DB 주석과 다른 채택이지만 V2는 수정하지 않는다.
- DEC-20260920-13: 짧은 DB transaction 안에서 업무/outbox/재현 결과를 함께 확정한다. 동시 요청은 transaction advisory lock으로 busy 응답을 주고 실패는 rollback한다. 24시간 유효기간과 물리 batch 정리/후속 scheduler 책임을 구분한다.

## 최신 채택 기준 — 작업 3 (2026-09-20)

관련 이슈 #2. 상세 비교와 이후 서비스 책임은 [DB 채택 기준](contracts/database-stage3-alignment.md), 검증은 [완료 보고서](database-stage3-completion-20260920.md)를 따른다.

- DEC-20260920-06: 원본 46테이블·557컬럼을 V2 기준선으로 채택하고 PK/FK/UNIQUE/CHECK 및 후보 인덱스 전체를 반영한다. V1 및 원본은 보존한다.
- DEC-20260920-07: V3에서 API 최종시험 FINAL과 일정 preview 저장을 보완한다. 결과는 47테이블·567컬럼이며 558 수치 맞추기와 무관하다.
- DEC-20260920-08: 업무 daily_career와 인프라 public을 분리하고 Flyway default-schema를 public으로 고정한다. 같은 이름의 DB 사용자로 재접속/재시작을 검증했다.
- DEC-20260920-09: 내부 넓은 상태/수치 범위와 공개 계약을 분리한다. 설정·알림·사용자 프로필의 누락 필드는 해당 기능의 후속 migration에서 보완하며 임의 제품 기본값/seed는 생성하지 않는다.

## 최신 채택 기준 — 작업 1 (2026-09-20)

아래 결정은 이번에 확인한 원본에 근거한다. 뒤에 보존된 초기 기록의 'OpenAPI 부재' 전제는 더 이상 적용하지 않는다. 현황 정리 범위에서 계약 원본을 선택했으며 업무 schema/API를 변경하지 않았다.

| 결정 | 근거와 영향 | 검증 / 남은 사항 |
|---|---|---|
| DEC-20260920-01: OpenAPI v1.2.1을 공개 계약 기준으로 채택 | Downloads에서 실제 YAML 확보, 저장소 원본 사본 보관. 경로/DTO/nullable/enum을 추정하지 않고 구현 가능 | 91개 인터페이스·217스키마·1,036참조의 대상 확인. 전체 스키마/예시 및 구현 검증은 후속 |
| DEC-20260920-02: DB HTML v0.1을 상세 비교 출발점으로 사용 | 46테이블·557컬럼·105FK 직접 집계. v0.2 요약만으로 558번째 컬럼을 만들지 않음 | API 대조·전체 제약·실행 DDL은 작업 3. 상세 원본과 다른 보완을 변경 목록으로 기록 |
| DEC-20260920-03: API와 내부 DB 표현을 구분 | AI 상태/목적, 표시명/설정·언어, ID/revision의 범위 차이 확인 | API를 DB enum/nullable에 맞춰 임의 변경하지 않고 매핑과 필요한 저장 구조 검토 |
| DEC-20260920-04: 인증·시간대는 확인한 원본 적용 | 허용된 Google 계정 로그인, 공개 회원가입 없음. 초기 Asia/Seoul·응답 +09:00 확인 | 작업 5의 OAuth 라우팅·보안 및 도메인별 일자 경계 검증 |
| DEC-20260920-05: 원본 사본과 작업용 보완 분리 | 기존 42파일 패키지 유지, 추가 원본은 `design-package/supplemental-20260920/`에 보관 | 원본 해시 동일. 보완 기준은 [contracts 안내](contracts/README.md), 차이는 [작업 1 결과](project-stage1-assessment-20260920.md) |

사용자의 최신 진행 방식은 '세부작업을 하나씩 시작'이다. 이번에는 작업 1만 완료하며 작업 2 이후를 자동으로 실행하지 않는다.

## 초기 구현 및 개발환경 결정 — 이력

- 빈 저장소이므로 backend/frontend/infra 분리. Maven Wrapper + npm lockfile 사용. Java 21을 고정한다.
- Spring Boot 3.5 계열은 Java 21을 지원하고 Jackson 2 기반의 명시적 ID serializer 구성이 가능하다. 실제 사용 버전은 pom.xml에 고정한다. [공식 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- Next.js App Router, TypeScript strict, Tailwind, TanStack Query, shadcn 계열 cva/Slot 기반 공통 컴포넌트 사용. build와 lint를 별도 실행한다. [공식 설치 문서](https://nextjs.org/docs/app/getting-started/installation)
- OpenAPI가 없는 동안 route/controller와 추정 ErrorEnvelope를 만들지 않는다. Spring 기본 로그인/로그아웃 UI도 공개 계약으로 노출하지 않는다.
- 기술 인프라 선택은 업무 계약 결정과 구분한다. Session JDBC 공식 스키마는 업무 46개 테이블과 별도다.
- 루트 화면은 실제 학습 데이터가 없는 시작 화면이다. 설계 시안의 예시 점수/진척도를 사용자 데이터로 표시하지 않는다.
- npm registry에서 Next 16.3.5 / React 19.3.0 확인 후 lockfile 고정. Vitest 3 계열 설치 시 발견한 advisory 2건은 5.0.1로 올려 해소했다. ESLint 10.11.0은 Next의 React plugin에서 `getFilename` 런타임 오류가 발생하여 작동 검증한 9.39.5로 고정했다. ESLint 9 지원 종료 경고는 남으며 React plugin의 ESLint 10 호환성 확보 후 올린다.

## 개발환경 운영 결정 (추가)

- 로컬 DB 설치 없이 Compose가 기본 실행 경로다. 기존 호스트 Maven/npm 명령은 선택적인 개발/테스트 도구이며 새 PC의 필수 설치 항목이 아니다.
- 이미지 digest는 multi-platform manifest 단위로 고정한다. 버전 업데이트는 두 PC의 공통 Git 변경으로 수행한다. Docker Desktop/Engine은 OS별 설치물이며 동일 Linux container image/runtime을 사용한다.
- readiness는 PostgreSQL → Backend → Frontend → Nginx 순서로 확인한다. [Compose 공식 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/)
- Health는 별도 내부 management port의 Spring Actuator health만 사용하고 Nginx에서 외부에 노출하지 않는다. DB indicator를 포함한 UP을 요구한다. `/api/v1` 계약은 그대로 둔다. [Spring 공식 Actuator 문서](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- 개발 seed는 `dev-seed` Spring profile에만 추가 Flyway location을 등록한다. 현재 업무 schema/콘텐츠가 없어 seed 데이터 자체는 추가하지 않는다.
