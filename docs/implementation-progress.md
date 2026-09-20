# 구현 진행

## 최신 진행 — 작업 9 완료 (2026-09-21)

- Next.js 공통 기반으로 OpenAPI 성공/오류 Envelope와 현재 사용자·CSRF runtime decoder를 구현했다. 64-bit ID는 문자열로 유지한다.
- `/workspace`가 CSRF와 현재 사용자를 복원하고 loading/미로그인/error/인증 상태를 구분한다. 인증된 화면은 DTO→ViewModel을 거쳐 데스크톱 좌측·모바일 하단 탐색을 렌더링하며 후속 route는 준비 중으로 비활성화했다.
- Docker build에서 typecheck, ESLint, Vitest **34개**, Next production build가 모두 통과했다. Chromium 1440×900과 375×812를 확인했고 모바일 가로 넘침은 없었다. [완료 보고서](frontend-stage9-verification-20260921.md), [계약 대조](contracts/frontend-stage9-alignment.md).
- 원격 이슈는 GitHub CLI 부재로 조회하지 못해 [이슈 초안](frontend-stage9-issue-draft.md)을 남겼다. 커밋·푸시·PR은 진행하지 않았다. 다음은 작업 10 로그인·온보딩 UI다.

## 최신 진행 — 작업 8 완료 (2026-09-21)

- [이슈 #7](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/7)로 PROBLEM-001/002, ATTEMPT-001/002, WRONG-001~005의 문제·제출·채점·신고·오답 복습 API 9개를 구현했다.
- 외부 기본 문제 ID와 내부 불변 버전을 분리하고, 첫 풀이/재시도와 여러 버전의 오답을 기본 문제 단위로 집계한다. 메모 ETag, 수동/정답 근거 해결, 재개 복습 작업을 지원한다.
- V6를 clean/순차 migration과 기존 개발 DB에 적용했다. 57테이블·631컬럼·123FK이며 기존 cluster ID/체크섬을 보존했다. 일반 38개 + 실제 PostgreSQL IT 52개, 합계 **90개 전부 통과**했고 4서비스 healthy와 smoke를 확인했다. [완료 보고서](problem-stage8-verification-20260921.md), [계약 대조](contracts/problem-stage8-alignment.md).
- 운영 콘텐츠는 사용자 결정대로 적재하지 않았고 테스트 fixture만 사용했다. 사용자 결정으로 작업 1–8은 유지하고 9단계 이후를 Backend/UI 사용자 흐름 중심으로 재구성했다. 다음은 작업 9 Next.js 공통 기반이며, 이후 10–13단계는 공통 기반과 각 API 계약을 선행 조건으로 분리해 진행할 수 있다. 커밋/푸시/PR은 진행하지 않았다.

## 최신 진행 — 작업 7 완료 (2026-09-21)

- [이슈 #6](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/6)으로 일정 변경 preview/confirm과 휴식일 추가·해제 API 4개를 구현했다.
- 미래 미시작 학습만 이동하며 시작·완료 학습과 생성된 시험 응시를 고정한다. 학습일·세션·콘텐츠 ID와 완료 이력, 특정 날짜 휴식 예외를 유지한다.
- 일반 38개 + 실제 PostgreSQL IT 49개, 합계 **87개 전부 통과**했다. 동시 확정, stale ETag, preview 만료·소비·불일치, 소유권, rollback과 재시도를 확인했다.
- 기존 V2/V3 테이블을 사용해 신규 migration은 없다. [완료 보고서](schedule-stage7-verification-20260921.md), [계약 대조](contracts/schedule-stage7-alignment.md).
- 다음은 사용자 요청 시 작업 8 문제·문제풀이이다. 작업 8, 커밋/푸시/PR은 진행하지 않았다.

## 최신 진행 — 작업 6 완료 (2026-09-21)

- [이슈 #5](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/5)로 발행 템플릿 검증·배정, 과정/월차/주차/학습일 조회, 세션 시작·콘텐츠/세션 완료 API 18개를 연결했다.
- 운영 콘텐츠는 적재하지 않고 테스트 전용 콘텐츠로 검증한다는 사용자 결정을 반영했다. 일반 38개 + 실제 PostgreSQL IT 44개, **82개 전부 통과**했다. 동시 요청의 외래키/행 잠금 교착을 재현·수정했고 중복 완료와 rollback을 확인했다.
- 기존 DB에 V5를 적용해 56테이블·618컬럼·119FK가 됐다. V1–V4, cluster ID, 계정을 보존했고 운영 템플릿/배정 콘텐츠는 0건이다. smoke·4서비스 healthy를 확인했다.
- 실제 Google 계정으로 템플릿 []·현재 과정 null·오늘 학습 null·기간 학습 []와 no-store를 확인했다. 로그아웃 후 현재 과정은 401이다. [완료 보고서](curriculum-stage6-verification-20260921.md), [운영 절차](curriculum-catalog-operations.md).
- 다음은 사용자 요청 시 작업 7 휴식·일정 변경이다. 작업 7, 커밋/푸시/PR은 진행하지 않았다.

## 이전 진행 — 작업 5 완료 (2026-09-20)

- [이슈 #4](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/4)로 허용 Google OIDC, issuer+subject 고정, 세션·CSRF 교체, 내 정보/닉네임 수정/로그아웃을 구현했다.
- 일반 38개 + 실제 PostgreSQL IT 30개, 합계 68개 모두 통과했다. 서명된 모의 공급자 인증 흐름, 오류 거절, 동시 콜백·수정 및 사용자 격리를 확인했다.
- V4를 기존 개발 DB에 적용했다. 48테이블·571컬럼·107FK이며 V1–V3 체크섬과 cluster ID를 보존했다. 실제 Nginx 응답·smoke·4서비스 healthy를 확인했다.
- 사용자 승인으로 Google 프로젝트·테스트 앱·웹 클라이언트를 설정하고 인증값을 추적 제외 `.env`에 저장했다. Chrome에서 실제 Google 로그인 후 내 정보·CSRF·닉네임 무변경 PATCH·로그아웃·로그아웃 후 401을 모두 확인했다. **작업 5 전체 완료**. [검증 결과](auth-stage5-verification-20260920.md), [설정 안내](google-login-local-setup.md).
- 다음은 사용자 요청 시 작업 6 커리큘럼·학습 운영이다. 작업 6, 커밋/푸시/PR은 진행하지 않았다.

## 이전 진행 — 작업 4 완료 (2026-09-20)

- [이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3)로 OpenAPI 공통 응답·오류, 32자리 trace/+09:00/no-store, 엄격한 JSON/ID/validation, ETag/revision과 DB 멱등성 기반을 구현했다.
- 일반·계약 테스트 36개 + PostgreSQL IT 15개, 합계 51개가 모두 통과했다. 실제 경합에서 중복 실행과 stale 갱신을 막고 업무/outbox/키 rollback을 확인했다.
- 개발 Backend 반영 및 Nginx 경유 401/403 ErrorEnvelope, smoke·4서비스 healthy를 확인했다. DB catalog와 V1–V3는 그대로다.
- [완료 보고서](backend-stage4-completion-20260920.md), [공통 계약·사용법](contracts/backend-stage4-alignment.md). 업무 API 연결·OIDC/CSRF 발급·Frontend decoder·정기 TTL 정리 worker는 각각 후속 범위다.
- 이번에는 4번에서 종료한다. 다음은 5번 인증·회원이며 커밋/푸시/PR은 수행하지 않았다.

## 이전 진행 — 작업 3 완료 (2026-09-20)

- [이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2) 기반으로 V2 업무 기준선 및 V3 일정 preview/FINAL 보완을 구현했다. 실제 업무 DB는 47테이블·567컬럼·107FK다.
- 실제 PostgreSQL에서 일반 테스트 11개 + IT 8개 전부 통과했다. 원본 catalog 전수 비교, V1 업그레이드/세션 보존, 제약 위반 거부, 답안·재채점·outbox·멱등성 저장 구조를 검증했다.
- 기존 개발 DB에 적용하고 Backend 재시작, 4서비스 healthy, 연결 smoke를 통과했다. cluster ID와 V1 체크섬을 유지했다.
- [완료 보고서](database-stage3-completion-20260920.md), [설계 채택/차이](contracts/database-stage3-alignment.md). 설정/알림/프로필의 후속 migration 및 서비스 검증 책임은 남아 있다.
- 이번에는 3번에서 종료한다. 다음은 4번 Spring Boot 공통 기반이며 커밋/푸시/PR은 수행하지 않았다.

## 이전 진행 — 작업 2 완료 (2026-09-20)

- 사용자 승인으로 [이슈 #1](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1)을 등록한 뒤 `SessionMigrationIT`의 고정 9090 호출을 실제 관리 포트 주입으로 수정했다.
- 수정 전 오류를 재현했고 수정 후 일반 테스트 11개 + 실제 PostgreSQL IT 2개를 모두 통과했다. 실패·오류·skip은 0개다.
- Compose config, 4서비스 healthy, Frontend smoke를 통과했다. 기존 개발 DB cluster ID와 Flyway 이력은 유지됐다.
- 애플리케이션 설정·Compose·업무 DB·Frontend는 변경하지 않았다. 상세 명령과 범위는 [작업 2 완료 보고서](docker-stage2-completion-20260920.md)에 기록했다.
- 커밋/푸시/PR은 수행하지 않았다. 이번에는 2번에서 종료하고 다음 대상은 3번 DB 스키마·마이그레이션이다.

## 이전 진행 — 26단계 계획의 작업 1 완료 (2026-09-20)

사용자는 세부 작업을 하나씩 진행하기로 했으며 이번 범위는 프로젝트·설계 현황 정리였다. 기존 Phase 구분과 26단계 번호는 [실행 계획](implementation-plan.md)의 대응표를 따른다.

- Downloads에서 OpenAPI v1.2.1과 DB HTML v0.1을 찾아 저장소에 원본 그대로 보관하고 SHA-256 일치를 확인했다.
- 직접 집계한 API는 91개 인터페이스·217스키마·1,036참조이며 참조 대상 누락은 0개다. DB는 46테이블·557컬럼·105FK다. v0.2 요약의 558컬럼과 차이는 기록했다.
- 현재 Docker config와 4서비스 running/healthy를 확인했다. IT 수정·재실행은 아직 수행하지 않았다.
- API/DB/현재 코드의 주요 차이와 채택 기준을 [작업 1 결과](project-stage1-assessment-20260920.md), [계약 자료 안내](contracts/README.md), [정적 집계](audit/design-baseline-20260920.json)에 정리했다.
- 애플리케이션·테스트·Compose·DB는 변경하지 않았다. 사용자 요청에 따라 1번에서 종료하며 다음 대상은 2번 Docker 개발환경 보완이다.

아래 Phase 기록은 당시 결과를 보존한 이력이다. 특히 '원본 없음'과 'Docker 환경 없음'은 최신 현황이 아니며 위 결과와 [미해결 목록](implementation-gap-log.md)의 최신 표를 우선한다.

## Phase 0 — 완료

저장소·패키지 문서 및 실제 이미지 전체 감사, 계획/gap 작성 완료. 기존 코드나 테스트가 없어 이 단계의 코드 빌드 대상은 없다. 패키지 체크섬 결과는 `audit/package-checksums.json`에 기록했다. 원본 문서는 변경하지 않는다.

## Phase 1 — 확정 가능한 기반 구현, 일부 인수 조건 차단

구현:

- Java 21 / Spring Boot 3.5.16 / Maven Wrapper 3.9.9. JPA는 validate, Flyway는 clean 금지.
- ID 전용 JsonId 직렬화/역직렬화: Java Long을 JSON string으로 유지하며 일반 숫자·revision은 변경하지 않음.
- 서버 생성 X-Trace-Id와 MDC 정리. 인증 전 기본 닫힘, CSRF 보호, 기본 로그인/로그아웃 endpoint 비활성화.
- Spring Session JDBC 공식 PostgreSQL 스키마 migration. 업무 테이블/공개 Controller 0개.
- Next 16.3.5 / React 19.3.0 / strict TypeScript / Tailwind / TanStack Query / shadcn 계열 Button.
- 실제 수치를 꾸미지 않은 반응형 시작 화면과 8종 상태 컴포넌트.
- same-origin API transport, 메모리 CSRF, ETag/If-Match, 논리 액션별 고정 payload/Idempotency-Key, 자동 mutation retry 금지.
- unknown response → decoder 경계. 오류 → ViewModel mapper → Component 연결, 412/428 재조회 UX.
- Dockerfile 2개, PostgreSQL/Backend/Frontend/Nginx Compose, CI 검증 workflow, 실행 문서.

아직 완료로 처리할 수 없는 조건:

- 공통 ErrorEnvelope 및 공개 DTO/상태 계약은 원본 OpenAPI 부재로 보류(GAP-001).
- Docker/clean PostgreSQL 검증은 실행 환경 부재로 차단(GAP-005).
- 전체 10화면, PWA, 학습 E2E는 이후 Phase 범위이며 아직 구현하지 않았다.

### 검증 결과

| 명령 / 확인 | 결과 |
|---|---|
| backend `mvnw.cmd -B -ntp verify` | PASS, 단위/MVC 테스트 8개, 실행 JAR 생성 |
| backend `mvnw.cmd -B -ntp -Ppostgres-it verify` | 환경 차단: Docker 미발견. 통합 테스트 1개 ERROR, skip 0. migration 성공 주장 없음 |
| `docker compose config` | 환경 차단: docker 명령 없음 |
| frontend `npm ci` | PASS, lockfile 재설치 성공. Windows optional package cleanup 경고 발생 |
| frontend `npm run typecheck` | PASS (`next typegen && tsc --noEmit`) |
| frontend `npm run lint` | PASS, 경고 0개 (`--max-warnings=0`) |
| frontend `npm test` | PASS, Vitest 5.0.1 / 3파일 / 27개 테스트 |
| frontend `npm run build` | PASS, production 정적 페이지 및 standalone 출력 생성 |
| standalone 서버 및 브라우저 | PASS, 127.0.0.1:3000 기동. 390/1280 viewport에서 시작 화면 확인, 수평 넘침 없음(콘텐츠 폭 375/1265), console error 0, 학습 버튼 disabled |
| npm 의존성 audit | 수정 후 알려진 취약점 0건 |
| ID 숫자 변환 / AI cancel 소스 검색 | production source에서 발견 0건. 아직 없는 업무 API의 계약 준수까지 검증한 것은 아님 |

수정한 실패: Maven Wrapper 생성 명령의 PowerShell 인수 분리, SessionRepository 내부 타입 접근 컴파일 오류, Next Link lint 오류, PostCSS default-export 경고, Vitest 취약 의존성. ESLint 10/React plugin 비호환은 검증 가능한 9.39.5 고정으로 해결(지원 종료 경고는 decisions에 기록).

브라우저 검증은 Phase 1 시작 화면만 대상으로 했다. 핵심 10화면 반응형/E2E 완료를 의미하지 않는다.
CI workflow는 작성했으며 원격 실행하지 않았다. Compose build/up 및 실제 migration은 아직 검증되지 않았다.
변경 파일 전체 목록은 `changed-files.md`에 기록한다.

## 다음 Phase

Phase 1의 원본 계약/런타임 게이트를 해소한 뒤 Phase 2 인증/세션/보안 vertical slice.
보안·세션 기술 기반은 이미 준비했지만 Google OIDC, CSRF 조회, 로그인/로그아웃은
경로·응답을 발명해야 하므로 구현하지 않았다. Phase 3–9는 OpenAPI/DB 원본에
의존하며 순서를 건너뛰어 추정 API와 업무 스키마를 만들지 않는다.

## Phase 1 추가 진행 — 회사/집 PC 개발환경 운영 기준 반영

기존 Phase 0 기록과 계획은 유지하고 관련 섹션만 증분 보완했다. 원본 설계 패키지는 수정하지 않았다.

### 추가 구현

- PostgreSQL Docker 전용, 4서비스 Compose, 서비스명 기반 주소, named volume/환경별 project·domain·bind·cookie·secret 변수.
- Node 22.22.0, Temurin JDK/JRE 21.0.12+8, PostgreSQL 17.11 및 Nginx 1.28 계열 manifest digest 고정. 각 이미지의 amd64/arm64 제공 확인, Maven Wrapper 3.9.9 유지.
- PostgreSQL → Backend → Frontend → Nginx health 기반 시작 순서. DB 포함 내부 management health, 외부 management 차단, Frontend runtime 연결 smoke script.
- 개발 seed profile/디렉터리 경계 준비. 실제 seed는 승인된 업무 DDL/콘텐츠 부재로 미생성.
- README에 신규 PC 설치/최초 실행/재실행/종료/로그/재빌드/DB 초기화·volume 삭제 절차 추가.
- CI에 4서비스 시작, smoke test, Flyway 이력 검사, down/up 후 volume 보존 비교 추가. 원격 CI는 실행하지 않았다.

### 이번 변경의 검증

| 확인 | 결과 |
|---|---|
| Backend `mvnw.cmd -B -ntp verify` | PASS, 11개 테스트 및 실행 JAR 생성 |
| 내부 management 실제 HTTP 테스트 | PASS, UP/200·모의 DB DOWN/503·상세 비노출·main port/다른 management 경로 차단. 실제 PostgreSQL 검증과는 구분 |
| Frontend typecheck / lint / build | 모두 PASS |
| Frontend `npm test` | PASS, 4파일 / 30개 테스트 (기존 27 + smoke script 검증 3) |
| Compose config (dev/prod 변수 조합) | PASS, 내부 서비스 port 비공개·named volume·healthcheck·profile/domain 분리 확인 |
| 공식 image manifests | 5개 digest 확인, 각각 Linux amd64/arm64 포함 |
| Compose `up -d --build` | BLOCKED, Docker Engine named pipe 없음. 컨테이너가 시작되었다고 보고하지 않음 |

기존 미리보기 standalone 서버가 `.next/standalone`을 점유해 첫 Frontend build가 EBUSY로 실패했다. 해당 작업에서 띄웠던 서버만 확인 후 종료하고 재빌드 PASS. Docker 기반 실행으로 전환할 수 있도록 현재 호스트 미리보기는 종료 상태다.

### 아직 통과하지 않은 실제 실행 게이트

- [ ] PostgreSQL Container 정상 실행 / clean Flyway 성공
- [ ] Spring Boot / Next.js / Nginx **컨테이너** 정상 실행
- [ ] Backend → 실제 PostgreSQL 연결
- [ ] Frontend 컨테이너 → Backend health 연결
- [ ] 재시작/재생성 후 named volume의 DB 데이터 보존

Docker Engine/WSL 실행 환경이 준비되면 README의 `docker compose up -d --build` 및 smoke 절차부터 재개한다. Phase 1 전체 완료나 Phase 2 완료로 승격하지 않는다.
