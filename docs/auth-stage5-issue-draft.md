# 기능: Google OIDC 허용 계정 로그인과 내 정보·CSRF·로그아웃 구현

2026-09-20 [이슈 #4](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/4)로 등록했다.

## 배경

26단계 계획의 5번이다. 실제 OpenAPI v1.2.1의 OIDC-001/002, AUTH-004/002, USER-001/002 계약을 기존 Spring Session JDBC 및 공통 API 기반에 연결한다.

## 범위

- Google OIDC state/nonce/PKCE, 10분·일회성 요청, 검증된 허용 이메일과 issuer+subject 고정, 세션 ID 교체, 고정 성공/실패 경로.
- 익명 CSRF 조회, 로그인 후 토큰 교체, 세션과 쿠키를 종료하는 CSRF 보호 로그아웃.
- 내 정보 GET, 닉네임만 PATCH, ETag/If-Match 및 무변경 요청 보존.
- V4 프로필 URL 저장 보완. 기존 V1–V3 및 사용자 데이터 보존.
- Google 환경변수/허용 이메일과 Nginx 로그인·콜백 라우팅. secret/code/state/token 비노출.

## 완료 기준 및 검증

- 실제 PostgreSQL에서 최초 연결/재로그인/미허용·미검증·정지 계정 거절과 동일 이메일의 다른 subject 연결 거절.
- 서명된 모의 OIDC code 교환부터 세션·CSRF·내 정보·로그아웃까지 HTTP 통합 검증.
- 실패·만료·재사용 state, nonce/서명/issuer/audience 오류 거절, nickname 검증/동시성/무변경 테스트.
- 빌드·기존 테스트·Compose smoke 및 기존 DB 업그레이드 확인.
- 로컬 .env 설정 후 Google 실계정으로 로그인 검증. 자격증명과 개인정보는 추적 파일·로그에 기록하지 않는다.

## 범위 제외

공개 회원가입, 비밀번호 로그인, 자체 JWT, Google 전체 로그아웃, 학습 API 및 이후 화면 작업.
