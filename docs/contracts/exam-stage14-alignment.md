# 작업 14 시험·제출·채점 Backend 계약 대조

## 기준과 채택

- OpenAPI v1.2.1 `TEST-001~008`과 DB v0.1의 `test*`, `grading_run`, `grading_result`를 기준으로 했다.
- `DAILY`, `WEEKLY`, `MONTHLY`, `FINAL`은 배정된 `learning_day_item.test_version_id`에서 조회한다.
- 시험 시작 시 공개 문제와 서버 전용 정답·배점·루브릭을 서로 다른 JSON 스냅샷으로 고정한다.
- 답안은 `If-Match` revision으로 저장하고, 마감 뒤 저장은 차단한다. 제출은 Idempotency-Key로 한 번만 확정한다.
- 제출 트랜잭션은 `SUBMITTED`와 `QUEUED` run까지만 만든다. 별도 비동기 worker가 채점 결과를 원자적으로 완성한 뒤에만 `is_current=true`로 바꾼다.
- 채점 실패는 제출을 되돌리지 않고 V7 `grading_run.error_code`에 비민감 분류 코드만 남긴다. 실패 run 재처리는 기존 행 수정이 아니라 새 `run_no`로 생성한다.

## 경계

- 운영 학습 콘텐츠와 시험 seed는 사용자 결정대로 추가하지 않았다. 실제 시험은 과정에 발행 시험 버전이 배정될 때 목록에 나타난다.
- 이번 규칙 채점기는 객관식과 참/거짓을 처리한다. 서술형·코드·면접 AI/HUMAN 채점은 작업 18 provider 경계에서 확장한다.
- 오답 발생·진척 집계 이벤트의 시험 채점 연결은 작업 16의 지표 산식 확정과 함께 구현한다.

