# Backend DTO / Controller 최종 기준 복원본

> 상세설계는 전용 방에서 완료되었으나 현재 런타임에서 원본 파일 바이트를 회수할 수 없어 확정 계약 중심으로 복원했다.

## Controller 원칙

- OpenAPI v1.2.1 경로/Method/Status를 그대로 구현
- 신규 공개 엔드포인트를 임의 추가하지 않음
- 인증 사용자 식별은 세션/SecurityContext 기반
- 쓰기 요청 CSRF 검증
- 조건부 수정은 If-Match
- 필요한 mutation은 Idempotency-Key

## DTO 타입

- 외부 ID: Java 필드는 Long을 사용하되 JSON은 문자열 계약을 지키도록 직렬화
- LocalDate: `YYYY-MM-DD`
- Instant/OffsetDateTime 계열: offset 포함 ISO 8601
- nullable/optional은 OpenAPI required/nullable 계약을 기준으로 함
- Enum은 OpenAPI 값과 1:1

## 예외

공통 ErrorEnvelope와 HTTP 상태를 유지한다.

주요 상태:
- 400 요청/검증
- 401 인증
- 403 CSRF/권한
- 404 리소스
- 409 멱등/업무 충돌
- 412 stale If-Match
- 422 업무 규칙 위반
- 428 If-Match 누락
- 429 제한
- 5xx 서버/외부 장애

## Revision

DB revision → Response revision/ETag → Request If-Match 흐름을 끊지 않는다.

## 시험

Controller가 제출 성공과 채점 성공을 하나의 동기 성공으로 합치지 않는다. 제출 후 채점은 별도 job/run으로 수행 가능해야 한다.

## AI

- 사용자 cancel 엔드포인트 금지
- Job 생성/조회 기반 비동기 처리

## 외부 연동

Notion/Slack/Web Push 호출을 핵심 업무 트랜잭션 내부에서 동기 수행하지 않는다. outbox/event 기록까지 핵심 트랜잭션으로 묶고 실제 전송은 분리한다.
