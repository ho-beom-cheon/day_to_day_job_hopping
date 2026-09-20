# 데일리 이직 개발 실행 계획서

작성 기준: 2026-09-21 / 문서 버전: v0.3 / 상태: 구현하면서 갱신하는 작업 초안

현재 진행: **작업 1–9 완료**. [작업 9 결과](frontend-stage9-verification-20260921.md)에 OpenAPI decoder, 세션·CSRF 복원, DTO→ViewModel 경계, 반응형 앱 셸과 Frontend 테스트 34개를 기록했다. 작업 1–8의 Backend/API 기반 위에 후속 UI가 공통으로 사용할 화면 기반을 연결했다. 다음은 작업 10 로그인·온보딩 UI다.

## 1. 목표와 사용 방법

초안 설계서를 출발점으로 개발환경을 정리하고, 로그인부터 학습·문제풀이·시험·복습·평가까지 실제 사용할 수 있는 서비스를 완성한다. 설계의 누락이나 충돌은 해당 기능을 구현할 때 보완하고 결정 근거를 남긴다. 모든 상세설계가 완성될 때까지 전체 개발을 대기하지 않는다.

제품의 최종 목표는 금융·공공 SI 경력 개발자의 **6개월 이직 준비 학습 서비스**다. 기본 주 5일 학습과 2일 휴식을 제공하고, 개인 일정에 맞게 조정한다. 여기서 6개월은 학습 과정의 길이이며 개발 납기나 예상 공수가 아니다.

- 이 문서: 앞으로 할 작업, 의존성, 완료 기준을 관리한다.
- [진행 기록](implementation-progress.md): 실제 구현·검증 결과를 날짜와 함께 누적한다.
- [결정 기록](implementation-decisions.md): 채택한 설계와 변경 이유를 남긴다.
- [미해결 목록](implementation-gap-log.md): 미확정 사항, 영향 범위, 해소 조건을 관리한다.
- [작업 인수인계](../WORK_HANDOFF.md): 작업 종료 시 현재 위치와 다음 실행 항목을 갱신한다.

이 계획서의 항목은 실행 결과와 구분한다. 완료한 범위는 진행 기록으로 확인하며 과거 문서에 들어 있는 재개 요청문도 현재 사용자의 작업 범위를 대신하지 않는다.

## 2. 초기 출발점과 자료 확인 결과

기준 커밋은 `c61c999`이며, 계획 수립 시 별도의 미커밋 문서 작업이 있었다. 아래 표는 초기 계획의 소스 확인과 기존 실행 보고서의 결과다. 이후 작업 1에서 확인한 자료와 현재 Docker 상태는 [현황 정리 결과](project-stage1-assessment-20260920.md)를 따른다.

| 영역 | 확인된 상태 | 다음 조치 |
|---|---|---|
| 저장소 | Backend, Frontend, Compose, CI 정의와 설계 패키지가 존재 | 기존 기반 재사용 |
| Backend | Java 21 / Spring Boot 3.5.16, ID 처리·trace·보안·세션 기반 존재 | ErrorEnvelope, 업무 API, 인증 흐름 연결 |
| Frontend | Next.js 16.3.5 / React 19.3.0 / TypeScript, API client·상태 UI 존재 | 실제 로그인·학습 화면 연결 |
| DB | Spring Session용 Flyway V1만 존재 | 업무 스키마와 후속 migration 구현 |
| Docker | PostgreSQL·Backend·Frontend·Nginx 구성 존재 | 기존 구성을 유지하며 남은 통합 테스트 수정 |
| 실행 검증 | 집 PC 보고서에서 build/up, health/smoke, HMR, DB 보존 통과 | 이후 변경 범위에 맞게 재검증 |
| PostgreSQL IT | 보고서상 2개 중 1개 오류. 소스에도 관리 URL의 고정 9090이 남아 있음 | 실제 배정 관리 포트 사용 후 IT 재실행 |
| 업무 기능 | 로그인, 학습, 문제, 시험, 진척도, AI, 외부 연동 미구현 | 단계별 구현 |
| UI 자료 | 핵심 시안 목록과 PNG 15개 존재 | 화면 구현 시 렌더링 결과와 대조 |

근거: [집 PC 실행 보고서](docker-home-verification-20260920.md), [SessionMigrationIT](../backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java), [Backend 설정](../backend/pom.xml), [Frontend 설정](../frontend/package.json), [Compose](../compose.yaml).

### 설계 자료 확인 결과 — 작업 1 반영

Downloads에서 DB HTML v0.1과 OpenAPI v1.2.1을 찾아 [추가 원본 보관 위치](../design-package/supplemental-20260920/README.md)에 복사했고 원본과 해시 일치를 확인했다. 이제 공개 API의 기준은 실제 OpenAPI이며, DB는 상세 HTML과 API를 대조해 보완한다. [계약 자료 안내](contracts/README.md)를 기준으로 진행한다.

HTML을 직접 집계한 결과는 46테이블/557컬럼/105FK다. v0.2 복원 요약의 558컬럼과 차이는 남지만 어느 컬럼인지 입증할 상세 v0.2 원본은 없다. 557개는 설계 비교 출발점이며 실제 구현 검증값과 구분한다. 숫자를 맞추기 위한 컬럼 추가는 하지 않는다.

## 3. 초안 설계를 보완하는 원칙

### 작업 기준과 원본 보존

1. `design-package/`의 받은 파일은 원본 참고자료로 보존한다. 파일명에 '최종'이 있어도 현재 사용자 요청에 따라 보완 가능한 설계 초안으로 검토한다.
2. 적용할 API, DB, 화면 규칙은 작업용 설계로 구분한다. 원본 경로·버전·채택 범위와 변경점을 결정 기록에 남긴다.
3. 적용 우선순위는 **사용자의 최신 명시적 결정 → 변경 이력이 남은 작업용 계약 → 확인한 원본 상세설계 → 통합 기준선 → 분야별 요약/시안**이다. 초안이라는 이유만으로 기존 계약을 조용히 변경하지 않는다.
4. API는 경로·method·status·required·nullable·enum, DB는 컬럼·제약·관계, UI는 실제 사용자 흐름과 상태를 기능별로 함께 대조한다.
5. 원본 회수가 어려운 영역은 필요한 범위의 작업용 설계를 작성한다. 이를 '원본 복구 완료'로 표시하지 않고 새로 정한 내용과 미확정 내용을 구분한다.
6. 받은 프롬프트와 과거 문서의 완료/PASS 표기는 참고 기록이다. 현재 구현 또는 검증 완료의 증거로 대체하지 않는다.

### 진행 중 결정하는 방식

| 발견 사항 | 처리 방식 | 대기 범위 |
|---|---|---|
| 문서 오탈자, 설명 부족, 중복 표현 | 근거와 수정 내용을 기록하고 진행 | 없음 |
| 기존 계약을 유지하는 패키지 구성, 내부 구현, 테스트 방법 | 구현자가 결정하고 필요한 경우 결정 기록에 추가 | 없음 |
| API와 DB의 타입·상태·nullable 불일치 | 관련 자료를 대조하고 채택안·영향·검증 방법을 기록 | 영향받는 작업만 계약 정리 후 진행 |
| 숙련도 산식, 과정 배정 기준, 실제 콘텐츠 범위처럼 사용자 경험을 바꾸는 정책 | 선택지와 권장안을 구체화하고 필요한 사용자 결정을 받음 | 해당 정책에 의존하는 기능 |
| 기존 데이터 삭제, 운영 공개, 유료 서비스 사용 등 별도 승인이 필요한 실행 | 검토 가능한 결과와 실행 영향을 준비한 뒤 확인 | 해당 실행 |

결정 기록의 최소 항목은 `날짜 / 관련 작업·이슈 / 근거 / 결정 / 영향받는 DB·API·UI / 검증 / 남은 사항`이다. 전체 작업을 막는 문제와 일부 기능만 막는 문제를 분리한다.

### 계속 유지할 핵심 계약

- 업무 ID: PostgreSQL `bigint` → Java `Long` → JSON/TypeScript `string`. 일반 숫자와 revision까지 문자열로 바꾸지 않는다.
- 개인 업무는 인증 사용자 기준으로 접근을 제한한다. Session Cookie, CSRF, 개인화 응답의 `no-store` 정책을 적용한다.
- 수정 경쟁은 revision/ETag/If-Match로 처리한다. 누락 428, 오래된 값 412 및 화면의 재조회 흐름을 검증한다.
- 멱등성이 필요한 요청은 같은 사용자 액션의 재시도에 같은 키를 사용한다. 같은 키와 다른 payload는 409로 처리한다.
- 일정 변경은 preview → confirm이며 과거·완료·시작된 학습을 보존한다. 미래 미시작 학습을 이동하고 기본적으로 종료일을 연장한다.
- 답안 저장, 시험 제출, 채점 완료를 분리한다. 채점 실패에도 답안·제출을 보존하고 재채점마다 새 run을 만든다.
- 일정 이행률, 전체 과정 완료율, 숙련도, 첫 풀이 정답률은 별개다. 계산 불가 시 0%로 위장하지 않는다. null 표현은 작업용 API 계약과 맞춘다.
- AI는 비동기 생성/조회 흐름이며 사용자 cancel API·버튼과 공개 `CANCELED`를 추가하지 않는다. 내부 상태와 공개 상태를 구분한다.
- 업무 저장과 outbox 기록은 같은 DB 트랜잭션에 포함하고 외부 호출은 분리한다. 외부 장애로 학습·시험 성공을 취소하지 않는다.

## 4. 소스와 개발환경 운영

소스는 현재 저장소 [ho-beom-cheon/day_to_day_job_hopping](https://github.com/ho-beom-cheon/day_to_day_job_hopping)에서 함께 관리한다. 현재 구조를 유지하며 필요한 도메인을 점진적으로 추가한다.

| 위치 | 책임 |
|---|---|
| `backend/` | 인증, 업무 규칙, API, DB 접근, 비동기 작업, Backend 테스트 |
| `backend/src/main/resources/db/migration/` | 적용 순서와 이력을 보존하는 Flyway migration |
| `backend/src/main/resources/db/dev-seed/` | 개발 전용 데이터. 검토된 자료와 테스트 fixture를 구분 |
| `frontend/` | 화면, API DTO, mapper/ViewModel, 서버 상태, Frontend 테스트 |
| `infra/`, `compose.yaml` | 프록시, 이미지 고정값, 개발 실행 구성 |
| `.github/workflows/` | 빌드·테스트·실행 검증 자동화 |
| `design-package/` | 받은 설계·이미지 원본 |
| `docs/` | 실행 계획, 진행·결정·미해결 기록, 실행 가이드 |

`docs/contracts/`에는 원본 채택과 계약 변경 기준을 정리했다. OpenAPI·DB 원본 사본은 `design-package/supplemental-20260920/`에 있다. 후속 작업용 계약은 `docs/contracts/`, 보완 설계는 추가 예정인 `docs/design/`에서 관리한다. 적용된 DB 변경의 실행 기준은 Flyway이고 설계 사전은 비교 기준이다.

- 기본 실행 방식은 Docker Compose다. 새 PC에서는 Git과 Docker/Compose를 준비하며, PostgreSQL을 호스트 OS에 별도 설치하지 않는다.
- 호스트 Java/Node/Maven은 직접 빌드할 때만 필요하다. 버전 기준은 저장소의 Dockerfile, pom, lockfile, 이미지 digest다. 이번 계획에서 버전을 올리지 않는다.
- 회사와 집은 같은 Git revision을 사용하되 DB volume은 독립적이다. 공통 데이터는 migration과 검토된 seed로 재현한다.
- 이미 적용된 Flyway migration을 수정하지 않고 후속 버전으로 보완한다. 신규 DB와 기존 버전에서의 업그레이드를 모두 검증한다.
- 일반 종료는 `docker compose stop` 또는 `docker compose down`이다. 데이터 초기화는 삭제할 개발 DB/volume을 특정한 별도 작업으로 다룬다.
- `.env`, 토큰, 비밀번호, DB dump, IDE·빌드 산출물은 Git에 넣지 않는다. 공유 설정은 `.env.example`에 이름과 설명만 남긴다.
- 컨테이너 간 연결은 서비스명을 사용한다. 운영에는 별도 Compose/환경값, Frontend runtime, HTTPS, 독립 secret·volume이 필요하다.

실행 명령의 상세 기준은 [README](../README.md)와 [runtime](runtime.md)에 유지해 중복 관리를 줄인다.

## 5. 구현 순서와 사용자에게 제공할 결과

작업 1–8은 완료된 번호와 범위를 유지한다. 작업 9부터는 Backend를 모두 끝낸 뒤 화면을 붙이지 않고, 사용자 흐름별로 Backend와 UI를 연결·검증하도록 순서를 재구성했다. 프론트·백엔드 전체 통합 검증은 21단계에서 마무리한다.

| 묶음 | 포함 작업 | 완료했을 때 가능한 일 | 선행 조건 |
|---|---|---|---|
| A. 기반과 계약 정리 | 1–4, 9 | 새 환경에서 실행하고 DB/API/UI의 공통 기준으로 개발 가능 | 현재 자료·소스 대조, 관련 구현 이슈 |
| B. 첫 학습 흐름 | 5–7, 10–12 | 로그인 → 과정 배정 → 오늘 학습 → 완료 → 일정 조정 | A, 검토된 최소 학습 콘텐츠와 배정 규칙, OIDC 테스트 설정 |
| C. 풀이와 평가 | 8, 13–17 | 문제풀이 → 오답 복습 → 시험 → 결과·진척·월말평가 | B의 학습/버전 구조, 문항·평가 규칙 |
| D. AI와 외부 연동 | 18–19 | AI 피드백, Notion 동기화, 알림 설정·수신 | 필요한 업무 이벤트, outbox, 외부 테스트 설정 |
| E. 완성도와 배포 | 20–24 | 모바일을 포함한 전체 흐름을 운영환경에서 사용 | 포함 기능의 인수 조건, 배포환경 준비 |

첫 실행 가능한 범위는 B까지다. 작업 9 공통 기반을 먼저 확정한 뒤 작업 10–13은 담당 route와 feature 경계를 분리해 병렬 진행할 수 있다. 단, 작업 13 문제 UI는 완료된 작업 8 API 계약과 작업 9 공통 기반을 선행 조건으로 한다. C에서 문제·시험·복습을 포함한 핵심 학습 서비스를 완성하고, D의 부가 기능을 연결한다. AI 채점처럼 C와 D가 겹치는 부분은 C에서 job/run 및 provider 경계를 준비하고 18단계의 필요한 부분을 함께 진행한다. mock 검증만으로 실제 AI 채점 완료를 선언하지 않는다.

### 24개 작업의 범위와 완료 기준

| 번호 | 작업 | 주요 결과 및 완료 기준 |
|---|---|---|
| 1 | 프로젝트·설계 현황 정리 | 파일 위치/버전, 현재 소스, 미해결·충돌 목록, 채택 기준을 기록 |
| 2 | Docker 개발환경 보완 | 기존 4서비스 기동·smoke·HMR·영속성 유지, 고정 관리 포트 IT 오류 해소 |
| 3 | DB 스키마·마이그레이션 | 상세 사전과 DDL 대조, PK/FK/UNIQUE/CHECK/INDEX/DEFAULT/NOT NULL 검증, 신규·업그레이드 migration 통과 |
| 4 | Spring Boot 공통 기반 | OpenAPI에 맞는 응답/오류, validation, ID·trace·동시성·멱등성 기반과 계약 테스트 |
| 5 | 인증·회원 | Google OIDC, 세션, 내 정보, 로그아웃·CSRF, 사용자 간 데이터 접근 차단 검증 |
| 6 | 커리큘럼·학습 운영 | 템플릿/버전/배정, 일정 조회, 학습 세션·콘텐츠 완료와 이력 보존 |
| 7 | 휴식·일정 변경 | preview/confirm 영향 일치, 시작된 학습 고정, revision 충돌·중복 확정 방지 |
| 8 | 문제·오답·복습 | 풀이 저장·해설, 첫 풀이/재풀이 구분, 오답 이력·복습 예약·해결 상태 |
| 9 | Next.js 공통 기반 | 기존 client·Query·상태 UI 재사용, DTO→mapper→ViewModel→Component와 라우팅/레이아웃·인증 상태 정리 |
| 10 | 로그인·온보딩 UI | 로그인 성공·거부·만료, 과정 배정 및 새로고침 후 사용자 상태 복원 |
| 11 | 대시보드·오늘 학습 UI | 실제 오늘 학습·일정·현재 가능한 지표 연결, 학습 시작·완료와 데이터 없음·평가 전 표현 |
| 12 | 로드맵·휴식·일정 변경 UI | 6개월 로드맵, 휴식일 추가·해제, 일정 변경 preview/confirm과 재진입 상태 표시 |
| 13 | 문제·오답·복습 UI | 작업 8 API를 연결해 문제풀이·해설·재풀이·오답노트·복습 상태를 구분 |
| 14 | 시험·제출·채점 Backend | DAILY/WEEKLY/MONTHLY/FINAL, 답안 저장, 제출, 비동기 채점, 실패 보존·새 run 재채점 |
| 15 | 시험 UI | 시험 현황·응시·답안 저장·제출·채점 대기·실패·결과 상태 구분 |
| 16 | 진척도 4지표·월말평가 Backend | 지표별 출처·분모·기간·산식과 평가 시점의 데이터·콘텐츠 버전 및 결과 보존 |
| 17 | 진척도·평가 UI | 네 지표의 라벨·기간·평가 전 상태와 월말 결과 조회 |
| 18 | AI 기능 및 피드백 UI | provider adapter, job 생성/조회, timeout·실패·중복·사용량 처리와 실제 피드백 화면 연결 |
| 19 | Notion·Slack·Web Push 및 설정 UI | 동기화·알림 설정·권한·구독·부분실패·재시도·재연결 상태를 업무 성공과 분리 |
| 20 | 디자인 시스템·캐릭터·전체 화면 보완 | 핵심 10시안 대조, 화면 매핑, 모바일·키보드·오류 UX 확인 |
| 21 | 프론트·백엔드 통합 | 계약 불일치, 캐시 갱신, 세션 만료, 412/428, 네트워크 재시도까지 전체 흐름 확인 |
| 22 | E2E·예외·보안 검증 | 핵심 사용자 시나리오, 다중 탭 경쟁, 중복 제출, 권한/CSRF, 외부 장애 테스트 |
| 23 | Docker 운영 구성 | 개발 mount 제거, production build/runtime, HTTPS·secret·volume 분리, backup/restore·복구 절차 검증 |
| 24 | OCI 배포·최종 검증 | 환경·도메인 설정, migration, 실제 로그인/학습/시험 smoke, 로그·상태 관측, 재시작·복구 확인 |

원 설계의 UI 43화면+22오버레이 및 API 91개/217스키마는 **요약에 기재된 규모**다. 실제 상세자료와 구현 목록을 연결해 누락을 확인하며, 화면/API 개수만 맞추는 것을 완료 조건으로 삼지 않는다. PWA의 설치·업데이트·알림 흐름은 19·20·22단계에서 검증하고, 오프라인 학습 범위는 별도 설계 결정 없이 확대하지 않는다.

기존 Phase 문서는 설계 패키지의 원래 분류로 보존하되, 실제 실행 순서는 이 문서의 재구성된 1–24번을 우선한다. 작업 9 공통 UI와 작업 20 전체 화면 보완은 이후 기능 작업에서도 계속 보완한다.

## 6. 우선 착수할 작업과 미확정 항목

### 첫 작업 묶음

| 순서 | 작업 | 산출물 / 종료 조건 |
|---|---|---|
| 1 | 자료 위치와 차이 확인 — 완료 | 원본 2파일 보관·해시 검증, API/DB 직접 집계 및 557/558 차이·후속 정합성 항목 기록 |
| 2 | PostgreSQL IT 수정 — 완료 | [이슈 #1](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1), 일반 테스트 11개 + PostgreSQL IT 2개 통과, 개발환경 smoke 확인 |
| 3 | DB 상세설계·migration 이슈 — 완료 | [이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2), V1 보존 및 V2/V3 실제 적용. [결과](database-stage3-completion-20260920.md) |
| 4 | 공통 API 기반 — 완료 | [이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3), 응답/오류·validation·ID/trace·동시성·DB 멱등성. 일반 36개 + PostgreSQL IT 15개 통과 |
| 5 | 첫 학습 범위 구체화 | 과정 배정 규칙과 최소 실제 콘텐츠 선정, 로그인→오늘 학습 완료 흐름의 인수 시나리오 작성 |

1번과 2번은 독립적으로 진행할 수 있다. 3번은 상세 DB 비교 기준이 마련된 뒤 시작한다. 현재 이 문서는 이슈 번호를 부여하거나 원격 이슈가 없다고 단정하지 않는다. 구현 착수 시 관련 이슈를 조회하고, 없으면 프로젝트 규칙에 따라 이슈 초안을 먼저 제안한다.

### 결정이 필요한 내용과 필요한 시점

| 항목 | 현재 상태 / 다음 행동 | 필요한 시점 |
|---|---|---|
| 상세 DB/OpenAPI | 원본 확보 완료. 공개 API와 DB HTML의 필드/상태·범위 차이를 대조하고 보완 | 3·4단계 구현 시 |
| 숙련도 산식 | 가중치·문제/시험 결합 정책 미확정. 계산 사례를 포함한 결정안 작성 | 16단계 계산 구현 전 |
| 실제 학습 콘텐츠·문항 | 현재 저장소에서 운영 가능한 원본 세트 미확인. 출처·버전·배정 규칙 정리 | 6·8·14단계 실제 사용 검증 전 |
| 날짜·학습일 경계 | 초기 Asia/Seoul·응답 +09:00 확인. 자정·마감·휴식일과 집계기간의 상세 영향 대조 | 6·7·14·16단계의 관련 로직 전 |
| 외부 서비스 설정 | Google/OIDC 및 AI·Notion·Slack·Push의 테스트 계정·키·대상 필요 | 해당 연동 실검증 전 |
| 운영환경 | OCI 대상, 도메인, secret, 백업 보관·복구 기준 구체화 | 23·24단계 전 |

테스트용 fixture와 mock은 명확히 구분해 사용할 수 있다. 이를 실제 학습 콘텐츠, 사용자 기록 또는 실제 외부 연동 성공으로 보고하지 않는다. 계정 키가 필요한 시점에는 로컬 secret 설정을 사용하고 문서나 채팅에 값을 남기지 않는다.

## 7. 작업 한 건의 진행 절차와 Git 관리

1. 현재 Git 상태와 관련 이슈를 확인하고 기존 미커밋 작업을 보존한다.
2. 이슈에 사용자에게 제공할 기능, 포함/제외 범위, 선행 조건, 완료 기준을 구체화한다. 이슈가 없다면 구현 전에 초안을 제안한다.
3. 설계·소스 차이를 분석하고 작업용 계약 및 결정 기록에 채택안을 반영한다.
4. 필요한 브랜치에서 DB/API/화면을 작업 범위에 맞게 구현한다. 사용자 흐름 단위로 검증 가능한 크기로 나눈다.
5. 관련 테스트와 필수 검증을 실행한다. 실패·미실행·환경 차단을 구분해 기록한다.
6. 진행·결정·미해결 기록과 필요한 실행 가이드를 갱신한다.
7. 요청된 Git 작업 범위에서 커밋/PR을 준비하고 다음 범위를 명확히 한다. Draft PR은 중간 점검이며, 이미 요청된 다음 작업이 있으면 계속 진행한다.

브랜치는 기본 `codex/` 접두사를 사용한다. 커밋은 영어 Conventional Commits이며 한 목적만 담는다. PR 제목은 한글 작업 유형을 붙이고 관련 이슈를 `Closes #번호`로 연결한다. PR 본문은 프로젝트의 요약/관련 이슈/변경 내용/검증 결과/릴리스 노트 후보/유지보수자 참고사항 형식을 따른다. 실행하지 않은 검증 항목은 체크하지 않는다.

계획서 작성만으로 커밋·푸시·PR·머지·배포까지 수행한 것으로 간주하지 않는다.

## 8. 검증 및 완료 판정

### 공통 완료 기준

- 사용자 동작이 실제 저장·조회 결과와 연결된다.
- 작업용 OpenAPI, DB migration, DTO, 화면의 상태·타입·오류 처리가 일치한다.
- 관련 정상·실패·경계 조건을 검증하고 결과를 남긴다.
- Loading / Empty / Error / Disabled / Completed / Processing 및 필요한 Conflict/Stale / 외부 연동 부분실패 상태를 제공한다.
- 화면 변경은 실제 브라우저에서 모바일·태블릿·데스크톱의 가로 넘침, 버튼 접근, 폼 입력, 키보드 조작, console 오류를 확인한다.
- 검증되지 않은 부분과 후속 작업을 기록하고 이슈의 완료 조건을 충족한 뒤에만 완료 처리한다.

### 검증 명령과 실행 범위

아래 명령은 다음 구현에서 사용할 기준이며 이번 계획서 작성의 실행 결과가 아니다. 변경 범위에 맞는 검증을 먼저 하고 통합 지점에서 전체 회귀를 실행한다. 문서만 바꾼 작업은 링크·정합성·diff 검사를 수행한다.

| 대상 | 명령 / 실행 위치 | 추가 확인 |
|---|---|---|
| Backend | `./mvnw.cmd -B -ntp verify` / `backend` | Linux/컨테이너는 `sh mvnw -B -ntp verify` |
| 실제 PostgreSQL IT | `./mvnw.cmd -B -ntp -Ppostgres-it verify` / `backend` | Docker 필요. 호스트 JDK가 없으면 집 PC 보고서의 build stage 실행법 사용 |
| Frontend | `npm ci`, `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` / `frontend` | 호스트 Node가 없으면 기존 Dockerfile build stage 사용 |
| Compose | `docker compose config --quiet`, `docker compose up -d --build --wait --wait-timeout 180` / 루트 | 기존 서비스·포트·volume 상태를 먼저 확인 |
| 연결 smoke | `docker compose exec -T frontend node scripts/smoke.mjs` / 루트 | DB 포함 내부 health와 프록시 확인. 업무 E2E를 대체하지 않음 |
| DB 변경 | 별도 빈 DB migration, 이전 버전 DB 업그레이드, catalog 대조 | 제약 위반 거부, 재시작·재실행 안정성, 기존 이력 보존 |
| 최종 통합 | 브라우저 E2E 및 운영환경 smoke | 실제 연동과 mock 결과 구분, CI의 원격 실행 결과 확인 |

현재 CI 정의가 있다는 사실과 해당 변경의 CI 통과는 다르다. 테스트 수는 코드 증가에 따라 달라지므로 과거의 11개/30개 같은 수치를 고정 목표로 삼지 않는다.

### 반드시 확인할 사용자 시나리오

1. 새 사용자 로그인 → 과정 배정 → 오늘 학습 완료 → 재접속해 기록 확인.
2. 휴식일 변경 미리보기 → 확정 → 시작된 학습 보존, 다른 탭의 오래된 변경 거부.
3. 문제 첫 풀이 → 오답 등록 → 재풀이 → 첫 풀이 정답률과 복습 상태 구분.
4. 시험 답안 저장 → 제출 재시도 → 중복 제출 방지 → 채점 실패에도 제출 유지 → 재채점 성공.
5. 진척도 분모 없음·날짜 경계·평가 전 상태와 월말평가 재조회.
6. Notion/알림 장애 중 학습·시험 성공 유지 → 실패 항목만 재처리.
7. 세션 만료·CSRF 실패·다른 사용자 리소스 요청·네트워크 단절 시 적절한 오류 흐름.
8. 서비스 재시작 후 학습 데이터·미처리 작업 보존, 운영 backup/restore 결과 확인.

## 9. 진행 기록과 계획 갱신

작업 상태는 `미착수 / 진행 중 / 검증 대기 / 차단 / 완료`로 구분한다. 차단 시 원인, 영향 범위, 해소 조건을 함께 적는다. 완료율을 임의 숫자로 표현하지 않는다.

각 작업 종료 시 아래 정보를 남긴다.

```text
작업/이슈:
상태:
사용자가 할 수 있게 된 일:
설계 보완 및 결정:
검증 명령과 실제 결과:
미검증·차단·필요한 사용자 결정:
다음 작업:
```

전체 납기는 상세 DB/API 대조와 첫 학습 흐름의 작업량을 확인한 뒤 산정한다. 우선 A의 남은 일을 구체화하고 B가 실제 동작하는 시점에 C–E의 순서와 범위를 재검토한다. 이후에도 요구 변경이 생기면 근거·영향·완료 기준을 갱신하여 이 계획서를 계속 사용한다.

## 10. 참고 설계

- [통합 기준선](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_통합/IMPLEMENTATION_BASELINE_20260919.md)
- [기능 설계](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/01_기능설계/데일리_이직_기능설계_최종기준.md)
- [제작 스펙](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/02_제작스펙/데일리_이직_프로젝트_제작스펙_v1.1_최종기준.md)
- [UI/UX](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/데일리_이직_UIUX_v0.3_최종기준.md), [핵심 시안 목록](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/핵심시안_10종_완료목록.md)
- [DB 복원 기준](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/04_DB_ERD/데일리_이직_DB_ERD_v0.2_복원_최종기준.md), [API 기준](../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/05_API/API_인터페이스_v1.2.1_최종기준.md)
- [원본 Phase 구성](../design-package/daily_career_codex_implementation_20260919_full/03_codex/IMPLEMENTATION_PHASES.md), [인수 체크리스트](../design-package/daily_career_codex_implementation_20260919_full/03_codex/ACCEPTANCE_CHECKLIST.md)
