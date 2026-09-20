# 작업 9 Next.js 공통 기반 채택 기준

2026-09-21 기준으로 OpenAPI v1.2.1, UI/UX v0.3, 기존 Frontend 기반을 대조했다. 작업 9는 개별 학습 기능을 미리 구현하지 않고 작업 10 이후가 같은 경계와 상태 표현을 재사용할 수 있게 만드는 범위다.

## 채택한 구조

| 관심사 | 채택 기준 | 구현 |
|---|---|---|
| API 응답 | OpenAPI의 `success/data/error/meta`를 transport 밖에서 검증 | `api/contract.ts`의 성공·오류 envelope decoder |
| ID | 64-bit ID를 JSON string으로 유지 | 기존 `readId`를 DTO decoder에서 사용 |
| 인증 복원 | CSRF 조회 후 내 정보 조회, CSRF는 메모리에만 유지 | `currentSessionQuery`, 공용 API client singleton |
| 표현 경계 | DTO를 Component에 직접 전달하지 않음 | `CurrentUserDto → toSessionViewModel → AppShell` |
| 상태 | loading, 401, 일반 오류를 명시적으로 구분 | `SessionGate`와 기존 `StatePanel` 재사용 |
| 라우팅 | 공개 시작 화면과 인증 학습 공간을 분리 | `/`와 `/workspace`; 후속 메뉴는 준비 중 비활성 상태 |
| 레이아웃 | 큰 화면 좌측 탐색, 좁은 화면 하단 탐색 | `AppShell`, 44px 이상 터치 높이와 safe-area 여백 |

## 이번 단계에서 하지 않은 것

- Google 로그인 성공·거부·만료의 완성형 UX와 과정 배정은 작업 10이다.
- 실제 오늘 학습·지표·일정 데이터 연결은 작업 11 이후다.
- 준비 중 메뉴는 존재하지 않는 route로 이동시키지 않는다.
- 시안 10종의 전면 구현과 캐릭터 적용은 작업 20에서 최종 대조한다.

실제 로그인 API는 기존 작업 5의 `AUTH-004`, `USER-001` 계약을 그대로 사용한다. 공개 OpenAPI와 Backend 계약을 변경하지 않았다.
