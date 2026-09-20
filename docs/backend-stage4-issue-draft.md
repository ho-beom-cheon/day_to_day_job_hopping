# 기능: OpenAPI 기반 Spring Boot 공통 응답·검증·동시성·멱등성 구현

2026-09-20 / 실행 계획 4번 / 등록 완료: [이슈 #3](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/3)

구현과 검증 결과는 [완료 보고서](backend-stage4-completion-20260920.md), 채택 규칙과 이후 연결 방법은 [공통 계약](contracts/backend-stage4-alignment.md)에 기록했다.

## 문제와 범위

현재 보안 오류는 빈 본문이고 trace ID는 36자리 UUID다. ID 파서는 0·음수·앞자리 0을 허용한다. OpenAPI v1.2.1은 공통 Envelope/Error/Meta, 32자리 trace, +09:00 응답 시각, no-store, 양수 bigint 문자열을 요구한다. revision/If-Match 및 사용자+API ID 범위 24시간 멱등성도 공통 구현이 필요하다.

## 구현 범위

- 명세의 성공/오류 응답과 메타데이터, 보안 계층 401/403 및 MVC 검증/예외 응답을 연결한다.
- validation 의존성과 엄격한 JSON 입력, ID 범위, 428/412 및 단일 strong ETag 처리를 구현한다.
- 기존 idempotency_record를 사용해 동일 키/요청 재현, 다른 요청 409, 처리 중 409/Retry-After=1, 업무 저장과 결과 기록의 동일 트랜잭션을 제공한다.
- 기존 DB 마이그레이션, 닫힌 인증 경계와 내부 health를 보존한다. OIDC·내 정보·로그아웃/CSRF 발급·업무 API·Frontend는 각각 후속 작업이다.

## 완료 기준

- OpenAPI 공통 계약 대조와 MVC/JSON/보안 계약 테스트.
- 실제 PostgreSQL에서 멱등성 재시도·경합·만료·범위 분리·실패 rollback 및 outbox 원자성 검증.
- Backend 전체 build/test/IT, Compose 재기동 및 smoke, 기존 migration/DB 보존 확인.
- 진행 기록·적용 방법·남은 인증/업무 API 연결 책임 문서화.

## 채택할 세부 규칙

- request trace는 서버에서 생성하고 응답 헤더·본문·MDC에 동일하게 사용한다. 재시도 응답의 meta는 현재 요청에서 다시 만든다.
- 멱등성 scope는 OpenAPI의 사용자+API ID다. DB 원본 주석의 대상별 scope와 달리 대상/경로 변수·query·정규화 body·전제조건은 fingerprint에 포함해 다른 대상으로 같은 키를 재사용하면 409가 되게 한다.
- 같은 DB 트랜잭션에서 처리 완료 결과만 확정한다. 콜백은 짧은 DB 작업/outbox 저장에 한정하고 외부 호출은 포함하지 않는다. 실패는 업무/멱등성 레코드를 함께 rollback한다.
- API의 ID 정규식은 20자리도 허용하지만 실제 저장 범위는 양수 Java Long/PostgreSQL bigint다. Long.MAX_VALUE 초과는 400으로 거부한다. revision은 0..2147483647 숫자다.
