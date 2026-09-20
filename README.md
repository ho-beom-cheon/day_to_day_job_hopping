# 데일리 이직

6개월 학습·시험·복습 서비스. Java 21 / Spring Boot / PostgreSQL / Next.js 기반.

**집에서 이어서 작업할 때: [통합 작업 인수인계 문서](WORK_HANDOFF.md)** — 현재 상태, 실행 명령, 남은 검증과 다음 작업 순서.

- [구현 계획](docs/implementation-plan.md)
- [작업 1 완료: 설계 원본·소스 현황과 채택 기준](docs/project-stage1-assessment-20260920.md)
- [작업 2 완료: Docker 개발환경 통합 테스트 보완](docs/docker-stage2-completion-20260920.md)
- [작업 3 완료: PostgreSQL 업무 스키마·마이그레이션](docs/database-stage3-completion-20260920.md)
- [작업 4 완료: Spring Boot 공통 응답·검증·동시성·멱등성](docs/backend-stage4-completion-20260920.md)
- [작업 5: 인증·회원 구현과 현재 검증 결과](docs/auth-stage5-verification-20260920.md)
- [작업 6 완료: 커리큘럼·학습 운영](docs/curriculum-stage6-verification-20260921.md)
- [작업 7 완료: 휴식일·일정 변경](docs/schedule-stage7-verification-20260921.md)
- [작업 8 완료: 문제·문제풀이·오답 복습](docs/problem-stage8-verification-20260921.md)
- [Google 로그인 로컬 설정](docs/google-login-local-setup.md)
- [현재 진행 및 검증 결과](docs/implementation-progress.md)
- [계약 누락 및 차단 항목](docs/implementation-gap-log.md)
- [실행 방법](docs/runtime.md)
- [원본 설계 패키지](design-package/daily_career_codex_implementation_20260919_full/00_START_HERE/README.md)

현재는 작업 1–8 완료 상태입니다. 문제 조회·제출·동기 규칙 채점·신고와 오답 목록·메모·해결·재개 API까지 구현했습니다. [작업 8 검증 결과](docs/problem-stage8-verification-20260921.md)에 일반 38개와 실제 PostgreSQL IT 52개, 총 90개 테스트 결과를 기록했습니다. 사용자 결정에 따라 운영 콘텐츠는 비어 있습니다. 작업 1–8의 번호와 범위는 유지하고 9단계 이후를 Backend/UI 사용자 흐름 중심으로 재구성했으며, 다음은 작업 9 Next.js 공통 기반입니다.
공통 기반, Google OIDC, 커리큘럼·학습·일정·문제 Backend 운영 기능을 구현했습니다. 설계 차이는 [계약 자료 안내](docs/contracts/README.md)에 따라 계속 보완합니다.

이번 요청의 **2단계(Docker 개발환경)** 결과는 [검증 보고서](docs/docker-development-stage2.md)에 기록합니다.
집 PC에서 컨테이너 실행·DB 영속성·HMR 검증을 통과했고, 작업 2에서 관리 포트 오류를 수정해 일반 테스트 11개와 PostgreSQL IT 2개도 모두 통과했습니다. [최신 완료 보고서](docs/docker-stage2-completion-20260920.md), [이전 실행 기록](docs/docker-home-verification-20260920.md).

## 새 PC 개발환경 구축 (Phase 1 운영 기준 추가)

기존 스택과 구현 계획을 유지하며 **Docker Compose를 기본 실행 방식**으로 사용합니다.
회사/집 PC의 DB는 각각 독립적입니다. PostgreSQL을 로컬 OS에 직접 설치하지 않습니다.

### 최소 설치 프로그램

- Git
- Windows/macOS: Docker Desktop + Docker Compose, Linux containers 모드. Windows는 Docker Desktop이 요구하는 WSL 2/가상화 환경을 준비합니다.
- Linux/OCI VM: Docker Engine + Compose plugin (Compose 2.24 이상; 구성 검증 도구 5.5.1).
- 호스트 Java, Node.js, Maven, PostgreSQL 설치는 Compose 실행에 필요하지 않습니다. 최초 빌드에는 이미지·의존성 다운로드가 가능한 네트워크가 필요합니다.

### 최초 실행

Docker Desktop(Linux containers) 또는 Docker Engine을 실행한 뒤 저장소를 받습니다.

```sh
git clone https://github.com/ho-beom-cheon/day_to_day_job_hopping.git
cd day_to_day_job_hopping
```

이후 루트 폴더에서 실행합니다.

```powershell
# Windows PowerShell
Copy-Item .env.example .env
```

```sh
# macOS / Linux
cp .env.example .env
```

`.env`의 `DATABASE_PASSWORD`를 각 PC에서 정한 값으로 채운 뒤:

```sh
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

기본 접속 주소: [http://localhost:8080](http://localhost:8080).
`up -d`는 백그라운드 시작이므로 완료 검증에는 다음 명령을 사용합니다.

```sh
docker compose up -d --wait --wait-timeout 180
docker compose exec -T frontend node scripts/smoke.mjs
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

Backend 시작 시 Flyway가 schema를 자동 구성합니다. V1은 public의 Spring Session, V2는 daily_career 업무 기준선, V3는 일정 preview·최종시험 보완입니다. Flyway 이력은 public으로 고정합니다. 기존 DB는 후속 migration으로 업그레이드하며 적용 파일을 수정하거나 volume을 초기화하지 않습니다. smoke test는 **Frontend 컨테이너 → 내부 Backend health(DB 포함 UP)** 및 Nginx의 Frontend/Backend 연결을 검사합니다. 내부 health는 브라우저용 공개 API가 아닙니다.

### 일상 운영 명령

| 작업 | 루트 폴더에서 실행할 명령 |
|---|---|
| 다시 실행 | `docker compose up -d` |
| 컨테이너 재시작 | `docker compose restart` |
| 일시 중지 | `docker compose stop` |
| 종료 및 컨테이너 제거, DB 유지 | `docker compose down` |
| 상태 확인 | `docker compose ps` |
| 전체 로그 | `docker compose logs -f --tail=100` |
| Backend/DB 로그 | `docker compose logs -f --tail=100 backend postgres` |
| Frontend 로그 | `docker compose logs -f --tail=100 frontend` |
| 변경 코드 재빌드/실행 | `docker compose up -d --build` |
| 캐시 없이 재빌드 | `docker compose build --no-cache` 후 `docker compose up -d` |
| 현재 환경 DB 초기화/volume 삭제 | 아래 삭제 절차 참고 |

### 소스 변경과 서비스 주소

- 기본 Compose는 Frontend `development` stage를 사용합니다. `frontend/src`만 읽기 전용 bind mount하고 Next.js webpack polling으로 변경을 감지합니다. 호스트 `node_modules`와 `.next`는 공유하지 않습니다. 브라우저 HMR은 기존 Nginx를 통과합니다.
- `package.json`, lockfile, Next 설정, scripts 변경 시 `docker compose up -d --build frontend`로 의존성을 다시 반영합니다. 의존성은 이미지 안에서 `npm ci`로 설치하므로 별도 node_modules volume의 오래된 캐시 문제가 없습니다.
- Backend 소스 변경은 `docker compose up -d --build backend`로 반영합니다. 기존 Maven verify/JAR 실행 구조를 유지하며 자동 Java 재시작이나 디버거를 추가하지 않았습니다.
- 서비스 재생성 후 프록시 연결 오류가 지속되면 `docker compose restart nginx`로 내부 주소를 다시 해석합니다.
- Frontend의 기존 production build/runtime stage는 유지했습니다. 기본 Compose는 개발 전용이며 운영 배포 구성이 아닙니다.

| 대상 | 주소 | 호스트 접근 |
|---|---|---|
| Frontend | `http://localhost:8080/` → `frontend:3000` | 기존 Nginx 경유 |
| Backend | `http://localhost:8080/api/` → `backend:8080` | 기존 보안 정책 적용, 업무 API 미구현 |
| Backend health | `http://backend:9090/actuator/health` | 컨테이너 내부 전용 |
| PostgreSQL | `postgres:5432`, 기본 DB `daily_career` | 직접 공개하지 않음; `docker compose exec postgres psql -U daily_career -d daily_career` |

호스트 포트 충돌 시 `.env`의 `HTTP_PORT`를 변경합니다. Backend의 내부 8080과 호스트의 8080은 별도 네트워크이므로 충돌하지 않습니다.

### 데이터 보존 검증 (Docker 실행 가능한 PC)

아래 조회 결과의 cluster ID와 migration 이력이 `down`/`up` 전후 동일한지 비교합니다.
업무 테이블이나 테스트 데이터를 추가하지 않고 기존 DB cluster 보존을 확인합니다.
2026-09-20 집 PC에서 아래 절차와 smoke 재검증을 통과했습니다.

```sh
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT system_identifier FROM pg_control_system(); SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"'
docker compose down
docker compose up -d --wait --wait-timeout 180
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT system_identifier FROM pg_control_system(); SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"'
docker compose exec -T frontend node scripts/smoke.mjs
```

### DB 초기화와 Volume 삭제

**아래 명령은 현재 Compose 프로젝트의 DB 데이터를 영구 삭제합니다.** 일반 종료에는 사용하지 않습니다.
먼저 `.env`의 `COMPOSE_PROJECT_NAME`, `DB_VOLUME_NAME`, 현재 Docker context를 확인하여 버릴 개발환경인지 확인합니다.

```sh
docker context show
docker compose ps
# 해당 개발 DB를 버리기로 한 경우에만:
docker compose down --volumes
# 새 빈 volume 생성 → Flyway 재적용:
docker compose up -d --build
```

DB를 지우기만 할 때는 `down --volumes`까지만 실행합니다. 전체 Docker host의 `volume prune`은 사용하지 않습니다.
비밀번호/DB 이름/사용자 값은 **기존 volume의 DB 계정을 자동 변경하지 않습니다**. 로컬 초기 설정 변경은 재생성 가능한 개발 DB일 때만 위 초기화 절차를 사용하고, 보존할 데이터가 있으면 별도 계정 변경/backup 계획이 필요합니다.

### 재현성·데이터·운영 분리

- 이미지 manifest digest는 `infra/images.lock.json` 및 Dockerfile/Compose에 고정했습니다. 두 PC에서 같은 Git revision을 사용하세요. Apple Silicon/OCI ARM64도 manifest에 포함된 Linux ARM64 이미지를 사용합니다.
- PostgreSQL은 named volume에 저장되어 컨테이너 restart/rebuild/down 이후에도 유지됩니다. 같은 volume 이름이어도 회사/집 Docker host의 데이터는 서로 독립적입니다.
- Volume/DB dump를 Git에 넣거나 PC 간 volume 복사를 기본 개발 방식으로 사용하지 않습니다. 공통 데이터는 확정된 개발 전용 seed로 재현합니다. 현재 임의 업무 seed는 없습니다. 상세: [실행 문서](docs/runtime.md).
- 실제 `.env`, API Key, OAuth Secret은 커밋하지 않습니다. `.env.example`만 공유합니다.
- 환경별 project·volume·domain·cookie·DB 설정은 분리 가능합니다. 단, 현재 Compose는 개발 서버와 소스 mount를 사용하므로 운영 배포에는 별도 Compose 구성과 Frontend runtime target 선택이 필요합니다. 운영 구성 완성은 이번 범위가 아닙니다.
- OCI에서도 컨테이너 간 주소는 `postgres`, `backend`, `frontend` 서비스명을 사용합니다. 특정 PC 경로는 사용하지 않습니다. 개발 기본값은 loopback HTTP이며 운영 공개 배포에는 HTTPS 종단/도메인 설정과 `SESSION_COOKIE_SECURE=true`, 운영용 독립 volume/secret이 필요합니다. 현재 TLS 운영 배포 검증은 미완료입니다.
