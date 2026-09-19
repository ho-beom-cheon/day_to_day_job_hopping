# 데일리 이직

6개월 학습·시험·복습 서비스. Java 21 / Spring Boot / PostgreSQL / Next.js 기반.

- [구현 계획](docs/implementation-plan.md)
- [현재 진행 및 검증 결과](docs/implementation-progress.md)
- [계약 누락 및 차단 항목](docs/implementation-gap-log.md)
- [실행 방법](docs/runtime.md)
- [원본 설계 패키지](design-package/daily_career_codex_implementation_20260919_full/00_START_HERE/README.md)

현재는 Phase 1 공통 기반 구현 단계이며 실제 학습 API는 아직 제공하지 않습니다.
공개 API와 업무 DB 스키마는 원본 OpenAPI/DDL 확보 후 연결합니다.

## 새 PC 개발환경 구축 (Phase 1 운영 기준 추가)

기존 스택과 구현 계획을 유지하며 **Docker Compose를 기본 실행 방식**으로 사용합니다.
회사/집 PC의 DB는 각각 독립적입니다. PostgreSQL을 로컬 OS에 직접 설치하지 않습니다.

### 최소 설치 프로그램

- Git
- Windows/macOS: Docker Desktop + Docker Compose, Linux containers 모드. Windows는 Docker Desktop이 요구하는 WSL 2/가상화 환경을 준비합니다.
- Linux/OCI VM: Docker Engine + Compose plugin (Compose 2.24 이상; 구성 검증 도구 5.5.1).
- 호스트 Java, Node.js, Maven, PostgreSQL 설치는 Compose 실행에 필요하지 않습니다. 최초 빌드에는 이미지·의존성 다운로드가 가능한 네트워크가 필요합니다.

### 최초 실행

프로젝트 clone 후 루트 폴더에서 실행합니다.

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

Backend 시작 시 Flyway가 schema를 자동 구성합니다. 현재 migration은 공식 Spring Session 테이블이며 업무 schema는 원본 DDL을 기다리고 있습니다. smoke test는 **Frontend 컨테이너 → 내부 Backend health(DB 포함 UP)** 및 Nginx의 Frontend/Backend 연결을 검사합니다. 내부 health는 브라우저용 공개 API가 아닙니다.

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
| 변경 코드 재빌드/실행 | `docker compose up -d --build` |
| 캐시 없이 재빌드 | `docker compose build --no-cache` 후 `docker compose up -d` |
| 현재 환경 DB 초기화/volume 삭제 | 아래 삭제 절차 참고 |

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
- 개발/운영은 `APP_PROFILES`, `APP_DOMAIN`, `HTTP_BIND_ADDRESS`, `HTTP_PORT`, `COMPOSE_PROJECT_NAME`, `DB_VOLUME_NAME`, `SESSION_COOKIE_SECURE`, `DATABASE_*`로 분리합니다. 예: 별도 운영 환경파일로 `docker compose --env-file /secure/path/career.env up -d --build`.
- OCI에서도 컨테이너 간 주소는 `postgres`, `backend`, `frontend` 서비스명을 사용합니다. 특정 PC 경로는 사용하지 않습니다. 개발 기본값은 loopback HTTP이며 운영 공개 배포에는 HTTPS 종단/도메인 설정과 `SESSION_COOKIE_SECURE=true`, 운영용 독립 volume/secret이 필요합니다. 현재 TLS 운영 배포 검증은 미완료입니다.
