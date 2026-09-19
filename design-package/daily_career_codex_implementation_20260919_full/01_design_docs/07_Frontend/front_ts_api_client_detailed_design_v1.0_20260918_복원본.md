# Frontend TypeScript / API Client 상세설계 v1.0 복원본

> 전용 방에서 상세설계를 완료했으나 현재 런타임에서 원본 파일 바이트를 회수할 수 없어 확정 계약 중심으로 복원했다.

## 타입 계약

- 모든 외부 ID: `string`
- ID를 `number`로 변환 금지
- API DTO 타입과 UI 상태 타입을 분리
- `API DTO → mapper → ViewModel → Component`

## API Client

도메인 기준 모듈화하되 실제 OpenAPI에 없는 API를 만들지 않는다.

예시 도메인:
- auth
- user
- curriculum
- dashboard
- learning day/session/content
- problem/wrong answer
- test
- progress
- calendar/rest
- evaluation
- ai
- notification/push
- notion
- slack
- settings

## 인증/보안

- same-origin session cookie
- CSRF 토큰 메모리 보관
- 쓰기 요청 `X-CSRF-TOKEN`
- 로그인/로그아웃 후 CSRF 재취득

## Revision

- GET의 ETag/revision 보관
- mutation 시 If-Match 전달
- 412/428은 최신 데이터 재조회 UX로 연결

## 멱등성

필요한 mutation에서 UUID Idempotency-Key 생성/보존.
같은 사용자 액션의 네트워크 재시도는 동일 키를 재사용하고, 사용자가 새 업무를 다시 수행하면 새 키를 발급한다.

## TanStack Query

- queryKey는 도메인/리소스/식별자/조건을 안정적으로 구성
- mutation 성공 후 실제 영향을 받는 query만 invalidate
- revision/멱등성 mutation은 무조건 optimistic update하지 않음
- 비동기 AI/외부동기화는 제한된 polling 후 명시적 재조회 정책 적용

## 오류

HTTP 상태와 서버 ErrorCode를 분리해 해석한다. 사용자 메시지는 서버 코드 → 프론트 메시지 매핑을 사용한다.

## AI

사용자 취소 mutation/hook/button을 만들지 않는다.
