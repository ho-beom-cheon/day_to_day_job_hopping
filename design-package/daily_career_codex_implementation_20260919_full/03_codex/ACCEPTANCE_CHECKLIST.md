# 구현 인수 체크리스트

## 계약
- [ ] 모든 외부 ID가 JSON string / TypeScript string이다.
- [ ] 프론트에서 ID를 Number/parseInt/+id로 변환하지 않는다.
- [ ] 공개 API는 `/api/v1` 계약을 따른다.
- [ ] 임의 공개 엔드포인트가 추가되지 않았다.
- [ ] OpenAPI enum/required/nullable/HTTP status를 구현이 변경하지 않는다.

## 동시성/멱등성
- [ ] revision/ETag/If-Match 연결이 끊기지 않는다.
- [ ] stale 수정은 412, If-Match 누락은 428 계약을 따른다.
- [ ] Idempotency-Key가 필요한 mutation에서 동일 사용자 액션 재시도 시 같은 키를 유지한다.
- [ ] 같은 키+다른 payload는 409 처리한다.

## 시험
- [ ] 제출 성공과 채점 성공을 합치지 않는다.
- [ ] 제출 후 채점 실패에도 답안/제출이 보존된다.
- [ ] 재채점은 새 grading run이다.
- [ ] 성공한 grading만 공식 결과가 된다.

## 진척도
- [ ] adherenceRate = 일정 이행률
- [ ] completionRate = 전체 커리큘럼 완료율
- [ ] masteryRate = 문제·시험 기반 숙련도
- [ ] firstAttemptAccuracy = 첫 풀이 정답률
- [ ] 네 지표를 서로 대체하지 않는다.
- [ ] 계산 불가 시 임의 0 대신 null/평가 전을 사용한다.

## AI
- [ ] 사용자 cancel API 없음
- [ ] 사용자 cancel 버튼 없음
- [ ] 비동기 job 조회 구조 유지

## 외부 연동
- [ ] Notion/Slack/Web Push 장애가 학습/시험 원 업무를 롤백하지 않는다.
- [ ] outbox 또는 동등한 영속 이벤트가 원 업무 트랜잭션에 포함된다.

## UI
- [ ] 모바일 우선 반응형
- [ ] 핵심 10화면 디자인 패턴 일관성
- [ ] Loading/Empty/Error/Disabled/Completed/Processing 상태 구현
- [ ] Conflict/Stale 상태 구현
- [ ] External Sync Partial Failure 상태 구현

## 실행
- [ ] Backend build/test 통과
- [ ] Frontend typecheck/lint/build 통과
- [ ] Flyway clean DB migration 검증
- [ ] Docker Compose 로컬 기동 검증
