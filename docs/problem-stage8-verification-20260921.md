# 작업 8 문제·문제풀이·오답 복습 완료

2026-09-21 / [이슈 #7](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/7)

**문제 조회부터 제출·즉시 채점·오답 복습까지 Backend 운영 기능과 실제 PostgreSQL 검증을 완료했다.** 채택 근거는 [API/DB 대조](contracts/problem-stage8-alignment.md)에 기록했다.

## 사용할 수 있는 기능

- 배정된 객관식·참/거짓 문제를 기본 문제 ID로 조회하고 선택지를 노출한다. 정답과 해설은 제출 전 응답에 포함하지 않는다.
- 답안을 멱등하게 제출하면 불변 문제 버전을 기준으로 RULE 채점하고, 시도·채점 run·결과·오답 발생·복습 작업을 한 트랜잭션으로 저장한다.
- 같은 기본 문제의 재시도 횟수를 버전과 무관하게 계산한다. 오답 목록도 버전이 바뀌어도 한 항목으로 유지하고 가장 최근 오답 당시 내용을 보여 준다.
- 문제 오류 신고, 오답 목록·상세·메모, 정답 재시도 또는 수동 확인에 의한 해결, 해결 항목 재개를 지원한다.
- 정답 재시도 해결은 마지막 오답 뒤에 생성된 본인 정답 결과만 근거로 허용한다. 메모는 ETag/If-Match로 동시 수정을 보호한다.

연결한 API는 PROBLEM-001/002, ATTEMPT-001/002, WRONG-001~005의 **9개**다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `docker build --target build -t daily-career-backend-check backend` | PASS, 컴파일·일반/계약 테스트 38개·실행 JAR |
| PostgreSQL 프로필 `verify` | PASS, 일반 38 + PostgreSQL IT 52 = **90개**, 실패/오류/skip 0 |
| LearningOperationsIT | 21개 PASS. 실제 HTTP·CSRF·JDBC 세션·PostgreSQL로 문제/오답 전체 흐름 검증 |
| 제출·채점 | SINGLE_CHOICE/TRUE_FALSE 유효성, first/retry, 동시 멱등성, 잘못된 답안, 다른 사용자 접근 검증 |
| 오답 | 생성·누적·목록/상세·메모 ETag·수동/정답 근거 해결·재개·복습 작업 검증 |
| 원자성 | 오답 발생 저장 실패 시 시도·채점·이벤트·멱등성 전체 rollback, 같은 키 재시도 성공 |
| Flyway | V6 clean migration 및 V1→V3→V6 순차 업그레이드·재검증 PASS |
| 개발 적용 | Backend 재빌드·재생성 후 4서비스 healthy, Frontend runtime smoke PASS. Nginx 경유 신규 GET route가 404가 아닌 401 ErrorEnvelope로 보호됨 |
| 정적 확인 | `docker compose config --quiet`, `git diff --check` PASS |

테스트 중 보이는 `DataIntegrityViolationException`은 rollback을 확인하기 위해 의도적으로 제약을 발생시킨 기록이며 테스트 실패가 아니다.

## 데이터와 다음 범위

V6는 `problem_report`를 추가하고 `wrong_answer`에 복습 횟수·해결 방식·근거 시도를 저장한다. `problem_version.question_type`에는 공개 계약의 TRUE_FALSE를 추가했다. 기존 V1–V5는 수정하지 않았다.

개발 DB cluster ID `7687576957880160290`와 V1–V5 체크섬을 보존했고 V6 체크섬은 `1835651952`다. 적용 뒤 업무 schema는 **57테이블·631컬럼·123FK**이며, 운영 `problem`, `problem_report`, `wrong_answer`는 모두 0건이다.

사용자 결정에 따라 운영 학습 콘텐츠와 문항은 적재하지 않았다. 검증 fixture는 테스트에서만 생성되며 운영 JAR과 개발 DB seed에 포함되지 않는다. AI 직접 생성 문제는 작업 12, 문제풀이 화면은 작업 19 범위다. 다음 구현은 **작업 9 시험·제출·채점**이다. 커밋·푸시·PR은 수행하지 않았다.
