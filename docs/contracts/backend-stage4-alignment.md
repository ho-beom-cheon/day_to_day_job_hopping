# 작업 4 공통 Backend 계약과 사용 방법

2026-09-20 / [이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3)

## 원본 대조와 채택

OpenAPI v1.2.1의 `Meta`, `Error`, `ValidationDetail`, `ErrorEnvelope`를 기준으로 삼았다. 88개 정상 응답 schema의 필수 공통 필드는 `success`, `data`, `error`, `meta`로 동일하다. 해당 원본 일부와 전체 원본 SHA-256을 `backend/src/test/resources/api/common-contract.json`에 기록한다. 원본 YAML은 변경하지 않는다.

| 대상 | 기존 문제 / 채택한 동작 |
|---|---|
| 응답 | 성공은 data와 error:null, 실패는 data:null과 Error를 포함한다. nullable 필드도 생략하지 않는다. 명시적 ApiResponses 팩토리를 사용한다. |
| trace/time | 36자리 UUID에서 하이픈 없는 소문자 32자리로 변경한다. 클라이언트 trace를 신뢰하지 않으며 요청 attribute·MDC·X-Trace-Id·meta.traceId가 일치한다. async/error 재디스패치에서는 같은 요청 trace를 유지한다. serverTime은 +09:00이다. |
| 오류 | MVC validation/역직렬화·보안 필터와 서비스 예외에 같은 ErrorEnvelope를 사용한다. 예외 메시지/SQL/거부된 입력값을 응답으로 보내지 않는다. 내부 예외 로그도 타입과 trace만 남긴다. |
| 캐시 | `/api/v1/` 응답은 `Cache-Control: no-store`다. Actuator health는 공통 Envelope로 포장하지 않는다. |
| 입력 | DTO 미정의 필드, 중복 JSON 키, 뒤따르는 JSON 값, 숫자↔문자열/소수→정수 묵시 변환을 거부한다. DTO에는 required/nullable/범위에 맞는 Bean Validation을 함께 지정한다. |
| ID | `@JsonId Long`과 명시적 `Ids.parse(pathOrQuery)`를 사용한다. 1..Long.MAX_VALUE 십진 문자열만 허용한다. API 정규식의 20자리 가능 범위보다 실제 DB/Java 한계가 좁음을 기록하며 overflow는 400으로 처리한다. 일반 숫자는 문자열로 바꾸지 않는다. |
| revision | 0..2147483647 숫자. ETag는 따옴표로 감싼 revision이다. If-Match 누락 428, 형식 오류/weak/wildcard/복수값/초과 400, 오래된 revision 412. ORM optimistic lock 실패도 412로 변환한다. |
| 보안 경계 | 아직 모든 업무 route는 닫혀 있다. API 비인증은 401 AUTH_REQUIRED, CSRF 실패는 403 CSRF_INVALID. 인증됐지만 접근할 수 없는 리소스는 404 RESOURCE_NOT_FOUND로 존재를 숨긴다. 비-API 로그인/로그아웃과 내부 health의 기존 경계를 유지한다. |
| 프레임워크 입력 오류 | 잘못된 method/media type/필수 파라미터 등은 공개 공통 400 INVALID_REQUEST로 정규화한다. 별도 405/415 공개 오류 코드를 발명하지 않는다. |
| 멱등성 scope | API의 사용자+API ID를 적용한다. DB 원본 주석의 '대상을 포함한 operation_scope'는 채택하지 않는다. 대상은 fingerprint에 넣어 다른 대상에 같은 키를 쓰면 409가 된다. V2 주석 이력을 덮어쓰지 않는다. |

## 업무 API 연결 방법

성공 반환은 `ApiResponses.ok(request, dto)` 또는 `ApiResponses.success(request, status, dto, etag, location)`을 사용한다. DTO의 ID에만 `@JsonId`를 붙이고, required 입력은 `@NotNull`/`@NotBlank` 등과 `@Valid`로 검증한다. 일반 revision에 ID annotation을 붙이지 않는다. nullable data를 허용하는 API는 null을 그대로 반환한다.

조건부 수정은 `Revisions.required(request)`로 헤더를 읽는다. 인증 사용자와 소유권을 확인한 뒤 같은 트랜잭션에서 자원 행을 잠그고 `requireMatch(expected,current)`를 실행하거나, `UPDATE ... WHERE id=? AND revision=?`의 갱신 건수로 충돌을 판단한다. 단순한 메모리 비교만 하고 잠금 없이 저장해서는 안 된다. 변경 없는 PATCH의 revision 유지 등은 개별 API 규칙이다.

업무 오류 코드는 해당 기능 구현 시 OpenAPI에 있는 코드를 추가한다. 숙련도 산식, 빈 과정의 분모 규칙, 도메인별 revision 갱신 단위는 공통 계층에서 임의로 결정하지 않는다.

## 멱등성 트랜잭션

1. 인증·소유권·CSRF와 DTO를 검증한다. 재현 요청도 현재 권한 검사를 생략하지 않는다. 사용자 ID와 API ID는 서버가 결정한다.
2. `IdempotencyService.requiredKey(request)`로 정확한 UUID 헤더 하나를 받는다. 누락은 400 IDEMPOTENCY_KEY_REQUIRED, 잘못된 형식은 400 INVALID_REQUEST다. UUID 대소문자는 정규화한다.
3. fingerprint용 JSON 객체에 검증된 path/query/body와 필요한 전제조건을 명시적으로 넣는다. 필드 생략과 null은 구분하고, 객체 키 순서만 정렬한다. 배열 순서와 값 타입은 유지한다. 인증정보·CSRF·cookie는 넣지 않는다. DTO 정규화는 해당 API와 같은 규칙으로 먼저 수행한다.
4. `execute(appUserId, "TEST-005", key, fingerprint, work)`를 호출한다. PostgreSQL transaction advisory lock으로 아직 행이 없는 경합까지 직렬화한다. 잠금 중인 중복 요청은 즉시 409 REQUEST_IN_PROGRESS와 Retry-After:1을 받는다. 64비트 잠금 해시 충돌은 다른 작업을 실행시키지 않고 일시적인 busy만 발생시킬 수 있다.
5. 같은 키·다른 fingerprint는 409 IDEMPOTENCY_KEY_REUSED다. 같은 완료 요청은 기존 HTTP status/data/ETag/Location을 재현하며 업무 콜백은 다시 실행하지 않는다. meta와 trace는 현재 HTTP 요청에서 새로 만든다.
6. 콜백은 **짧은 로컬 DB 업무 변경과 outbox 저장**만 수행하고 `StoredReply`를 반환한다. 응답 status는 명세의 200/201/202, Location은 앱 내부 API 경로로 제한한다. 응답의 data는 민감정보를 최소화한 공개 DTO 스냅샷이다. Set-Cookie·인증 헤더를 저장하지 않는다.
7. 콜백 실패/바깥 트랜잭션 rollback은 업무·outbox·멱등성 기록을 함께 취소한다. 5xx를 성공 결과로 캐시하지 않는다. 외부 호출은 별도 worker가 처리한다. 모든 DB 작업은 같은 Spring transaction manager/DataSource를 사용해야 한다.

레코드의 유효기간은 생성/재획득 transaction 시각부터 24시간이다. 만료 키는 다시 사용할 수 있지만 시험 제출 등 실제 업무 고유 제약은 계속 중복 부작용을 막아야 한다. `purgeExpired(batchSize)`는 만료 행만 잠금 중 행을 건너뛰며 최대 1,000개씩 정리하는 유지보수 진입점이다. **주기적 정리 worker 연결은 후속 worker 단계**이며 이번에 자동 삭제 스케줄을 켜지 않았다. API의 24시간 재현 유효기간과 물리 삭제 시점은 구분한다.

현재 서비스는 PROCESSING과 SUCCEEDED를 한 transaction 안에서 처리해 정상적으로 커밋된 중간 상태를 남기지 않는다. 다른 writer/운영 조작이 만든 활성 비완료 행은 재실행하지 않고 REQUEST_IN_PROGRESS로 제한한다. 그러한 행의 복구 정책은 후속 worker/운영 작업에서 명시해야 한다.

## 현재 범위

공통 계층과 테스트용 Controller/DB fixture를 구현한다. 운영 JAR에는 테스트용 route/fixture가 들어가지 않는다. 실제 CSRF 발급·OIDC·회원·로그아웃은 작업 5, 도메인 Controller와 서비스는 각 기능 단계, Frontend decoder/오류 UI 연결은 작업 15다. V1–V3와 업무 schema는 변경하지 않는다.
