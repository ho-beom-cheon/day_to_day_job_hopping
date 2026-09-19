# 구현 기준선 2026-09-19

## 1. 공통 타입 계약

| 계층 | ID 타입 |
|---|---|
| PostgreSQL | bigint |
| Java | Long |
| JSON/OpenAPI | string |
| TypeScript | string |

프론트에서 ID를 숫자로 변환하지 않는다.

## 2. 진척도 계약

- `adherenceRate`: 일정 이행률
- `completionRate`: 전체 커리큘럼 완료율
- `masteryRate`: 문제·시험 기반 숙련도
- `firstAttemptAccuracy`: 첫 풀이 정답률

`masteryRate`와 `firstAttemptAccuracy`는 별도 지표다. 일/주/월/분야 내부의 단순 완료 진행률은 문맥이 명확한 경우 `progressRate`를 유지할 수 있다.

## 3. Revision

- 조회 응답에서 revision/ETag 확보
- 조건부 수정에 If-Match 사용
- 누락: 428 PRECONDITION_REQUIRED
- 오래된 revision: 412 PRECONDITION_FAILED
- 프론트는 충돌 시 최신 데이터 재조회 UX를 제공한다.

주요 revision: scheduleRevision, answerRevision, connectionRevision, targetsRevision.

## 4. 멱등성

업무 중복 실행이 위험한 mutation은 `Idempotency-Key: UUID`를 사용한다.

- 동일 키 + 동일 요청: 기존 결과 재사용
- 동일 키 + 다른 payload: 409 IDEMPOTENCY_KEY_REUSED
- 처리 중 동일 요청: REQUEST_IN_PROGRESS 정책 적용

## 5. 일정

- preview → confirm
- 과거 및 시작된 학습은 변경하지 않음
- 미래 미시작 학습만 이동
- 기본 정책은 일일 학습량 압축이 아니라 예상 종료일 연장
- 확정 후 scheduleRevision 증가

## 6. 시험/채점

`답안 저장 ≠ 시험 제출 ≠ 채점 완료`

- 제출 성공 후 채점 실패가 발생해도 제출과 답안은 유지
- 재채점은 새로운 grading run 생성
- 성공한 결과만 공식 결과로 전환
- UI는 제출 완료/채점 중/채점 완료/채점 실패를 구분

## 7. AI

- 사용자용 cancel API 없음
- 사용자용 취소 버튼 없음
- CANCELED는 공개 API 계약에 노출하지 않음
- 비동기 Job: 요청 → 202/jobId → 상태 조회 → 성공/실패

## 8. 외부 연동

핵심 업무 DB commit과 외부 연동을 분리한다.

`업무 저장 → outbox → Notion/Slack/Web Push`

외부 장애가 학습 완료, 문제 제출, 시험 제출/결과 등 핵심 업무를 롤백시키면 안 된다.

## 9. 개발 원칙

- OpenAPI를 외부 계약 기준으로 사용
- 서버 DTO와 TS API 타입은 1:1 대응 후 UI ViewModel로 변환
- 문서와 구현이 충돌하면 임의 보정하지 말고 해당 계약 소유 산출물만 수정
- 구현 중 발견되는 문서 품질 이슈는 개발을 막지 않는다. 실제 재작업/데이터 무결성 위험만 blocker로 취급한다.
