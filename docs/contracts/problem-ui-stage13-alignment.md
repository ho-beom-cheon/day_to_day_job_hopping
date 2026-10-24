# 작업 13 문제·오답·복습 UI 계약 대조

## 대조 기준

- OpenAPI v1.2.1 `PROBLEM-001/002`, `ATTEMPT-001/002`, `WRONG-001~004`
- 작업 8 Backend의 `LearningController`, `ProblemViews`, `ProblemService`
- 작업 9의 Envelope decoder, same-origin API client, SessionGate, 상태·오류 UI
- UI 시안 `04_practice_question.png`, `07b_wrong_answer_notebook.png`

## 채택한 흐름

| 사용자 흐름 | API 및 계약 | UI 처리 |
|---|---|---|
| 문제 조회 | `GET /problems/{problemId}` | 유형·난이도·보기와 이전 제출 횟수를 표시하고 정답은 노출하지 않음 |
| 답안 제출 | `POST /problem-attempts`, Idempotency-Key | 한 논리 액션의 payload/key를 고정하고 제출 후에만 정답·해설 표시 |
| 재풀이 | `previousAttemptCount`, `attemptNo` | 재풀이임과 첫 풀이 정답률에서 분리됨을 명시 |
| 오답 목록 | `GET /wrong-answers?status=` | 전체/미해결/해결 완료를 구분하고 빈 상태를 표시 |
| 오답 상세 | `GET /wrong-answers/{id}` + ETag | 마지막 오답·정답·해설·메모·해결 상태 표시 |
| 메모 저장 | `PATCH`, If-Match | 조회 ETag를 전송하며 412/428은 최신 정보 재조회로 안내 |
| 해결/재복습 | `POST .../resolve`, `POST .../reopen` | 수동 학습 확인과 다시 복습 액션을 상태별로 분리 |

## 시안과의 차이

- 모바일 시안의 문제 진행률과 남은 시간은 단일 문제 API에 전체 세트/시간 계약이 없어 임의 계산하지 않았다.
- 시안의 오답 목록 검색은 서버 검색 parameter가 없어 추가하지 않았다. 상태 필터는 공개 계약을 그대로 사용한다.
- 카테고리 아이콘은 별도 자산 계약 없이 공통 복습 아이콘을 사용했다. 작업 20의 전체 디자인 보완 때 자산 체계를 함께 정리한다.
- 문제 진입은 현재 문제 목록 API가 없으므로 학습 콘텐츠 또는 오답 상세에서 알려진 `problemId`를 전달받는 route로 제한한다.

## 작업 14 영향

시험 API, 시험 문제 타입, 답안 저장/제출/채점 계약은 수정하지 않았다. 작업 13은 일반 연습 문제와 오답 계약만 별도 feature decoder로 구현한다.
