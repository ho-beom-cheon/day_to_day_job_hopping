# 집 PC Docker 2단계 실행 검증 — 2026-09-20

> 후속 결과: 이 문서의 관리 포트 오류는 [이슈 #1](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1)에서 수정했고 일반 11개/IT 2개가 모두 통과했다. 최신 판정은 [작업 2 완료 보고서](docker-stage2-completion-20260920.md)를 따른다. 아래는 수정 전 실행 이력이다.

## 1. 작업 결과

기준 커밋 `c61c999`의 기존 구성을 변경하지 않고 Docker Desktop을 시작해 검증했다.
Compose 4서비스 build/up/health, 실제 PostgreSQL/Flyway, 브라우저 HMR, named volume 보존은 통과했다.
**postgres-it 2개 중 1개 오류로 전체 검증 완료는 보류한다.** 업무 기능과 3단계 구현은 진행하지 않았다.

## 2. 최종 서비스 구조

`브라우저 localhost:8080 → nginx → frontend:3000 / backend:8080 → postgres:5432`

- 기존 Nginx는 same-origin API와 HMR 전달에 필요하므로 유지했다.
- PostgreSQL 17.11, Java 21, Node 22.22.0 및 이미지 digest를 그대로 사용했다.
- 4서비스 모두 healthy. 호스트에는 `127.0.0.1:8080`만 공개한다.
- 기존 다른 프로젝트 컨테이너와 볼륨은 변경하지 않았다.

## 3. 생성/수정 파일

| 파일 | 구분 | 목적 |
|---|---|---|
| `docs/docker-home-verification-20260920.md` | 생성 | 이번 실행 결과와 테스트 수정 이슈 초안 |
| `WORK_HANDOFF.md` | 수정 | 최신 검증 상태 및 남은 작업 |
| `README.md` | 수정 | 집 PC 검증 결과 연결 |
| `docs/docker-development-stage2.md` | 수정 | 이전 결과와 최신 실행 결과 구분 |
| `.env` | 로컬 생성, Git 제외 | 기존 ide.ps1로 개발 DB 비밀번호 생성 |

`frontend/src/app/page.tsx` 문구를 일시 변경하여 HMR을 확인한 뒤 원본 바이트로 복구했다.
애플리케이션, 테스트, Docker 구성 변경은 없다.

## 4. 환경변수

- DB: `DATABASE_NAME`, `DATABASE_USER`, `DATABASE_PASSWORD`; Backend `DATABASE_URL`은 Compose에서 서비스명으로 구성한다.
- 환경 분리: `COMPOSE_PROJECT_NAME`, `DB_VOLUME_NAME`, `APP_PROFILES`.
- 접속: `HTTP_BIND_ADDRESS`, `HTTP_PORT`, `APP_DOMAIN`, `SESSION_COOKIE_SECURE`.
- 내부 검증: `BACKEND_HEALTH_URL`, `NGINX_ORIGIN`.
- `GOOGLE_CLIENT_ID/SECRET`은 이번 기동에 불필요하다. 실제 secret 값은 기록하지 않는다.

## 5. 실행 방법

Git clone 후 저장소 루트에서 Docker Desktop을 Linux containers 모드로 실행한다.

```powershell
docker version
docker compose version
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Up
docker compose exec -T frontend node scripts/smoke.mjs
```

기존 스크립트는 `.env`가 없을 때만 생성한다. 접속 주소는 http://localhost:8080 이다.
이후 시작은 `docker compose up -d`, 종료는 `docker compose down`이다.
**`down -v`는 데이터를 삭제하므로 이번 검증에서는 사용하지 않았다.**

## 6. 검증 결과

Docker Engine/CLI 29.6.1, Compose 5.1.4, WSL 2 기반 Docker Desktop에서 실행했다.
호스트 Node는 24.18.0이며 Java 21은 PATH에서 발견되지 않아 빌드는 고정된 Docker 환경에서 수행했다.

| 실제 명령/검증 | 결과 |
|---|---|
| `docker compose config --quiet` | PASS |
| `scripts/ide.ps1 -Action Up` | PASS: config와 `docker compose up --build -d --wait --wait-timeout 180` 수행 |
| Compose Backend/Frontend 이미지 빌드 | PASS: 위 명령의 build 단계; 별도 `docker compose build`는 중복 실행하지 않음 |
| Backend Dockerfile의 `sh mvnw -B -ntp verify` | PASS: compile, 테스트 11개, JAR 생성 |
| `docker build --target build -t daily-career-frontend-check ./frontend` | PASS: typecheck, lint, 기본 병렬 테스트 30개, production build |
| `docker compose ps` | 4서비스 모두 healthy |
| `docker compose exec -T frontend node scripts/smoke.mjs` | PASS: DB 포함 health UP, Nginx Frontend/API 전달, management 비공개 |
| 새 named volume의 Flyway 조회 | PASS: V1 spring session, checksum 1873624569, success=true |
| 실제 브라우저 localhost:8080 | 시작 화면 확인 |
| Frontend 문구 임시 수정 | 새로고침 없이 브라우저 자동 반영 확인, 이후 원본 복구 |
| `docker compose down` 후 `up -d --wait --wait-timeout 180` | PASS: 4서비스 healthy, smoke 재통과 |
| 재기동 전후 DB 비교 | cluster ID `7687576957880160290`, V1/checksum/success 동일 |
| `sh mvnw -B -ntp -Ppostgres-it verify` | FAIL: 일반 테스트 11개 통과, IT 2개 중 1개 오류; 세션 migration/저장 테스트 통과 |
| `git diff --check`, `git check-ignore` | PASS: .env, IDE, node_modules, target, 로그, dump 제외 |

Java 설치 없이 기존 빌드 stage에서 IT를 실행한 정확한 명령:

```powershell
docker build --target build -t daily-career-backend-check ./backend
docker run --rm --name daily-career-postgres-it -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify
```

IT는 Compose DB와 별도의 실제 PostgreSQL 컨테이너를 생성했다. 테스트용 컨테이너는 종료됐다.
검증용 이미지 `daily-career-backend-check`, `daily-career-frontend-check`는 로컬에 남아 있다.

## 7. 발견된 문제

`SessionMigrationIT`는 RANDOM_PORT 환경에서 관리 서버가 실제 `34343` 포트로 시작됐으나
37행에서 `http://127.0.0.1:9090/actuator/health`를 호출하여 ConnectException이 발생했다.
같은 파일의 `/actuator/env` 호출도 고정 포트다. Compose의 실제 9090 health는 정상이다.
기존 `ManagementHealthTest`는 이미 `@LocalManagementPort`를 사용한다.

이전 PC의 Frontend worker timeout은 이번 Docker 기본 병렬 실행에서 재현되지 않았다.
빌드에는 ESLint 지원 종료 및 Vite config loader 관련 경고가 있었으나 실패하지 않았다.
이번 범위에서 의존성을 임의 변경하지 않았다.

## 8. 미해결 사항 및 이슈 초안

공개 GitHub API의 열린 이슈 응답은 `[]`였다. 프로젝트 규칙의
“관련 이슈가 없으면 구현하지 말고 이슈 초안을 먼저 제안한다”에 따라 테스트 수정은 보류한다.
이슈 생성·코드 수정·커밋·PR은 수행하지 않았다.

**제목:** 테스트: PostgreSQL 통합 테스트의 관리 포트 하드코딩 수정

- 문제: RANDOM_PORT 실행에서 SessionMigrationIT가 고정 9090으로 health/env를 호출해 IT 실패.
- 수정 범위: 해당 테스트에 `@LocalManagementPort`를 주입하고 두 관리 URL에 실제 포트를 사용.
- 유지 사항: Compose 관리 포트와 애플리케이션 설정, 보안 경계, 업무 schema는 그대로 유지.
- 완료 조건: 실제 Docker/PostgreSQL에서 `-Ppostgres-it verify` 일반 테스트 11개와 IT 2개 모두 통과. health UP 및 management 비공개 assertion 유지.

## 9. 다음 단계 준비 여부

Docker 실행 기반은 검증됐지만 IT 오류가 남아 전체 완료 판정은 보류한다.
업무 DB v0.2 원본 상세 DDL과 OpenAPI v1.2.1 YAML 부재도 기존 gap으로 남아 있다.
DB 구현 및 3단계는 시작하지 않았다.

## 10. Git 상태

시작 시 작업 트리는 깨끗했다. 이번 변경은 보고서 및 안내 문서와 Git 제외 `.env`다.
추적 환경 관련 파일은 `.env.example`, `frontend/next-env.d.ts`뿐이다.
임시 화면 변경은 복구했고 secret·빌드 산출물은 추적하지 않는다. 커밋/푸시는 수행하지 않았다.
