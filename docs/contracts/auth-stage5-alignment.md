# 작업 5 인증·회원 채택 기준

관련 [이슈 #4](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/4). OpenAPI v1.2.1 및 DB HTML v0.1 원본을 유지한다.

- Google만 허용한다. 공급자 주소는 코드에 고정하고 외부 요청으로 바꾸지 않는다. 클라이언트 자격증명과 허용 이메일이 없으면 로그인은 고정 실패 경로로 닫힌다.
- 이메일은 최초 허용 여부를 확인하는 값이며 로그인 키는 issuer+subject다. 기존 이메일을 다른 subject에 자동 연결하지 않는다. 기존 연결의 이메일 변경도 자동 병합하지 않고 거부한다. 로그인 때마다 verified email/허용 목록/ACTIVE 상태를 확인한다.
- 최초 닉네임은 검증된 Google 이름을 공백 제거 후 30 Unicode code point까지 사용하며 비어 있으면 `학습자`다. 이후 로그인은 사용자 닉네임을 덮어쓰지 않는다. 기존 DB의 넓은 nullable email/80자 표시명은 보존하고 API 접근 가능한 OIDC 사용자에 필수 email/1..30자 불변식을 적용한다.
- V4는 nullable HTTPS 프로필 URL과 로그인 state 소비 기록을 추가한다. state 원문 대신 SHA-256/10분 만료만 기록하고 DB의 원자적 DELETE로 동시 콜백도 한 번만 소비한다. nonce/PKCE 자료는 익명 JDBC 세션에 저장하고 소비 후 제거한다. Google 토큰은 로그인 처리 후 세션/업무 DB에 보존하지 않는다.
- 성공은 `/`, 실패는 `/login?error=LOGIN_FAILED` 또는 `ACCOUNT_NOT_ALLOWED`다. 임의 returnUrl은 받지 않는다. 로그인 세션 ID/CSRF를 교체한다.
- 운영 쿠키는 `__Host-CAREER_SESSION`, Secure/HttpOnly/SameSite=Lax/Path=/, Domain 없음이다. 기존 로컬 HTTP 개발 모드(`SESSION_COOKIE_SECURE=false`)만 `CAREER_SESSION`을 사용한다. HTTPS에서는 반드시 Secure 모드로 전환한다.
- CSRF는 익명 GET으로 발급한다. 쓰기는 X-CSRF-TOKEN만 받는다. 로그아웃 재호출은 세션 없음 401로 처리하고 세션·동일 쿠키를 제거한다.
- 내 정보는 현재 사용자만 조회한다. curriculumId는 사용자의 미종료 user_curriculum ID다. 닉네임 PATCH는 잠금 안에서 If-Match를 검사하고 실제 변경에만 revision/updatedAt을 증가시킨다.
- Nginx는 로그인 시작/콜백을 Backend로 전달한다. 인증 경로 access log는 query/referrer를 기록하지 않는 전용 형식을 사용한다.

실제 PostgreSQL·서명된 모의 공급자 HTTP 흐름과 실제 Google 브라우저 검증은 별도 결과로 기록한다. 모의 공급자 주소와 로그인 우회 endpoint는 production 코드/설정에 추가하지 않는다.

참고: [Spring Security OAuth2 Login 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [CSRF 로그인·로그아웃](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
