# 작업 9 Next.js 공통 기반 완료

2026-09-21 / 원격 이슈 연결 대기

**후속 화면이 API와 인증 상태를 각자 다시 해석하지 않고 같은 구조 위에 구현될 수 있는 Frontend 공통 기반을 완성했다.** 채택 범위는 [계약 대조](contracts/frontend-stage9-alignment.md)에 기록했다.

## 사용할 수 있는 기능

- `/workspace` 진입 시 익명 CSRF를 먼저 발급하고 현재 사용자를 복원한다.
- 미로그인은 로그인 필요 화면, 통신 오류는 재조회 가능한 오류 화면, 정상 인증은 반응형 학습 공간으로 구분한다.
- OpenAPI Envelope와 사용자/CSRF DTO를 런타임에서 검증하고 64-bit ID를 문자열로 유지한다.
- 사용자 DTO는 표시 전용 ViewModel로 변환하며 Component가 API 필드에 직접 결합하지 않는다.
- 데스크톱은 좌측 탐색, 모바일은 safe-area를 고려한 하단 탐색을 사용한다. 아직 없는 기능 route는 준비 중으로 비활성화한다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `docker build --target build -t daily-career-frontend-stage9-check frontend` | PASS |
| TypeScript | PASS, Next route type 생성 및 `tsc --noEmit` |
| ESLint | PASS, warning 0 |
| Vitest | PASS, 6 files / **34 tests** |
| Next production build | PASS, `/`와 `/workspace` 정적 route 생성 |
| 실제 미로그인 | `/api/v1/auth/csrf` 200 후 `/api/v1/users/me` 401, 로그인 필요 화면으로 전환 |
| 인증 상태 렌더링 | 로컬 mock 계약 응답으로 사용자 ViewModel·앱 셸·준비 중 메뉴 확인 |
| 반응형 | Chromium 1440×900, 375×812 확인. 모바일 `scrollWidth=clientWidth=375` |
| 개발환경 | Frontend/Nginx 재빌드·재기동, Backend 일반 테스트 38개 통과, 서비스 healthy |

Chromium 콘솔의 401은 미로그인 상태 확인을 위한 예상 응답이다. 기능 오류는 없고 favicon 404만 남아 있으며 아이콘 자산은 작업 20 디자인 보완 범위다.

원격 이슈는 현재 PC에 GitHub CLI가 없어 확인하지 못했다. [이슈 초안](frontend-stage9-issue-draft.md)을 남겼고 커밋·푸시·PR은 수행하지 않았다. 다음은 **작업 10 로그인·온보딩 UI**다.
