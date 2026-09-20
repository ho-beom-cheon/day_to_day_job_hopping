# 작업 9 이슈 초안 — Next.js 공통 기반

현재 환경에는 GitHub CLI가 없어 원격 이슈 존재 여부를 조회하거나 등록하지 못했다. PR을 만들기 전 저장소에서 기존 이슈를 확인하고, 없다면 아래 내용으로 등록한다.

## 목표

기존 API client, TanStack Query, 상태 UI를 OpenAPI 계약에 맞게 확장하고 이후 화면이 공통으로 사용할 인증 상태·ViewModel·반응형 앱 셸을 제공한다.

## 완료 조건

- 성공/오류 Envelope, 현재 사용자, CSRF 응답을 런타임에서 검증한다.
- DTO → mapper → ViewModel → Component 경계를 테스트로 확인한다.
- 새로고침 시 CSRF와 현재 사용자를 복원하고 loading/401/error를 구분한다.
- 공개 시작 화면과 인증 학습 공간 route를 분리한다.
- 1440px desktop과 375px mobile에서 탐색·콘텐츠·터치 영역·가로 넘침을 확인한다.
- typecheck, lint, unit test, production build를 통과한다.

## 제외

- 로그인·온보딩의 전체 기능, 실제 학습 데이터, 개별 기능 화면, 전체 디자인 보완
