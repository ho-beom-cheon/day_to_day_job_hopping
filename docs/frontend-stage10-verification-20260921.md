# 작업 10 로그인·온보딩 UI 완료 보고서

관련 작업 범위는 [이슈 초안](frontend-stage10-issue-draft.md), 계약 결정은 [계약 대조](contracts/frontend-stage10-alignment.md)를 따른다. 다른 채팅에서 병렬 진행 중인 작업 11–14의 route·feature는 수정하지 않았다.

## 사용자 기능

- 시작 화면이 익명, 인증 확인 실패, 로그인 사용자 상태를 구분한다. Google 로그인 후 현재 과정 유무에 따라 온보딩 또는 학습 공간으로 이어진다.
- `LOGIN_FAILED`, `ACCOUNT_NOT_ALLOWED` 고정 실패 경로를 별도 화면으로 제공하고 Google 비밀번호·토큰을 저장하지 않는다는 경계를 표시한다.
- 신규 사용자는 발행된 6개월 과정, 시작일, 하루 학습 시간, 정기 휴식 요일을 선택해 최초 과정을 배정할 수 있다.
- 템플릿 없음은 임시 데이터로 숨기지 않고 운영 콘텐츠 미배포 상태로 안내한다. 조회 오류, 세션 만료, 배정 처리·실패도 각각 구분한다.
- 배정 요청은 CSRF와 동일한 UUID 멱등 키를 사용하고 실패 후 같은 요청 재시도에 payload·키를 유지한다. 완료 후 현재 사용자 상태를 갱신해 학습 공간으로 이동한다.
- `/workspace` 직접 진입 시 서버의 `curriculumId`가 없으면 온보딩으로 보내고, 있으면 기존 앱 셸을 유지한다.

## 검증 결과

| 확인 | 결과 |
|---|---|
| `npm run typecheck` | PASS |
| 작업 10 대상 ESLint | PASS, 경고 0개 |
| 전체 Vitest 단일 worker | PASS, 17파일 65개 |
| 작업 10 신규 테스트 | PASS, 3파일 11개 |
| `npm run build` | PASS, `/`, `/login`, `/onboarding` 포함 production build |
| 전체 ESLint | 작업 10 오류 0개. 병렬 작업 12·13 파일의 `set-state-in-effect` 2건으로 전체 명령은 아직 실패하며 해당 파일은 수정하지 않음 |
| Chromium 데스크톱 | 1440×900 온보딩 구조·입력·요약 확인, document scrollWidth/clientWidth 1249/1249 |
| Chromium 모바일 | 375×812에서 실제 content clientWidth 360, scrollWidth 360. 요일 7개, 날짜·시간 입력, CTA와 안내 카드 잘림 없음 |
| 로그인 거부 모바일 | `ACCOUNT_NOT_ALLOWED` 문구, 재시도, 홈 이동, 보안 안내 표시. 375/375, 가로 넘침 없음 |
| 브라우저 콘솔 | 온보딩 모바일 확인 시 오류 0, 경고 0 |
| 배정 요청 | POST, CSRF 존재, Idempotency-Key 36자, 문자열 templateId와 60분·주말 2일 정책 확인 |

브라우저 API 응답은 화면 상태와 요청 계약 검증을 위해 모의했다. 실제 Google 계정 로그인과 실제 PostgreSQL 배정 API 자체는 작업 5·6에서 각각 이미 검증됐으며, 이번 작업에서 운영 템플릿을 새로 적재하거나 실제 사용자 과정을 생성하지 않았다.
