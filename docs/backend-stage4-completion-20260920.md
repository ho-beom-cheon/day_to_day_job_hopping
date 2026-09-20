# 작업 4 완료 — Spring Boot 공통 기반

2026-09-20 / [이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3)

이후 업무 API가 공통 응답·오류, 엄격한 입력 검증, 변경 충돌 처리와 DB 멱등성 저장을 재사용할 수 있게 했다. 개발 Backend에도 반영했고 실제 Nginx 경유 보안 오류 응답을 확인했다. 이번에는 4번만 완료했으며 업무 API와 로그인 기능은 후속 단계다.

## 달라진 동작

- 비인증 API 요청은 빈 본문 대신 `AUTH_REQUIRED` ErrorEnvelope를 반환한다. CSRF 오류는 `CSRF_INVALID`, 접근할 수 없는 리소스는 존재를 숨기는 `RESOURCE_NOT_FOUND`를 사용한다. 기본 인증 경계는 계속 닫혀 있다.
- 응답의 헤더·본문·MDC가 같은 서버 생성 32자리 trace를 사용한다. 서버 시각은 +09:00, API 캐시는 no-store다. 예외 메시지·SQL·거부된 입력을 오류 응답에 노출하지 않는다.
- ID는 양수 bigint 문자열만 받고 일반 숫자/revision은 숫자로 유지한다. 미정의 필드·중복 키·여분 JSON·숫자/문자열 자동 변환을 거부하며 필수값·범위 오류는 validation 응답으로 처리한다.
- 조건부 수정용 단일 strong ETag 파서와 revision 비교를 제공한다. 누락은 428, stale 또는 ORM optimistic lock 실패는 412다. 실제 갱신은 도메인 서비스가 DB 잠금/조건부 UPDATE와 결합한다.
- `IdempotencyService`는 사용자+API ID+UUID 키로 24시간 유효한 결과를 저장한다. 같은 요청은 기존 결과를 반환하고, 다른 대상/본문/전제조건은 409, 동시 처리 중이면 409와 Retry-After:1이다. 업무·outbox·결과 기록은 같은 트랜잭션으로 commit/rollback한다.
- 재현하는 응답에는 원래의 status/data/ETag/Location을 사용하고 trace/time은 현재 요청에서 새로 만든다. 외부 API 호출은 이 트랜잭션에 넣지 않는다.

자세한 입력 규칙, 코드 사용법과 책임은 [공통 계약·연결 가이드](contracts/backend-stage4-alignment.md)에 정리했다. DB 원본의 대상별 operation_scope 설명보다 OpenAPI의 사용자+API ID 범위를 우선 채택하고, 대상은 fingerprint에 포함했다. V1–V3는 수정하지 않았다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| Docker Backend build / Maven verify | PASS, 일반·계약 테스트 36개, 실행 JAR 생성 |
| PostgreSQL IT | PASS, 15개 (멱등성/동시성 7 + 기존 Session 2 + DB 스키마 6) |
| 전체 | **51개 통과, 실패 0, 오류 0, skip 0** |
| 공통 응답/JSON 계약 | null 필드 보존, 필드 집합, trace/시간, ID 정밀도·입력 범위, JSON 엄격성, validation, 428/412, UUID, Retry-After, 500 민감정보 비노출 |
| PostgreSQL 멱등성 | 정규화 요청 재현, 대상/본문/전제조건 차이 409, 사용자/API scope 분리, 동시 요청 busy 및 결과 1회 확정 |
| DB 트랜잭션 | 업무/outbox/키의 실패 및 바깥 transaction rollback, 재시도 성공, 동시에 같은 revision을 수정하면 한 건만 성공 |
| TTL/정리 | 24시간 유효기간, 만료 키 재획득, 활성 비완료 레코드 재실행 차단, 만료 행의 제한된 batch 정리 |
| 실행 JAR 검사 | 공통 main 코드 포함, 테스트 Controller/IT/원본 fixture 미포함 |
| 개발 Compose | config, Backend 재빌드/기동, 4서비스 running/healthy PASS |
| 실제 HTTP | Nginx→GET `/api/v1/users/me` 401 AUTH_REQUIRED, CSRF 없는 POST 403 CSRF_INVALID. no-store, 헤더/본문 trace 일치, +09:00 확인 |
| smoke | Frontend→Backend health(DB UP), Nginx 연결, 외부 management 차단 PASS |
| 기존 DB | 적용 전후 catalog 동일. cluster ID 및 V1/V2/V3 checksum, 47테이블·567컬럼 유지. 추가 migration 없음 |

전체 검증 중 ERROR 로그 1건은 `ApiContractTest`가 의도적으로 발생시킨 500 예외다. 테스트 실패나 개발 Backend 오류가 아니다. 실제 provider 호출, Google 로그인과 원격 CI는 이번 범위에서 실행하지 않았다.

## 주요 소스

- `backend/src/main/java/dev/dailycareer/common/api/`: 응답 팩토리와 공통 예외/보안 오류 작성.
- `common/json/`, `common/trace/`, `common/concurrency/`: ID/JSON, trace, ETag/revision.
- `common/idempotency/`: fingerprint, 저장 가능한 응답, DB 트랜잭션 및 만료 정리 진입점.
- `infrastructure/security/SecurityConfiguration.java`: 기존 접근 차단을 유지하면서 공통 오류 연결.
- `backend/src/test/java/dev/dailycareer/common/api/ApiContractTest.java`, `infrastructure/IdempotencyIT.java`와 기존 ID/trace/보안 테스트.
- `backend/src/test/resources/api/common-contract.json`: 원본 4개 공통 schema 발췌와 원본 SHA-256. 88개 성공 wrapper의 공통 구조도 대조했다.

원본 OpenAPI와 DB migration, Frontend·Compose 설정은 변경하지 않았다. dependency는 Boot BOM으로 관리되는 `spring-boot-starter-validation`만 추가했다.

## 재현

프로젝트 루트에서 실행한다. 호스트 Java/Maven 설치 없이 검증할 수 있다.

```powershell
docker build --target build -t daily-career-backend-check ./backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180 backend
docker compose exec -T frontend node scripts/smoke.mjs
```

원본 공통 schema 발췌는 YAML 변경이 있을 때 함께 갱신한다. test Controller는 테스트 소스에만 있으므로 구현하지 않은 업무 route가 새로 공개되지 않는다.

## 후속 책임

- 다음은 **5번 인증·회원**이다. Google OIDC, 허용 계정, 세션/내 정보, CSRF 발급·로그아웃과 Nginx OAuth 경로를 연결한다.
- 도메인 Controller는 공통 팩토리·헤더 파서·멱등성 서비스를 실제 업무에 연결하고, 재현 요청을 포함해 소유권/권한을 검증해야 한다. 공개 domain 오류 코드는 원본과 대조하며 추가한다.
- 멱등성 24시간은 재현 유효기간이다. 물리 삭제는 `purgeExpired` 진입점까지 구현했고 **주기적 정리 worker는 후속 단계**다. 기존 활성 비완료 행의 운영 복구 정책도 worker 단계에 남긴다.
- Frontend의 ErrorEnvelope decoder와 400 VALIDATION_FAILED 표시 등은 작업 15에서 연결한다. 기존 status만 보는 client는 아직 공통 오류 필드를 사용하지 않는다.

커밋/푸시/PR은 수행하지 않았다. 기존 미커밋 작업을 유지했고 이슈 #3은 후속 Git 반영을 위해 열어 두었다.
