# API 인터페이스 v1.2.1 최종 기준

## 메타데이터

- OpenAPI: 3.1.1
- API 문서 버전: 1.2.1
- 경로 버전: `/api/v1`
- 전체 인터페이스: 91
- JSON 인터페이스: 88
- OAuth 리다이렉트: 3
- 스키마: 217

## v1.2.1 P0 패치

- `completionRate`: 전체 커리큘럼 완료율
- `adherenceRate`: 일정 이행률
- `masteryRate`: 문제·시험 기반 숙련도
- `firstAttemptAccuracy`: 첫 풀이 정답률
- 일/주/월/분야 내부의 단순 완료 진행률은 `progressRate` 유지 가능
- 전체 커리큘럼의 모호한 `progressRate` 제거
- AI 사용자 취소 API 없음
- 공개 OpenAPI에 CANCELED 상태 없음

## 공통 계약

- Session Cookie 인증
- CSRF 쓰기 요청 보호
- 개인화 응답 Cache-Control: no-store
- X-Trace-Id
- ETag/If-Match 낙관적 동시성
- Idempotency-Key 기반 멱등성
- ErrorEnvelope 공통 오류

## 원본 파일 참고

현재 File Library에 `openapi.yaml` v1.2.1 원본이 존재한다. 다만 이 ZIP 생성 런타임에서는 File Library 참조를 파일 바이트로 직접 복사하는 인터페이스가 없어 원본 YAML 자체는 이 패키지에 비트 단위로 포함하지 못했다.
개발 시 File Library의 최신 `openapi.yaml`을 계약 원본으로 사용한다.
