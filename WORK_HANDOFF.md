# 집에서 이어서 작업하기 — 2026-09-20

이 문서 하나로 현재 상태를 파악하고 개발환경 실행 및 다음 작업을 이어갈 수 있도록 정리했다. 저장소는 `https://github.com/ho-beom-cheon/day_to_day_job_hopping`, 작업 브랜치는 `main`이다. 기존 기반 커밋은 `399fd2f`이며, 이 문서와 함께 오늘의 미커밋 변경을 저장한다.

## 1. 현재 어디까지 했나

**공통 기반 구현과 Docker 개발 구성 보완까지 진행했다. 실제 Docker 통합 실행은 아직 검증하지 못했으므로 사용자 개발 순서의 2단계는 완료되지 않았다. 다음 작업은 집 PC에서 Docker 실행 검증이다.**

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

아래 항목은 **아직 미검증**이다. 구성 파일만 확인한 결과와 실제 실행 성공을 구분해야 한다.

- [ ] `docker compose up -d --build --wait --wait-timeout 180` 성공 및 4서비스 정상 상태.
- [ ] 브라우저 시작 화면 표시, Frontend 컨테이너 smoke 성공.
- [ ] Backend → 실제 PostgreSQL 연결 및 clean Flyway migration 성공.
- [ ] `frontend/src`의 화면 문구를 임시 수정했을 때 브라우저에 반영되는지 확인 후 수정 복구.
- [ ] 아래 down/up 전후 DB cluster ID와 Flyway 이력 동일, smoke 재통과.
- [ ] Docker 실행 가능한 환경에서 Backend `postgres-it` 통합 테스트 성공.

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
| GAP-005: Docker 실제 실행 게이트 | 집 PC에서 위 체크리스트를 수행하고 결과 기록 |

개발 중 유지할 핵심 원칙: 업무 ID는 JSON/Frontend string ↔ Backend Long 경계를 유지하고 숫자로 변환하지 않는다. 진척도 네 지표를 혼용하지 않는다. AI 사용자 cancel API/버튼과 공개 CANCELED를 임의 추가하지 않는다. revision/ETag 경쟁 제어와 시험 답안 보존·재채점 계약을 유지한다. 원본 설계 패키지를 임의 수정하지 않는다.

설계 패키지는 `design-package/daily_career_codex_implementation_20260919_full/`에 있다. 일부 문서는 이미지 미포함이라고 적지만 실제 PNG 15개가 존재한다. 과거 완료 선언/정적 검사 PASS는 원본 계약 파일 존재나 현재 런타임 통과를 뜻하지 않는다.

## 8. 사용자가 공유한 전체 개발 순서

1. 프로젝트 현황 분석 + 개발환경 검증
2. Docker 개발환경 구축 — 현재 실제 실행 검증이 남은 지점
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

> WORK_HANDOFF.md를 읽고 현재 상태에서 이어서 진행해줘. 먼저 집 PC의 Docker 실행환경을 확인하고, 기존 4서비스 Compose의 build/up/health/smoke, 실제 PostgreSQL/Flyway, Frontend 소스 반영, down/up DB 보존 및 postgres-it 테스트를 검증해줘. 완료·실패·미검증을 구분하여 이 문서에 결과를 갱신해줘. 기존 공통 기반과 26단계 순서를 유지하고 원본 OpenAPI/업무 DDL이 없으면 추정 구현하지 말아줘. 2단계 검증 후 3단계에 필요한 원본 자료 존재 여부를 확인해줘.

상세 근거가 필요할 때만 `docs/docker-development-stage2.md`, `docs/development-readiness-20260920.md`, `docs/implementation-gap-log.md`, `docs/runtime.md`, `docs/implementation-decisions.md`를 추가로 참고한다.
