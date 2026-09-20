# 작업 7 휴식·일정 변경 API/DB 채택 기준

2026-09-21 / 관련 이슈 [#6](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/6)

| 공개 계약 | 구현 기준 |
|---|---|
| CURR-009 | `If-Match`의 현재 scheduleRevision을 잠금 안에서 확인하고 변경안을 10분간 `schedule_preview`에 저장한다. preview 생성은 실제 일정을 바꾸지 않는다. |
| CURR-007 | POLICY_APPLY와 SHIFT_BACKLOG preview만 실행한다. 일정·정책·이력·revision·preview 소비·멱등성 응답을 한 트랜잭션으로 확정한다. |
| REST-001/002 | 현재 과정의 REST_ADD/REMOVE preview와 날짜·사유를 다시 맞춘다. 정기 휴식요일 해제는 해당 날짜의 MANUAL STUDY 예외로 보존한다. |
| 이동 기준 | 오늘 이전과 시작·완료 학습일은 고정한다. 시험 응시가 존재하는 testVersion도 고정한다. 미래 미시작 학습 단위는 dayNo 순으로 가장 이른 허용 날짜에 둔다. |
| 식별자·이력 | `learning_day.id`와 연결된 `learning_day_item`, `assigned_content`를 유지하고 날짜만 이동한다. 완료 상태와 고정 필수 분모를 다시 만들지 않는다. |
| 동시성 | 사용자 `FOR NO KEY UPDATE` 뒤 과정·preview를 잠근다. 오래된 ETag는 412, 만료·소비·불일치 preview와 잠긴 학습일은 409다. |

V2의 `schedule_policy`, `schedule_rest_weekday`, `learning_day`, `schedule_change`와 V3의 `schedule_preview`가 필요한 관계와 이력을 이미 제공한다. 작업 7은 새 Flyway migration 없이 이 구조를 사용한다. 원본 V1–V5와 운영 데이터는 변경하지 않는다.

REST_ADD/REMOVE는 날짜 예외를 `learning_day.source=MANUAL`로 표현한다. 재배치할 때 MANUAL 의미를 행 ID가 아니라 날짜에 다시 적용하므로, 해당 학습 단위가 이후 이동해도 날짜 예외가 다른 학습 단위에 이어진다. POLICY_APPLY는 `effective_from`이 기존 정책 시작과 같으면 해당 정책을 갱신하고, 이후 날짜면 기존 기간을 닫고 새 정책 행을 만든다.
