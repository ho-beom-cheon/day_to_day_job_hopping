# 데일리 이직 DB/ERD v0.2 복원 최종 기준

> 과거 전용 방의 v0.2 원본 파일 바이트를 현재 실행 환경에서 직접 가져오지 못해, 최신 확정 기록을 기준으로 복원한 구현 기준선이다.

## 규모

- 업무·운영 테이블: 46
- 컬럼: 558
- FK: 105
- Spring Session JDBC 테이블은 46개 업무 테이블 외 별도
- Flyway 이력 테이블도 별도

## 주요 도메인

1. 사용자/OIDC/세션
2. 커리큘럼 템플릿/버전/배정
3. 학습일/학습세션/콘텐츠 인스턴스
4. 문제 원형/버전/풀이/채점
5. 시험 원형/버전/응시/답안/채점
6. 오답/오답노트/복습 예약
7. 진척 집계/월말평가
8. 휴식/일정 변경 preview 및 변경 이력
9. AI job/usage
10. 알림/외부 동기화/outbox
11. idempotency 기록

## 무결성 원칙

- 콘텐츠/문제/시험 버전은 과거 결과 재현이 가능해야 함
- 시험 제출과 채점 실행을 분리
- 제출 후 채점 실패 시 답안 유지
- 재채점은 새 grading_run
- 일정 변경은 완료/진행 중 학습을 고정
- revision으로 경쟁 업데이트 방지
- idempotency_record로 중복 실행 방지
- 외부 동기화는 outbox 이후 수행

## ID

내부 PK는 bigint. Java Long. 외부 JSON에는 문자열로 직렬화한다.

## 진척도

- adherenceRate: 일정 이행률
- completionRate: 전체 커리큘럼 완료율
- masteryRate: 문제/시험 기반 숙련도
- firstAttemptAccuracy: 첫 풀이 정답률

## 런타임 검증

구현 단계에서 PostgreSQL + Flyway + JPA + Testcontainers로 실제 제약/동시성/트랜잭션을 검증한다.
