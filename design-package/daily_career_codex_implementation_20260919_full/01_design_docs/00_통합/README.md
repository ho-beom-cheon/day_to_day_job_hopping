# 데일리 이직 최종 설계 패키지

- 기준일: 2026-09-19 (Asia/Seoul)
- 대상: 6개월 데일리 이직 교육 웹앱
- 상태: 개발 착수용 통합 기준선

## 패키지 성격

이 ZIP은 각 전용 대화방에서 확정된 최신 결정을 한 곳에 모은 **개발 인수인계 패키지**다.
현재 실행 환경에서 과거 대화방의 sandbox 파일 바이트를 직접 가져올 수 없는 산출물은 최신 확정 내용을 기준으로 `복원본`을 작성했다.
따라서 이 ZIP은 모든 과거 원본 파일의 비트 단위 아카이브가 아니라, **현재 개발자가 따라야 할 최신 기준선**이다.

### 원본/복원 상태

| 영역 | 최신 기준 | 패키지 상태 |
|---|---|---|
| 기능 설계 | v0.1 계열 최종 결정 | 복원 최종기준 |
| 제작 스펙 | v1.1 | 복원 최종기준 |
| UI/UX | v0.3, 43화면+22오버레이 | 복원 최종기준 |
| 핵심 시안 | 10종 완료 | 완료 목록/적용 기준 |
| DB/ERD | v0.2, 46테이블/558컬럼/105FK | 복원 최종기준 |
| API | OpenAPI v1.2.1 | 계약 요약 + 원본 참조 메타데이터 |
| API 정적검증 | v1.2.1 PASS | 원문 내용 수록 |
| Backend DTO/Controller | 상세설계 완료 | 복원 최종기준 |
| Frontend TS/API Client | v1.0 | 복원 최종기준 |
| Notion | 단방향 비동기 동기화 | 복원 최종기준 |
| Slack/Web Push | 비동기 알림 | 복원 최종기준 |
| 캐릭터 | 공식 HoBeom 캐릭터 확정 | 디자인 기준 기록 |

## 구현 전제

1. DB ID는 bigint, Java는 Long, 외부 JSON과 TypeScript는 string이다.
2. 프론트는 식별자를 Number/parseInt/단항 + 로 변환하지 않는다.
3. 진척 지표는 adherenceRate / completionRate / masteryRate / firstAttemptAccuracy를 분리한다.
4. AI 사용자 취소 기능은 제공하지 않는다.
5. 일정 변경은 preview → confirm, revision 기반 낙관적 동시성으로 처리한다.
6. 시험 제출과 채점 상태는 분리하며, 채점 실패가 제출 성공을 무효화하지 않는다.
7. 필요한 mutation은 Idempotency-Key를 사용한다.
8. PostgreSQL이 원본이며 Notion/Slack/Web Push 실패는 핵심 업무 트랜잭션을 롤백하지 않는다.

## 개발 착수 상태

- API 진척도 P0 해결: 완료
- 핵심 시안 10종: 완료
- 주요 상세설계: 완료
- 판정: **개발 GO**
