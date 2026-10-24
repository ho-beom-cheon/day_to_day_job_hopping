# 작업 12 로드맵·휴식·일정 변경 UI 검증

2026-09-21 / 원격 이슈 연결 대기

## 구현 범위

- `/roadmap`에서 현재 과정의 6개월 월별 여정과 포함 주차를 조회한다.
- 휴식일 추가·해제, 밀린 일정 재배치, 하루 학습시간/정기 휴식요일 정책 변경을 미리보기한다.
- 영향받는 학습일과 기존·새 종료일을 확인한 뒤에만 확정한다.
- 10분 preview를 같은 탭 재진입에서 복원하고, revision 변경·만료·취소·확정 시 폐기한다.
- 일정 충돌과 stale 응답은 최신 데이터 재조회 후 새 미리보기를 요구한다.

## 계약 검증

- `data:null` 현재 과정, 64-bit ID 문자열, nullable progress, scheduleRevision/ETag을 구분하는 decoder·mapper 테스트를 추가했다.
- current query는 `{ data, etag }`를 유지하고, month/week query는 decoder가 반환한 data만 feature에 전달한다.
- 적용 API는 action별로 CURR-007, REST-001, REST-002를 분기하며 preview에 사용한 revision을 If-Match로 보낸다.

## 실행 결과

| 검증 | 결과 |
|---|---|
| 작업 12 전용 Vitest | PASS, 3 files / 9 tests |
| 작업 12 route·feature ESLint | PASS, warning 0 |
| `docker build --target build -t daily-career-frontend-stage12-check frontend` | PASS |
| TypeScript / 전체 ESLint | PASS |
| 전체 Vitest | PASS, 17 files / 66 tests |
| Next production build | PASS, 정적 `/roadmap` route 생성 |
| `git diff --check` | PASS. 병렬 작업 파일의 기존 LF→CRLF 경고만 출력 |
| 공통 AppShell 병합 | PASS, 로드맵 메뉴 활성화 및 현재 route 강조 |
| 비로그인 브라우저 진입 | PASS, `/roadmap`에서 로그인 필요 상태와 Google 로그인 동선 표시 |

## 미검증·후속

- 실제 운영 콘텐츠가 없는 환경의 인증 사용자 브라우저 흐름과 데스크톱·모바일 시각 검증은 fixture 또는 검토된 콘텐츠 적재 후 통합 검증한다.
- 전체 시안 대조와 캐릭터 자산은 작업 20 범위다.
