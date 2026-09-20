# 2단계 Docker 개발환경 결과 — 2026-09-20

> 이 문서는 이전 PC의 구성 작업 기록이다. 집 PC에서 Compose 실행·영속성·HMR은 통과했고 postgres-it 오류가 남았다. [최신 실행 결과 및 이슈 초안](docker-home-verification-20260920.md)을 우선 참고한다.

## 1. 작업 결과

기존 Compose, 이미지 digest, Maven/npm, DB 설정, healthcheck를 재사용했다.
Frontend 개발 stage·소스 mount·polling과 기존 Nginx의 HMR 연결을 보완했다.
Docker Engine/WSL이 없는 현재 PC에서는 실제 컨테이너 실행을 검증하지 못했다.
따라서 **구성 보완 완료, 2단계 종료 조건 미충족**이다. 업무 기능/DB 스키마는 추가하지 않았다.

## 2. 최종 서비스 구조

`브라우저 → nginx:80 → frontend:3000 또는 backend:8080 → postgres:5432`

- frontend: 기존 Next.js 16.3.5 / TypeScript / npm / Node 22.22.0. 개발 서버는 webpack polling 사용.
- backend: 기존 Java 21 / Spring Boot 3.5.16 / Maven Wrapper 3.9.9. PostgreSQL driver·Actuator·Flyway 기존 의존성 유지. DB healthy 이후 기동하며 내부 9090 health는 실제 DB 연결 상태를 포함한다.
- db 역할의 기존 서비스명 `postgres` 유지. 기존 PostgreSQL 17.11-alpine3.23 및 digest 재사용; 새 버전을 선택하지 않았다. `postgres-data` named volume 유지.
- nginx: 기존 same-origin `/api/` 구조와 8080 진입점을 유지하기 위해 보존했다. 신규 프록시 구성이나 운영 TLS를 만들지 않았다.
- 호스트에는 기본 `127.0.0.1:8080`만 공개한다. 내부 Backend/DB 주소에 localhost를 사용하지 않는다.
- Frontend는 `src`만 mount하므로 Windows node_modules/빌드 산출물과 분리된다. 설정·의존성 및 Backend 수정은 재빌드한다. 셸 wrapper는 기존 LF와 `sh mvnw` 사용으로 실행 권한 차이를 회피한다.

## 3. 생성/수정 파일

| 경로 | 구분 | 목적 |
|---|---|---|
| `compose.yaml` | 수정 | Frontend development target, 소스 전용 read-only mount, health 시작 유예 |
| `frontend/Dockerfile` | 수정 | 공통 dependencies/development/build/runtime stage, 기존 production build 유지 |
| `infra/nginx/default.conf` | 수정 | 개발 HMR WebSocket 전달 |
| `README.md` | 수정 | clone부터 실행·소스 반영·주소·영속성 확인·운영 분리 안내 |
| `docs/runtime.md` | 수정 | 기존 IDE 안내를 보존하면서 개발 실행 방식 현행화 |
| `docs/docker-development-stage2.md` | 생성 | 이번 단계 결과와 미검증 항목 기록 |

작업 시작 전에 이미 수정되어 있던 양쪽 `.dockerignore`, `SessionMigrationIT.java`,
`docs/runtime.md`의 IDE 안내와 미추적 `.run/`, `scripts/`, 1단계 readiness 보고서는 보존했다.
Backend Dockerfile, pom, package/lockfile, application profile, `.gitignore`, `.gitattributes`,
`.env.example`은 검토 후 그대로 재사용했다. 새 secret 또는 실제 `.env`를 생성하지 않았다.

## 4. 환경변수

| 이름 | 용도/주입 위치 |
|---|---|
| `DATABASE_PASSWORD` | 각 PC에서 설정할 로컬 DB secret; `.env.example`은 빈 값, Compose 필수 검증 |
| `DATABASE_NAME`, `DATABASE_USER` | DB명과 계정, 기본 daily_career |
| `DATABASE_URL` | Compose가 Backend에 `jdbc:postgresql://postgres:5432/<DB명>` 주입 |
| `COMPOSE_PROJECT_NAME`, `DB_VOLUME_NAME` | 환경별 프로젝트/DB volume 분리 |
| `APP_PROFILES` | Backend `SPRING_PROFILES_ACTIVE`로 전달, 기본 dev |
| `SESSION_COOKIE_SECURE` | 로컬 HTTP 템플릿 false; 운영 HTTPS에서는 true |
| `APP_DOMAIN` | 기존 Nginx server_name |
| `HTTP_BIND_ADDRESS`, `HTTP_PORT` | 호스트 공개 주소/포트, 기본 127.0.0.1:8080 |
| `BACKEND_HEALTH_URL`, `NGINX_ORIGIN` | Compose 내부 smoke 확인 주소, 브라우저에 전달하지 않음 |
| `WATCHPACK_POLLING`, `NEXT_TELEMETRY_DISABLED`, `NODE_ENV` | 개발 이미지의 polling/telemetry/모드 설정 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 기존 템플릿의 미래 연동 예약값, 현재 실행에 불필요하며 빈 값 유지 |

브라우저 API base는 기존 same-origin `/api/`다. `NEXT_PUBLIC_*` secret은 추가하지 않았다.
내부 포트는 기존 값을 유지하며 충돌 시 호스트 HTTP_PORT만 변경한다.
회사/집 DB는 독립적이며 자동 동기화하지 않는다.

## 5. 실행 방법

Git 및 Linux 컨테이너용 Docker Engine/Compose를 준비하고 Docker를 실행한다.

```powershell
git clone https://github.com/ho-beom-cheon/day_to_day_job_hopping.git
cd day_to_day_job_hopping
Copy-Item .env.example .env
# .env의 DATABASE_PASSWORD를 로컬 값으로 채운다.
docker compose config --quiet
docker compose build
docker compose up -d
docker compose up -d --wait --wait-timeout 180
docker compose ps
docker compose exec -T frontend node scripts/smoke.mjs
```

Linux/macOS는 복사 명령만 `cp .env.example .env`를 사용한다.
브라우저는 http://localhost:8080. 이후 `git pull` → 환경변수 변경 확인 →
`docker compose up -d --build`로 코드/의존성을 반영한다. 변경 없을 때 `docker compose up -d`.
종료 `docker compose down`, 로그 `docker compose logs -f --tail=100 frontend backend postgres`.
**`docker compose down -v`는 DB 데이터를 영구 삭제한다.** 정상 종료에 사용하지 않는다.
DB 보존 조회·재생성 절차는 README에 기록했다.

## 6. 검증 결과

호스트는 Node 22.22.0/npm 10.9.4/JDK 21.0.2이며, 컨테이너 JDK는 기존 Temurin 21.0.12+8이다.
호스트 검증은 컨테이너 실행 검증을 대체하지 않는다.

| 명령/항목 | 실제 결과 |
|---|---|
| `docker version`, `docker compose version` | Docker CLI 없음 |
| `.tools/docker-compose.exe --env-file .env.example config --quiet` | PASS(exit 0), 기존 독립 Compose 5.5.1 사용, 검증용 비밀 아닌 값을 프로세스 환경에만 주입 |
| 위 config, 빈 DATABASE_PASSWORD | 예상대로 실패(exit 1), 필수값 검증 정상 |
| 독립 Compose `build` | FAIL: docker_engine named pipe 없음, buildx 부재 fallback 경고 |
| 독립 Compose `up -d` / `ps` | FAIL: docker_engine named pipe 없음 |
| PostgreSQL healthy / Backend→DB / 컨테이너 Frontend 접근 | 환경 차단, 성공 주장 없음 |
| named volume down/up 데이터 보존 | 구성 확인, 실제 실행 미검증 |
| Frontend `npm run typecheck` | PASS |
| Frontend `npm run lint` | PASS |
| Frontend `npm test` | FAIL: forks worker 응답 timeout, 테스트 실행 전 4 errors |
| Frontend `npm test -- --maxWorkers=1` | PASS: 4파일/30개 테스트. 저장소 테스트 설정은 변경하지 않음 |
| Frontend `npm run build` | PASS: production compile/TypeScript/static pages/standalone 산출물 |
| `next dev --hostname 127.0.0.1 --port 3300 --webpack` (WATCHPACK_POLLING=true) | 호스트 기동 PASS, HTTP 200 및 기존 페이지 제목 확인; 검증 후 종료. Docker/Nginx HMR 실증은 아님 |
| Backend `mvnw.cmd -B -ntp verify` | PASS: compile/test/package, 테스트 11개 성공, JAR 생성 |
| `git check-ignore`, `git ls-files '*env*'` | 실제 환경파일·node_modules·빌드·IDE·로그·DB dump 제외 확인 |
| `git diff --check` | PASS |

## 7. 발견된 문제

- 현재 PC에 Docker CLI/Desktop/Engine 실행환경이 없고 WSL도 설치되지 않았다. 실제 실행 게이트가 차단되어 있다.
- Frontend 기본 병렬 테스트의 worker 응답 timeout이 발생했다. 단일 worker 재실행은 30개 모두 통과했다. 최초 실패의 구체적 원인은 확정하지 않았으며 테스트를 skip하거나 성공으로 덮지 않았다.
- 기존 Frontend 이미지는 production 실행 전용이어서 개발 소스 반영에 매번 재빌드가 필요했다. development stage와 소스 mount로 보완했다. Windows Docker mount/HMR 실증은 남아 있다.
- 기존 문서의 환경파일 변경만으로 운영 실행이 가능하다는 안내는 개발 Compose와 맞지 않아 별도 운영 구성이 필요함을 명시했다.
- 이번 변경에서 설계 계약 충돌은 없음. ID·진척도·AI cancel·revision·시험 상태 계약이나 업무 코드는 수정하지 않았다.

## 8. 미해결 사항

Docker 실행 가능한 PC에서 build/up/health/smoke, 소스 수정 반영 및 down/up DB 보존 검증을 완료해야 한다.
업무 스키마 단계에는 기존 GAP-002의 원본 DDL 확인도 필요하다. 이번에 임의 DDL을 추가하지 않았다.

## 9. 다음 단계 준비 여부

**아직 시작 가능으로 판정하지 않는다.** 2단계 실제 실행 게이트 통과 및 기존 원본 DDL gap 해소가 필요하다.
DB 스키마/마이그레이션 구현 등 다음 단계는 진행하지 않았다.

## 10. Git 상태

기존 미커밋 변경을 보존했고 이번 변경도 커밋/푸시하지 않았다. 실제 `.env`는 없고 추적 대상도 아니다.
환경 관련 추적 파일은 `.env.example`과 `frontend/next-env.d.ts`뿐이다.
기존 `.run/`, `scripts/`, readiness 보고서 외 이번 새 파일은 본 보고서다.
빌드 결과물과 로컬 도구는 Git 제외 상태다.
