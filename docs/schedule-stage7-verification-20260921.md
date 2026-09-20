# 작업 7 휴식일·일정 변경 완료

2026-09-21 / [이슈 #6](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/6)

**구현과 실제 PostgreSQL 검증을 완료했다.** 채택 근거는 [API/DB 대조](contracts/schedule-stage7-alignment.md)에 기록했다.

## 사용할 수 있는 기능

- 휴식일 추가·해제, 현재 과정의 학습시간/정기 휴식요일 정책 교체, 밀린 일정 당기기의 변경 결과를 실제 반영 전에 확인한다. preview는 10분 뒤 만료된다.
- confirm은 preview에서 본 이동 수·이전/새 종료일과 같은 계획만 반영한다. 다른 탭의 학습 시작·완료나 일정 변경으로 revision이 달라지면 다시 조회하도록 거절한다.
- 시작·완료 학습과 응시가 생성된 시험은 고정하고 미래 미시작 학습만 원래 dayNo 순서로 이동한다. 학습일·세션·콘텐츠 ID와 완료 이력은 그대로 유지한다.
- 정기 휴식요일도 특정 날짜에 한해 학습일로 바꿀 수 있으며, 이후 정책을 교체하거나 다시 재배치해도 그 날짜 예외를 유지한다.

연결한 API는 CURR-009, CURR-007, REST-001, REST-002의 **4개**다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `docker build --target build -t daily-career-backend-check backend` | PASS, 컴파일·일반/계약 테스트 38개·실행 JAR |
| `docker run --rm --name daily-career-postgres-it-stage7 ... -Ppostgres-it verify` | PASS, 일반 38 + PostgreSQL IT 49 = **87개**, 실패/오류/skip 0 |
| LearningOperationsIT | 19개 PASS. 작업 7 시나리오 5개를 실제 HTTP·CSRF·JDBC 세션·PostgreSQL로 추가 |
| preview/apply | 네 action, 이동 수·종료일 일치, 10분 만료, 1회 소비, 요청 불일치, stale ETag 검증 |
| 보존 | 이동 전후 learningDayId와 하위 세션·콘텐츠 연결 유지, 시작 학습 고정, MANUAL 날짜 예외 유지 |
| 동시성·rollback | 동시 확정 1회만 반영. schedule_change 저장 실패 시 날짜·revision·preview 소비·멱등성 기록 rollback 후 같은 키 재시도 성공 |
| DB | 신규 migration 없음. V1–V5 체크섬과 기존 개발 데이터 보존 |
| 개발 적용 | Backend 재빌드·재생성 후 4서비스 healthy, Frontend runtime smoke PASS. 신규 POST route는 CSRF 없이 403 ErrorEnvelope 반환 |
| `docker compose config --quiet`, `git diff --check` | PASS |

테스트의 의도된 `DataIntegrityViolationException` 로그는 rollback 검증을 위해 추가한 제약이 요청을 거절한 기록이며 테스트 실패가 아니다.

개발 DB cluster ID `7687576957880160290`, V1–V5 체크섬 `1873624569`, `-1313458866`, `1647492594`, `-803042091`, `53147123`과 업무 테이블 56개를 유지했다. 운영 콘텐츠가 없어 schedule_preview와 schedule_change는 모두 0건이다. 첫 smoke는 Backend health가 `starting`인 재생성 직후 실행되어 연결 거절됐고, health가 healthy가 된 뒤 재실행해 통과했다.

## 다음 범위

운영 콘텐츠와 일정 변경 화면은 아직 없다. 실제 사이트는 준비 화면을 유지한다. 다음은 사용자 요청 시 **작업 8 문제·문제풀이**다. 커밋·푸시·PR은 수행하지 않았다.
