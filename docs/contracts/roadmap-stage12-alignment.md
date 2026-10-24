# 작업 12 로드맵·휴식·일정 변경 UI 채택 기준

2026-09-21 / 원격 이슈 연결 대기

| 관심사 | 채택 기준 |
|---|---|
| 로드맵 조회 | 현재 과정의 `CURR-003`, 월 목록 `CURR-004`, 주 목록 `CURR-005`를 조합한다. API DTO는 roadmap feature 내부 decoder를 통과한 뒤 ViewModel로 표시한다. |
| 진척 표현 | `progressRate`와 `completionRate`의 `null`은 0%가 아니라 `산정 전`으로 표시한다. 월·주 세션 분자/분모를 함께 표시한다. |
| 일정 미리보기 | `CURR-009`에 현재 과정 GET의 ETag와 새 Idempotency-Key를 보낸다. 휴식 추가·해제, 정책 변경, 밀린 일정 재배치를 같은 preview 화면에서 구분한다. |
| 일정 확정 | `REST_ADD`는 REST-001, `REST_REMOVE`는 REST-002, `POLICY_APPLY`와 `SHIFT_BACKLOG`는 CURR-007로 확정한다. preview의 revision을 `If-Match`에 사용한다. |
| 재진입 상태 | 미소비 preview를 같은 탭의 `sessionStorage`에만 저장한다. 과정 ID·scheduleRevision이 같고 `expiresAt` 이전일 때만 복원하며, 확정·취소·만료·stale이면 폐기한다. |
| 경쟁·만료 | 409/412/428은 기존 preview를 확정하지 않고 현재 과정과 로드맵을 다시 조회하도록 안내한다. 사용자는 항상 새 미리보기를 확인해야 한다. |
| 보존 안내 | 화면은 시작·완료 학습이 고정된다는 작업 7 규칙과, 확정 후 되돌리기 대신 새 변경안을 만든다는 점을 명시한다. |

작업 10 로그인·온보딩과 작업 11 오늘 학습의 route/component에 의존하지 않는다. 인증·CSRF 복원, 공통 AppShell, API transport, StatePanel만 작업 9 기반에서 재사용한다. 백엔드 API·DB 계약은 변경하지 않는다.
