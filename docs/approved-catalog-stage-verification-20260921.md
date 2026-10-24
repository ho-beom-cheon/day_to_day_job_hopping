# 승인 커리큘럼·문제·시험 데이터 준비 검증

## 범위

- `dev-seed` 전용 Flyway V8.1에 6개월 백엔드 기초 과정을 발행했다.
- Java, Spring Boot, SQL, 알고리즘, 금융 도메인, 기술 면접 본문·확인 문제를 각 1개씩 연결했다.
- DAILY, WEEKLY, MONTHLY, FINAL 시험 4개를 발행하고 과정 학습일에 배정했다.
- 사용자·풀이·진척 기록은 seed하지 않았고 production migration과 `prod` profile은 변경하지 않았다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| Backend Docker build / 일반 테스트 40개 | PASS |
| clean PostgreSQL 17.11 V1→V8.1 migration | PASS, 9개 migration |
| `LearningCatalog.plan(..., true)` 전체 발행 규칙 | PASS |
| 6개월·6주·6학습일, 문제 6개, 시험 4개 | PASS |
| Flyway 재실행 | PASS, 0 migration / 중복 0 |
| 기존 개발 volume V8→V8.1 | PASS, 기존 데이터 및 volume 보존 |
| Browser `/workspace`, `/roadmap`, `/tests`, `/practice/1` | PASS, 실제 승인 데이터 표시 |
| Compose 4서비스 | PASS, running/healthy |

최초 검증에서 PUBLISHED 콘텐츠의 자식 메타데이터 추가를 불변성 트리거가 거부했다. DRAFT 구성 완료 후 콘텐츠와 과정을 순서대로 발행하도록 수정했고, 실패 migration은 전체 rollback된 뒤 clean DB 재검증을 통과했다.

시험 목록 첫 브라우저 검증에서 PostgreSQL JDBC의 `date`가 `java.sql.Date`로 반환되어 기존 직접 `LocalDate` cast가 500을 발생시켰다. 두 반환 타입을 모두 처리하도록 수정한 뒤 시험 4개가 정상 표시되고 console error가 없음을 확인했다.

기존 runtime smoke는 개발 인증 우회 활성 시 401을 기대하는 assertion 때문에 실패했다. 이는 이전 단계에서도 확인된 검증 구성 충돌이며, 이번 데이터·DB·화면 검증 PASS로 계산하지 않았다.

대시보드의 빠른 메뉴가 연결하는 `/practice`는 정적 route가 없어 404였다. 배정된 문제의 실제 route `/practice/1`은 지문·보기·제출 상태를 정상 표시했다. 빠른 메뉴 route 결정은 별도 UI 보완 범위로 남겨 두었다.

## 활성화

`.env`의 `APP_PROFILES=dev,dev-seed`를 유지하고 Backend를 재생성한다. V8.1을 적용한 volume에서는 해당 volume 수명 동안 `dev-seed` profile을 제거하지 않는다.
