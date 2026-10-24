# 작업 15 시험 UI 계약 대조

## 화면 흐름

| 화면 | route | 상태 처리 |
|---|---|---|
| 시험 현황 | `/tests` | 예정·활성 응시·완료 횟수, 빈/로딩/오류 |
| 시험 응시 | `/tests/attempts/{attemptId}` | 답안 revision 저장, 남은 시간, 문제 이동, 제출 |
| 채점 상태 | 응시 route 유지 | WAITING/RUNNING polling, FAILED 제출 보존 안내 |
| 시험 결과 | `/tests/attempts/{attemptId}/result` | 점수·합격선·정답/오답/미응답과 문항별 해설 |

시안 `05_exam_status`, `06_exam_session`, `07_exam_result`의 정보 위계를 따르되, 순위·최근 평균처럼 현재 API에 없는 수치는 만들지 않았다. 북마크 역시 계약이 없어 추가하지 않았다.

