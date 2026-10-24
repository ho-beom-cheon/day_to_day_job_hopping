# 작업 11 대시보드·오늘 학습 UI 채택 기준

## API 연결

작업 11은 초기에는 개별 과정·학습일 API를 조합했다. 현재 홈과 학습 화면은 구현된 통합 `DASH-001`을 사용하며, 같은 DB 스냅샷의 과정·오늘 일정과 최근 실제 활동을 읽는다.

| 사용자 기능 | API | 채택 내용 |
|---|---|---|
| 대시보드 | `GET /api/v1/dashboard` | 현재 과정·오늘 일정·실제 진행/완료 세션 4건을 한 스냅샷에서 조회한다. 실패하면 오류 상태와 재시도를 제공한다. |
| 세션 조회·시작 | `GET /api/v1/learning-sessions/{sessionId}`, `POST .../start` | 세션 시작 시 서버 시작 시각을 사용하며 성공 후 대시보드·세션 캐시를 갱신한다. |
| 콘텐츠 조회·완료 | `GET .../contents`, `GET /learning-contents/{contentId}`, `POST .../complete` | `READ_ACK`만 직접 완료할 수 있고 문제 풀이 기반 완료는 문제 UI에 맡긴다. |
| 세션 완료 | `POST /api/v1/learning-sessions/{sessionId}/complete` | 실제 학습 시간 1~480분을 보내고 한 논리 요청의 payload와 Idempotency-Key를 재시도 동안 고정한다. |

## 상태와 수치

- 과정 없음, 오늘 일정 없음, 휴식일, 학습 전, 진행 중, 완료를 서로 다른 화면 상태로 표시한다.
- 과정 완료율과 오늘 진행률은 서로 다른 분모를 그대로 사용한다.
- 완료 과정의 `currentWeek`·`currentDayNo`는 null을 허용한다.
- 휴식일·미편성일의 day status인 `REST`·`NONE`은 세션 status와 분리한다.
- 숙련도와 첫 풀이 정답률은 작업 16 전까지 수치를 만들지 않고 `평가 전`으로 표시한다.
- 모든 ID는 JSON 문자열로 검증하고 JavaScript number로 변환하지 않는다.

## 후속 영향

- 최근 활동은 현재 과정의 `IN_PROGRESS`·`COMPLETED` 학습 세션만 보여준다. 오늘 휴식일에도 이전 활동을 유지하고 미시작 세션을 활동으로 표시하지 않는다.
- 문제 풀이 완료가 필요한 콘텐츠는 작업 13 화면이 연결된 뒤 해당 문제 route로 진입시킨다.
- 운영 콘텐츠가 준비되기 전까지 로그인한 실제 데이터 화면의 종단 간 검증은 테스트 fixture와 구분해 기록한다.
