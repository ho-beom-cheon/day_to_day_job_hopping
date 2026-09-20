# 작업 8 문제·문제풀이 API/DB 채택 기준

2026-09-21 / 관련 이슈 [#7](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/7)

| 공개 계약 | 구현 기준 |
|---|---|
| PROBLEM-001 | 외부 `problemId`는 `problem.id`다. 사용자의 배정 콘텐츠로 접근을 제한하고 최신 발행 `problem_version`을 읽되 정답·해설은 제출 전에 반환하지 않는다. |
| ATTEMPT-001/002 | 제출 시 선택된 불변 `problem_version.id`를 시도에 고정한다. SINGLE_CHOICE와 TRUE_FALSE는 동기 RULE 채점하며 시도·run·결과·이벤트·오답·복습 작업·멱등성 결과를 한 트랜잭션으로 확정한다. |
| PROBLEM-002 | 신고는 조회 시점의 불변 문제 버전과 사용자, 유형, 설명을 저장한다. |
| WRONG-001/002/005 | 오답은 사용자+기본 문제 하나로 묶는다. 여러 버전의 발생 횟수를 합산하고 최근 오답 당시 버전·답안을 상세에 사용한다. 메모는 revision/ETag로 보호한다. |
| WRONG-003/004 | MANUAL_REVIEW는 사용자 확인으로 해결한다. CORRECT_RETRY는 마지막 유효 오답 뒤의 본인 정답 결과만 허용한다. 재개하면 PLANNED 복습 작업을 만든다. |
| 콘텐츠 범위 | 운영 콘텐츠를 만들지 않는다. 자동 검증에서만 문항 fixture를 생성하며 AI 직접 생성 문제와 비동기 채점은 후속 단계다. |

V6는 기존 저장 구조를 roll-forward 방식으로 보완한다. `problem_report`를 추가하고 `wrong_answer.review_count`, `resolution_type`, `resolution_attempt_id`를 추가했다. 공개 TRUE_FALSE를 `problem_version.question_type` 제약에 반영했다. 적용된 V1–V5는 그대로 보존한다.

API의 문제 식별자는 안정적인 기본 문제 ID이고, 채점·오답 발생·신고가 참조하는 내부 식별자는 당시 불변 버전 ID다. 따라서 콘텐츠 교정 뒤에도 과거 채점 근거가 바뀌지 않으며 사용자 오답 항목은 중복되지 않는다.
