# 작업 13 문제·오답·복습 UI 검증

2026-09-21 / 원격 이슈 연결 대기

## 구현 결과

- `/practice/{problemId}`에서 일반 문제 조회, 유형별 답 선택, 답안 제출, 정답·해설과 오답노트 이동을 제공한다.
- 이전 제출이 있는 문제는 재풀이 회차와 첫 풀이 지표 분리를 안내한다.
- `/review`에서 전체·미해결·해결 완료 오답을 필터링하고 빈/로딩/오류 상태를 구분한다.
- `/review/{wrongAnswerId}`에서 오답·정답·해설·메모를 복습하고 해결 확인 또는 다시 복습으로 상태를 바꿀 수 있다.
- 메모 변경은 조회 ETag를 If-Match로 전달하며 공통 412/428 최신 정보 재조회 UX를 재사용한다.
- 앱 셸의 복습 메뉴만 활성화하고 현재 route에만 `aria-current`가 적용되도록 했다.

## 검증 기록

| 검증 | 결과 |
|---|---|
| 계약 decoder 테스트 | PASS, 1 file / 4 tests |
| 화면 상호작용 테스트 | PASS, 1 file / 3 tests — 제출 전/후 해설 노출, 오답 상태 filter query, 412 reset+refetch 복구 |
| 전체 Vitest | PASS, 18 files / 69 tests |
| TypeScript | PASS, `next typegen && tsc --noEmit` |
| 작업 13 ESLint | PASS, 문제 feature/routes와 app-shell warning 0 |
| 전체 ESLint | PASS, `eslint . --max-warnings=0` |
| Next production build | PASS, `/practice/[problemId]`, `/review`, `/review/[wrongAnswerId]` 생성 |
| 실행 브라우저 route guard | PASS, `http://127.0.0.1:8080/review`에서 미인증 상태가 로그인 안내로 전환됨 |

의존성 디렉터리가 없는 상태에서 첫 검증은 실행 파일 부재로 실패했고, 첫 `npm ci`는 공유 작업 트리의 병렬 설치와 겹쳐 다수의 npm tar `ENOENT`가 발생했다. 병렬 설치가 끝난 뒤 단독으로 `npm ci`를 다시 실행해 460개 패키지를 설치했고 위 최종 검증을 완료했다.

운영 콘텐츠를 적재하지 않는 현재 정책과 브라우저의 미인증 세션 때문에 실제 Backend 데이터가 표시된 인증 후 화면은 브라우저에서 검증하지 못했다. 대신 화면 상호작용은 계약 fixture와 API mock으로 검증했으며, 실제 콘텐츠가 연결된 환경에서 데스크톱·모바일 렌더링과 전체 사용자 흐름을 후속 통합 검증해야 한다.
