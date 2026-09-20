# 집에서 이어서 작업하기 — 2026-09-20

## 최신 인수인계 — 작업 9 완료 (2026-09-21)

Next.js 공통 기반을 완성했다. OpenAPI Envelope·현재 사용자·CSRF를 런타임에서 검증하고 문자열 ID를 보존한다. `/workspace`는 CSRF → 현재 사용자 순서로 세션을 복원하고 loading/401/error/인증 화면을 구분한다. 인증 화면은 DTO→ViewModel 경계를 거쳐 데스크톱 좌측 탐색과 모바일 하단 탐색을 사용한다.

Docker build에서 typecheck·lint·Vitest 34개·production build가 모두 통과했다. 실제 미로그인 흐름과 Chromium 1440×900/375×812 렌더링을 확인했고 모바일 가로 넘침은 없다. [완료 보고서](docs/frontend-stage9-verification-20260921.md), [채택 기준](docs/contracts/frontend-stage9-alignment.md).

원격 이슈는 GitHub CLI 부재로 확인하지 못해 [이슈 초안](docs/frontend-stage9-issue-draft.md)을 남겼다. **다음은 작업 10 로그인·온보딩 UI**다. Google 로그인 성공·거부·만료와 과정 배정, 새로고침 복원을 이번 공통 기반 위에 구현한다. 커밋·푸시·PR은 수행하지 않았다.

## 최신 인수인계 — 작업 8 완료 (2026-09-21)

[이슈 #7](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/7)로 문제 조회·제출·즉시 규칙 채점·신고와 오답 목록·상세·메모·해결·재개 API 9개를 구현했다. 외부에는 안정적인 기본 문제 ID를 사용하고, 채점과 과거 이력은 제출 당시 불변 문제 버전에 고정한다. 여러 버전의 오답도 사용자+기본 문제 하나로 집계한다.

V6로 TRUE_FALSE 제약, 문제 신고, 오답 복습 횟수와 해결 근거 저장을 보완했다. 실제 개발 DB는 V6/57테이블·631컬럼·123FK이며 기존 cluster ID와 V1–V5 체크섬을 보존했다. 일반 38개 + 실제 PostgreSQL IT 52개 = **90개 모두 통과**했고 4서비스 healthy와 smoke도 확인했다. [완료 보고서](docs/problem-stage8-verification-20260921.md), [채택 기준](docs/contracts/problem-stage8-alignment.md).

사용자 결정대로 운영 콘텐츠는 적재하지 않았고 테스트 fixture만 사용했다. 작업 1–8의 번호와 범위는 유지하고, 작업 9 이후는 Backend/UI를 사용자 흐름별로 완성하도록 재구성했다. AI 기능은 새 작업 18, 문제 화면은 새 작업 13이다. **다음은 작업 9 Next.js 공통 기반**이다. 작업 8은 커밋·푸시·PR하지 않았다.

## 최신 인수인계 — 작업 7 완료 (2026-09-21)

[이슈 #6](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/6)으로 CURR-009/007, REST-001/002를 구현했다. 일정 변경은 10분 preview 뒤 1회 확정하며, 휴식일 추가·해제, 현재 과정 정책 교체, 밀린 일정 당기기를 지원한다. 시작·완료 학습과 생성된 시험 응시는 고정하고 미래 미시작 학습만 dayNo 순서로 옮긴다. 학습일·세션·콘텐츠 ID와 완료 이력은 유지한다.

V2/V3의 schedule_policy, schedule_change, schedule_preview 구조가 충분해 새 migration은 없다. 일반 38개 + 실제 PostgreSQL IT 49개 = **87개 모두 통과**했다. 동시 확정, 만료·소비·불일치 preview, stale ETag, 다른 사용자 접근, 감사 이력 실패 rollback과 같은 키 재시도를 검증했다. [완료 보고서](docs/schedule-stage7-verification-20260921.md), [채택 기준](docs/contracts/schedule-stage7-alignment.md).

**다음은 사용자 요청 시 작업 8 문제·문제풀이**다. 작업 7은 커밋·푸시·PR하지 않았다. 운영 콘텐츠와 학습 화면도 아직 없다.

## 최신 인수인계 — 작업 6 완료 (2026-09-21)

[이슈 #5](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/5)로 커리큘럼·학습 운영 API 18개를 구현·적용했다. 발행 템플릿의 개인 배정, 교육 모듈/날짜 조회, 세션 시작, 콘텐츠 조건 확인 및 자기보고 시간으로 완료하는 기능이다. 일반 38개 + PostgreSQL IT 44개 = **82개 모두 통과**했다. [완료 보고서](docs/curriculum-stage6-verification-20260921.md).

실제 DB는 V5/56테이블·618컬럼·119FK이며 V1–V4 체크섬과 cluster ID 및 기존 계정을 보존했다. 사용자 결정대로 운영 콘텐츠는 적재하지 않았다. 테스트 fixture는 운영 JAR에 없으며, 실제 Google 로그인 후 템플릿 []·현재 과정/오늘 학습 null·기간 조회 []와 로그아웃 후 401을 확인했다. 임시 검증 페이지는 제거했다. 4서비스 healthy와 smoke도 통과했다.

**다음은 사용자 요청 시 작업 7 휴식·일정 변경**이다. [작업 6 API/DB 채택](docs/contracts/curriculum-stage6-alignment.md)을 먼저 읽는다. API sessionId는 시간 측정 learning_session이 아닌 learning_day_item.id다. 고정 학습일·세션·콘텐츠 인스턴스 ID와 완료 이력, 필수 분모를 보존하며 preview/confirm을 구현해야 한다. 사용자 잠금은 외래키 KEY SHARE와 양립하는 FOR NO KEY UPDATE를 사용한다.

[교육 템플릿 운영](docs/curriculum-catalog-operations.md)에 검토된 DRAFT 자료의 검증/발행 명령을 기록했다. 운영 콘텐츠 작성, 후속 UI, 작업 7 및 커밋/푸시/PR은 수행하지 않았다. 아래는 이전 상태의 이력이다.

## 이전 인수인계 — 작업 5 완료

[이슈 #4](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/4)로 인증·회원 구현과 자동 검증, 개발 서버 적용을 마쳤다. 일반 38개 + PostgreSQL IT 30개 = 68개 모두 통과했다. 실제 DB는 V4/48테이블·571컬럼이며 기존 V1–V3/cluster ID를 보존했다. [검증 결과](docs/auth-stage5-verification-20260920.md).

사용자 승인으로 Google 프로젝트 `Daily Career` (`daily-career-509214`)와 웹 클라이언트 `Daily Career Local`을 생성했다. 외부/테스트 앱이며 승인 계정 1개를 테스트 사용자와 서버 허용 계정에 등록했다. 로컬 `.env`에 GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET/GOOGLE_ALLOWED_EMAILS를 저장하고 Backend에 적용했다. 자격증명·개인 이메일은 문서나 Git에 기록하지 않는다. callback은 `http://localhost:8080/login/oauth2/code/google`다. 새 PC 설정은 [Google 콘솔 설정 안내](docs/google-login-local-setup.md)를 따른다.

Chrome에서 실제 Google 기본 프로필 동의 후 로그인 성공과 내 정보 200·CSRF 200·닉네임 무변경 PATCH 200·로그아웃 200·로그아웃 후 내 정보 401을 확인했다. 임시 검증 페이지는 제거했으며 앱 세션은 로그아웃 상태다. 4서비스 healthy와 smoke도 통과했다. **다음은 사용자 요청 시 작업 6 커리큘럼·학습 운영**이다. 작업 6과 커밋/푸시/PR은 진행하지 않았다. 아래는 이전 상태의 이력이다.

## 이전 인수인계 — 작업 4 완료

[이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3)로 공통 Envelope/Error, trace/time/no-store, JSON/ID/validation, ETag/revision과 DB 멱등성 기반을 구현했다. 일반·계약 36개 + PostgreSQL IT 15개, 합계 51개가 모두 통과했고 개발 Backend 적용·실제 401/403 응답·smoke·4서비스 healthy를 확인했다. DB catalog와 V1–V3는 그대로다. [완료 보고서](docs/backend-stage4-completion-20260920.md).

**다음은 사용자 요청 시 5번 인증·회원**이다. Google OIDC/허용 계정/세션·내 정보/CSRF 발급/로그아웃, Nginx OAuth 두 경로를 연결한다. 실제 외부 검증에 필요한 설정은 로컬 secret으로 관리하며 문서·채팅에 값을 남기지 않는다.

[공통 계약·사용법](docs/contracts/backend-stage4-alignment.md)에 따라 업무 Controller에 응답 팩토리와 validation을 연결한다. 재시도에도 소유권을 확인하고, 멱등성 scope는 사용자+API ID로 유지하며 대상/본문/전제조건을 fingerprint에 넣는다. 도메인 갱신은 DB 잠금/조건부 UPDATE와 결합해야 한다. TTL 정리 scheduler와 Frontend decoder는 후속 범위다.

커밋/푸시/PR은 수행하지 않았으며 이슈 #3은 열려 있다. 이번에는 작업 4까지만 진행했다. 아래 '공통 응답 미구현' 등의 내용은 과거 이력이다.

## 이전 인수인계 — 작업 3 완료

[이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2)로 V2 업무 DB 기준선과 V3 일정 preview/FINAL을 구현·적용했다. 업무 schema는 47테이블·567컬럼·107FK다. 기존 V1과 개발 volume을 보존했고 일반 테스트 11개 + PostgreSQL IT 8개, 4서비스 healthy, smoke 및 Backend 재시작을 통과했다. [완료 보고서](docs/database-stage3-completion-20260920.md).

**다음은 사용자 요청 시 4번 Spring Boot 공통 기반**이다. OpenAPI v1.2.1의 응답/오류·validation·동시성·멱등성을 구체화한다. [DB 채택/차이](docs/contracts/database-stage3-alignment.md)에 기록한 설정/알림/프로필 저장 보완과 서비스 책임을 확인한다. 현재 업무 API/화면/seed는 구현하지 않았다.

Flyway 이력은 `public`으로 고정했으며 업무 테이블은 `daily_career`다. V1/V2/V3를 수정하거나 다시 생성하지 말고 후속 migration을 추가한다. 커밋/푸시/PR은 수행하지 않았고 이슈 #2는 열려 있다. 아래 '업무 DDL 없음' 등은 이전 상태를 보존한 기록이다.

## 이전 인수인계 — 작업 2 완료

[이슈 #1](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1)을 등록하고 `SessionMigrationIT`의 고정 관리 포트 오류를 수정했다. 일반 테스트 11개와 실제 PostgreSQL IT 2개가 모두 통과했고, Compose smoke·4서비스 healthy·기존 개발 DB 이력 보존을 확인했다. [작업 2 완료 보고서](docs/docker-stage2-completion-20260920.md).

사용자 요청에 따라 이번에는 2번만 완료했다. **다음 요청 대상은 3번 DB 스키마·마이그레이션**이며 [DB 이슈 초안](docs/database-stage3-issue-draft.md), [작업 1 차이 목록](docs/project-stage1-assessment-20260920.md), [계약 기준](docs/contracts/README.md)을 읽고 시작한다. 업무 DDL은 아직 없다. 커밋/푸시/PR은 수행하지 않았으며 이슈 #1은 열어 두었다.

아래 이전 인수인계의 'IT 오류가 남음'과 '2번을 다음에 진행'은 과거 상태다. 최신 계획과 이번 보고서를 우선한다.

## 이전 인수인계 — 작업 1 완료

사용자가 세부 작업을 하나씩 진행하기로 했으며 **이번에는 프로젝트·설계 현황 정리(1번)만 완료**했다. 자세한 내용은 [작업 1 결과](docs/project-stage1-assessment-20260920.md)와 [계약 자료 안내](docs/contracts/README.md)를 읽는다.

- Downloads의 `openapi.yaml` v1.2.1과 DB 상세 HTML v0.1을 `design-package/supplemental-20260920/`에 원본 그대로 보관했다. 'OpenAPI/상세 DB 자료 없음'은 해소됐다.
- 직접 집계: API 91개·217스키마·1,036참조, DB 46테이블·557컬럼·105FK. 558컬럼인 v0.2 복원 요약과 차이는 후속 보완 기록으로 관리한다.
- Docker config 성공과 4서비스 running/healthy를 현재 조회했다. 애플리케이션·테스트·DB 변경 및 재빌드는 하지 않았다.
- 다음은 사용자의 **2번 시작 요청**에 따라 관련 이슈 확인 → 고정 관리 포트 수정 → 실제 PostgreSQL IT 재검증이다. 3번 업무 DDL을 함께 시작하지 않는다.
- 아래 기존 인수인계는 과거 작업의 상세 이력이다. 원본 부재·Docker 부재 기록과 9절의 재개 요청문은 위 최신 상태 및 사용자의 현재 요청을 우선한다.

이 문서 하나로 현재 상태를 파악하고 개발환경 실행 및 다음 작업을 이어갈 수 있도록 정리했다. 저장소는 `https://github.com/ho-beom-cheon/day_to_day_job_hopping`, 작업 브랜치는 `main`이다. 기존 기반 커밋은 `399fd2f`이며, 이 문서와 함께 오늘의 미커밋 변경을 저장한다.

## 1. 현재 어디까지 했나

**집 PC에서 Docker build/up, 4서비스 health/smoke, 실제 PostgreSQL/Flyway, 브라우저 HMR 및 down/up 데이터 보존을 검증했다. postgres-it는 관리 포트 하드코딩으로 IT 2개 중 1개 오류가 남아 2단계 전체 완료 판정은 보류한다. 다음 작업은 관련 이슈 확정 후 테스트 수정과 재검증이다.**

최신 실행 근거 및 이슈 초안: [집 PC 검증 보고서](docs/docker-home-verification-20260920.md). 아래 이전 PC 검증 기록과 구분한다.

- 6개월 학습·시험·복습 서비스인 ‘데일리 이직’을 개발 중이다.
- 저장소는 빈 프로젝트가 아니다. Backend/Frontend 공통 기반, 테스트, Compose, CI 정의, 설계 패키지가 있다.
- 로그인/회원, 실제 학습·문제·시험·진척도·AI·외부 연동은 아직 구현하지 않았다. 시작 페이지는 준비 상태 안내 화면이며 실제 대시보드가 아니다.
- 기존 문서의 Phase 0/1과 사용자가 공유한 26단계 개발 순서는 서로 다른 분류다. ‘Phase 1 기반 구현’이 ‘26단계 전체 기능 완료’를 의미하지 않는다.
- 이전 보고서는 당시 상태를 기록한다. 특히 1단계 readiness 문서의 ‘소스 mount 없음’은 이후 Docker 2단계에서 변경되었다. 현재 상태는 이 문서를 기준으로 재개한다.

## 2. 구현 및 오늘 변경한 내용

| 영역 | 저장된 내용 |
|---|---|
| Backend 기반 | Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.9, JPA validate/Flyway clean 금지, ID JSON string 처리, trace/MDC, 보안·CSRF 기반, 내부 health |
| 세션/DB | Spring Session JDBC 공식 PostgreSQL migration만 존재. 업무 테이블·Entity·seed는 아직 없음 |
| Frontend 기반 | Next.js 16.3.5, React 19.3.0, TypeScript strict, Tailwind, TanStack Query, 공통 Button/상태 UI, 시작 화면 |
| API 공통부 | same-origin `/api/`, CSRF 메모리 관리, ETag/If-Match, 논리 액션별 Idempotency-Key, 자동 mutation retry 금지, unknown/decoder 경계, 오류 UX |
| Docker 구성 | PostgreSQL/Backend/Frontend/Nginx 4서비스, 이미지 digest 고정, health 기반 시작 순서, PostgreSQL named volume |
| 오늘의 Docker 변경 | Frontend development stage, `frontend/src` 읽기 전용 mount, webpack polling, Nginx HMR WebSocket 전달, 기존 production stage 유지 |
| 점검 중 수정 | 양쪽 `.dockerignore`에 환경파일 제외 보완, SessionMigrationIT의 이미지 표기를 같은 digest의 digest-only 형식으로 수정 |
| IDE 실행 | `.run/`의 Full Stack/Build All/Stop All 설정과 `scripts/ide.ps1` 추가 |
| 문서 | 개발 준비 점검, Docker 2단계 결과, README/실행 방법 갱신, 본 통합 인수인계 문서 |

## 3. 집 PC에서 처음 실행하기

Git과 Docker Desktop(Linux containers)을 준비하고 Docker를 실행한다. Windows에서는 Docker 실행에 필요한 WSL 2/가상화 환경도 준비해야 한다. Compose 실행만 할 때 호스트 Java/Node/Maven/PostgreSQL 설치는 필요 없다. 최초 빌드는 네트워크와 다운로드 시간이 필요하다.

새 폴더에 받는 경우:

```powershell
git clone https://github.com/ho-beom-cheon/day_to_day_job_hopping.git
cd day_to_day_job_hopping
git switch main
git log -1 --oneline
```

이미 저장소가 있는 경우, 미커밋 작업이 있다면 먼저 보존한 뒤:

```powershell
git status --short
git switch main
git pull --ff-only origin main
git log -1 --oneline
```

이후 저장소 루트에서 실행한다. Windows에서는 아래 스크립트가 가장 간단하다. `.env`가 없으면 독립적인 로컬 DB 비밀번호를 자동 생성하며, 기존 `.env`는 덮어쓰지 않는다.

```powershell
docker version
docker compose version
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Up
```

성공하면 브라우저에서 **http://localhost:8080**을 연다. 스크립트는 build/up/health 대기를 수행하며 컨테이너는 백그라운드에서 계속 실행된다. 기존 `.env`의 `DATABASE_PASSWORD`가 비어 있다면 직접 채워야 한다.

스크립트 대신 직접 실행하는 방법(Windows PowerShell):

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# .env의 DATABASE_PASSWORD를 집 PC용 값으로 직접 설정한 다음 실행
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
docker compose exec -T frontend node scripts/smoke.mjs
```

macOS/Linux에서는 환경파일이 없을 때 `cp .env.example .env`로 복사한 뒤 비밀번호를 채우고 동일한 Docker 명령을 사용한다. `.run`과 `ide.ps1`은 Windows용이다.

| 접속/설정 | 값과 주의점 |
|---|---|
| 브라우저 | `http://localhost:8080` — 포트 충돌 시 `.env`의 `HTTP_PORT` 변경 |
| Backend 업무 API 경로 | Nginx의 `/api/` 전달 경로만 준비됨. 실제 업무 API/로그인은 미구현이며 보안 차단 응답이 나올 수 있음 |
| 내부 Backend health | `http://backend:9090/actuator/health`, 컨테이너 내부 전용. 브라우저 공개 경로가 아님 |
| DB | `postgres:5432`, 기본 DB/사용자 `daily_career`. 호스트 PostgreSQL 설치 불필요 |
| 환경값 | `.env.example`이 기준. `GOOGLE_CLIENT_ID/SECRET`은 현재 실행에 불필요 |
| 데이터 | 회사/집 DB는 별개이며 Git으로 동기화되지 않음. 집에서는 빈 volume에 migration부터 적용 |

실제 `.env`, API secret, DB dump, `.tools`, `.idea`, node_modules/빌드 결과물은 Git에 포함하지 않는다. 회사 PC의 IntelliJ JDK 연결 설정도 집 PC로 자동 복사되지 않는다.

## 4. 집에서 가장 먼저 완료할 검증

집 PC에서 아래 다섯 항목은 통과했다. postgres-it만 실패가 남아 있으며 상세 결과는 최신 집 PC 검증 보고서를 따른다.

- [x] `docker compose up -d --build --wait --wait-timeout 180` 성공 및 4서비스 정상 상태.
- [x] 브라우저 시작 화면 표시, Frontend 컨테이너 smoke 성공.
- [x] Backend → 실제 PostgreSQL 연결 및 clean Flyway migration 성공.
- [x] `frontend/src`의 화면 문구를 임시 수정했을 때 브라우저에 반영되는지 확인 후 수정 복구.
- [x] 아래 down/up 전후 DB cluster ID와 Flyway 이력 동일, smoke 재통과.
- [ ] Backend `postgres-it`: 실제 PostgreSQL migration/세션 저장 통과, health 테스트는 관리 포트 34343 대신 고정 9090 호출로 오류. 이슈 초안은 최신 보고서 8절 참조.

기본 DB/사용자 기준 migration 및 영속성 확인 명령이다. `.env`에서 이름을 바꿨으면 `-U`/`-d` 값도 바꾼다. 첫 번째와 두 번째 조회 결과를 비교한다.

```powershell
docker compose exec -T postgres psql -U daily_career -d daily_career -c 'SELECT system_identifier FROM pg_control_system(); SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;'
docker compose down
docker compose up -d --wait --wait-timeout 180
docker compose exec -T postgres psql -U daily_career -d daily_career -c 'SELECT system_identifier FROM pg_control_system(); SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;'
docker compose exec -T frontend node scripts/smoke.mjs
```

일반 종료는 `docker compose stop` 또는 `docker compose down`이다. **`down -v`/`down --volumes`는 DB 데이터를 삭제하므로 일반 종료에 사용하지 않는다.** 기존 volume의 DB 비밀번호는 `.env` 값 변경만으로 바뀌지 않는다.

오류가 나면 `docker compose ps`와 `docker compose logs --tail=100 backend frontend postgres nginx`를 확인한다. Docker 연결 오류는 Desktop/Engine 실행 여부부터 확인하고, 기동 제한시간 초과는 로그를 확인한 뒤 wait 명령을 재시도한다. 서비스 재생성 후 프록시 연결 오류가 지속되면 `docker compose restart nginx`를 실행한다.

## 5. 평소 실행/개발 및 IntelliJ

| 작업 | 명령/설정 |
|---|---|
| 재실행 | `docker compose up -d` |
| 전체 변경 반영 | `docker compose up -d --build` |
| Backend 변경 반영 | `docker compose up -d --build backend` |
| Frontend src 변경 | 개발 서버가 mount/polling으로 감지. 실제 Docker HMR 검증은 위 체크리스트에서 진행 |
| Frontend 의존성/설정/scripts 변경 | `docker compose up -d --build frontend` |
| 로그 | `docker compose logs -f --tail=100` |
| 전체 중지 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Stop` |
| 호스트 빌드 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Build` |

IntelliJ의 `Run → Edit Configurations`에서 `Daily Career - Full Stack`, `Daily Career - Build All`, `Daily Career - Stop All`을 사용한다. 설정이 표시되지 않으면 Shell Script 플러그인 활성화 여부를 확인하고 위 PowerShell 명령으로 직접 실행할 수 있다.

호스트 Build All은 **JDK 21/JAVA_HOME 및 Node.js 22.22.0/npm**이 필요하다. Backend Maven verify 후 Frontend npm ci/build를 수행하며, 별도의 Frontend lint/unit test 전체 실행을 대체하지 않는다. IntelliJ에서는 JDK 21을 설정하고 `backend/pom.xml`을 Maven 프로젝트로 연결한다. Maven 별도 설치는 필요 없다.

개별 검증(각 디렉터리에서 실행):

```powershell
# backend/
.\mvnw.cmd -B -ntp verify
# Docker Engine 실행 필요
.\mvnw.cmd -B -ntp -Ppostgres-it verify

# frontend/
npm ci
npm run typecheck
npm run lint
npm test
npm run build
# 기본 테스트에서 worker timeout이 재현되면 원인을 기록하고 단일 worker도 확인
npm test -- --maxWorkers=1
```

## 6. 지금까지의 검증 결과와 한계

아래는 2026-09-20 선행 작업에서 실행한 결과를 통합한 것이다. 본 문서 작성 작업에서 전부 다시 실행한 결과는 아니다.

| 검증 | 결과 |
|---|---|
| Backend 일반 verify | PASS, 테스트 11개 및 실행 JAR 생성 |
| Frontend typecheck/lint/build | PASS |
| Frontend 단위 테스트 | 1단계 30개 PASS. 2단계 기본 병렬 실행은 worker timeout으로 실패했고, `--maxWorkers=1`은 4파일/30개 PASS. 원인은 미확정 |
| Windows Build All 스크립트 | PASS, Backend verify 및 Frontend 설치/build |
| Compose 구성 | 독립 Compose 5.5.1에서 config PASS, 비밀번호 누락 시 예상대로 실패 |
| 호스트 Next 개발 서버 | 3300 포트 기동/HTTP 200 PASS 후 종료. Docker/Nginx HMR 검증은 아님 |
| PostgreSQL 통합 테스트 | 이미지 식별 오류 수정 후 Docker 환경 부재로 실패. migration 성공으로 처리하지 않음 |
| Compose 실제 build/up/ps | 현재 PC에 Docker CLI/Engine 및 WSL 실행환경이 없어 차단 |
| CI | workflow 정의는 있음. 원격 CI 통과 여부는 확인하지 않음 |

## 7. 다음 기능 개발 전에 필요한 자료와 유지할 계약

Docker 검증은 아래 자료 없이 진행 가능하다. 그러나 업무 DB/API 구현은 원본 확보 전 추정해서 만들지 않는다.

| 미해결 항목 | 영향/처리 |
|---|---|
| GAP-001: OpenAPI v1.2.1 원본 YAML 없음 | ErrorEnvelope, 공개 DTO/enum/nullable, 인증·CSRF·로그아웃 및 업무 API 경로 확정 불가. 원본 확보 필요 |
| GAP-002: DB v0.2 원본 상세 DDL 없음 | 46테이블/558컬럼/105FK의 요약만 있음. 업무 Entity/Flyway 생성 전 원본 확보 필요 |
| GAP-003: mastery 계산 산식 미확정 | 임의 산식이나 0% 대체 금지 |
| GAP-006: 실제 학습 콘텐츠/문항/배정 규칙 원본 없음 | 임의 데모 데이터를 실제 학습 데이터처럼 구현하지 않음 |
| GAP-005: 통합 테스트 게이트 | Compose 실행 검증 통과. postgres-it 관리 포트 오류 수정·재검증 필요 |

개발 중 유지할 핵심 원칙: 업무 ID는 JSON/Frontend string ↔ Backend Long 경계를 유지하고 숫자로 변환하지 않는다. 진척도 네 지표를 혼용하지 않는다. AI 사용자 cancel API/버튼과 공개 CANCELED를 임의 추가하지 않는다. revision/ETag 경쟁 제어와 시험 답안 보존·재채점 계약을 유지한다. 원본 설계 패키지를 임의 수정하지 않는다.

설계 패키지는 `design-package/daily_career_codex_implementation_20260919_full/`에 있다. 일부 문서는 이미지 미포함이라고 적지만 실제 PNG 15개가 존재한다. 과거 완료 선언/정적 검사 PASS는 원본 계약 파일 존재나 현재 런타임 통과를 뜻하지 않는다.

## 8. 사용자가 공유한 전체 개발 순서

1. 프로젝트 현황 분석 + 개발환경 검증
2. Docker 개발환경 구축 — 실제 Compose 검증 통과, postgres-it 오류 해결이 남은 지점
3. DB 스키마 / 마이그레이션 — 원본 DDL 확보 필요
4. Spring Boot 공통 기반
5. 인증/회원
6. 커리큘럼/학습 일정
7. 휴식일/일정 변경
8. 문제/문제풀이
9. 시험/제출/채점
10. 진척도 4대 지표
11. 월말 평가
12. AI 기능
13. Notion 연동
14. 알림/푸시
15. Next.js 공통 기반
16. 로그인/온보딩 UI
17. 대시보드
18. 학습 일정/오늘 학습
19. 문제/시험 UI
20. 진척도/월말평가 UI
21. 설정/휴식일/연동 UI
22. 캐릭터/디자인 시스템 최종 적용
23. 프론트-백엔드 통합
24. E2E/예외/보안 테스트
25. Docker 운영 구성
26. OCI 배포 및 최종 검증

이미 있는 공통 기반을 재사용하면서 순서대로 진행한다. 현재 Compose는 개발용이며, 운영용 Frontend runtime 선택/별도 Compose, HTTPS, 운영 secret/volume 분리와 OCI 검증은 아직 남아 있다.

## 9. 다음 작업에 그대로 전달할 요청

> WORK_HANDOFF.md와 docs/docker-home-verification-20260920.md를 읽고 이어서 진행해줘. Compose build/up/health/smoke, 실제 Flyway, HMR, DB 보존은 통과했다. 보고서 8절의 이슈 초안을 먼저 확정하고 SessionMigrationIT의 고정 관리 포트를 실제 배정 포트로 바꾼 뒤 Docker PostgreSQL에서 postgres-it를 재검증해줘. 결과를 문서에 반영하고 2단계에서 멈춰줘. 3단계나 업무 기능은 시작하지 말아줘.

상세 근거가 필요할 때만 `docs/docker-development-stage2.md`, `docs/development-readiness-20260920.md`, `docs/implementation-gap-log.md`, `docs/runtime.md`, `docs/implementation-decisions.md`를 추가로 참고한다.
