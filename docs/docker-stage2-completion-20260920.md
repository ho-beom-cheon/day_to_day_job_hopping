# 작업 2 완료 — Docker 개발환경 통합 테스트 보완

기준일: 2026-09-20 / 관련 이슈: [#1 테스트: PostgreSQL 통합 테스트의 관리 포트 하드코딩 수정](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/1)

## 결과

실제 PostgreSQL에서 실패하던 `SessionMigrationIT`의 관리 포트 호출을 수정했다. 일반 테스트 11개와 통합 테스트 2개가 모두 통과했고 skip은 0개다. 기존 개발환경 smoke와 4서비스 healthy, 개발 DB cluster ID 및 Flyway 이력 보존도 확인했다.

이전 [집 PC 검증](docker-home-verification-20260920.md)에서 통과한 Compose build/up·HMR·down/up 영속성 검증에 이번 IT 통과를 합쳐 **26단계 계획의 작업 2를 완료**한다. 업무 DB/API 기능이나 배포 완료를 의미하지 않는다.

## 문제와 수정

수정 전 현재 파일과 기존 테스트 이미지 안의 파일은 SHA-256이 같았다. 그 이미지로 재현했을 때 실제 관리 포트는 `33687`이었으나 테스트는 고정 `9090`을 호출했고, 일반 테스트 11개는 통과했지만 IT 2개 중 1개가 `ConnectException`으로 실패했다.

수정 파일: [SessionMigrationIT.java](../backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java)

- 테스트 속성에 `management.server.port=0`을 명시해 임의 관리 포트를 사용한다.
- `@LocalManagementPort`로 실제 포트를 주입한다.
- `/actuator/health`와 `/actuator/env` 호출에 주입된 포트를 사용한다.
- health 응답 `{"status":"UP"}`, main port에서의 관리 경로 차단, `/actuator/env` 차단 assertion을 유지한다.

수정 후 관리 포트는 `41255`였으며 두 IT가 모두 통과했다. 애플리케이션과 Compose의 내부 관리 포트 9090은 그대로다. 업무 schema, V1, migration 개수 assertion, Frontend, 의존성과 이미지 digest는 변경하지 않았다.

## 실행 명령과 검증 결과

호스트 Java 설치 없이 기존 Dockerfile의 build stage를 사용했다. 아래 명령은 저장소 루트에서 실행했다.

```powershell
# 수정된 테스트 컴파일 및 일반 테스트
docker build --target build -t daily-career-backend-check ./backend

# Compose 개발 DB와 별개인 일회용 PostgreSQL Testcontainers 검증
docker run --rm --name daily-career-postgres-it-stage2-after -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify

# 기존 개발환경 확인
docker compose config --quiet
docker compose exec -T frontend node scripts/smoke.mjs
docker compose ps --format '{{.Service}} {{.State}} {{.Health}}'
```

Docker socket mount는 테스트 runner에서 Testcontainers를 실행하기 위한 것이다. 개발/운영 서비스의 Compose에는 추가하지 않았다.

| 검증 | 결과 |
|---|---|
| 수정 전 `-Ppostgres-it verify` | 예상 실패 재현: 일반 11개 통과, IT 2개 중 오류 1개, skip 0 |
| 수정 후 Docker build의 `verify` | PASS: 일반 테스트 11개, JAR 생성 |
| 수정 후 `-Ppostgres-it verify` | PASS: 일반 11개 + IT 2개, 실패/오류/skip 모두 0 |
| 실제 PostgreSQL 17.11 신규 DB | PASS: public의 V1 Flyway 적용, 세션 저장/조회/삭제 |
| 관리 포트 검증 | PASS: 실제 임의 포트 health UP, main port/다른 관리 endpoint 차단 |
| Compose config / 현재 상태 | PASS: backend/frontend/nginx/postgres 모두 running/healthy |
| Frontend runtime smoke | PASS: 실제 DB 포함 Backend health, Nginx Frontend/API 전달, management 비공개 |
| 개발 DB 보존 | 전후 cluster ID `7687576957880160290`, V1/checksum `1873624569`/success 동일 |
| 테스트 컨테이너 종료 | stage2 runner와 Testcontainers 실행 컨테이너 잔존 없음 |
| 문서 링크 / `git diff --check` | PASS |

실행 로그는 Git 제외 `.tools/stage2/`의 `postgres-it-before.log`, `backend-build.log`, `postgres-it-after.log`에 남겼다. 개발 DB 전후 비교도 같은 폴더에 보관한다. 로그 전체를 Git에 추가하지 않았다.

이번에는 Frontend와 Compose 구성을 변경하지 않아 Frontend 전체 재빌드, 브라우저 HMR, down/up 영속성 검증을 반복하지 않았다. 해당 검증의 기존 근거는 집 PC 보고서다. 원격 CI는 실행하지 않았다. 현재 서비스 재시작·개발 volume 삭제는 하지 않았다.

## 진행 상태와 다음 작업

- 사용자가 이슈 등록 후 수정·검증을 승인했고 실제 이슈 #1을 만든 다음 수정했다.
- Git에 저장된 인증이 없어 등록은 로그인된 GitHub 브라우저에서 수행했다. 새 토큰을 만들거나 인증정보를 저장하지 않았다.
- 소스 변경은 테스트 한 파일이며 나머지는 진행 기록과 보고서다. 기존 사용자 변경을 보존했다.
- 커밋·푸시·PR·이슈 종료는 수행하지 않았다. 이슈는 코드 반영 전이므로 열어 두었다.
- 다음은 **3번 DB 스키마·마이그레이션**이다. 사용자 요청 시 [DB 이슈 초안](database-stage3-issue-draft.md)과 [작업 1의 차이 목록](project-stage1-assessment-20260920.md)을 기반으로 시작한다. 이번에 업무 DDL을 구현하지 않았다.
