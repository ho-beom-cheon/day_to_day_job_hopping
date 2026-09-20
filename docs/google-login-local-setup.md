# Google 로그인 로컬 설정

개발 주소는 **http://localhost:8080**이다. Google 인증과 쿠키 검증 중에는 127.0.0.1 주소와 섞어 쓰지 않는다.

2026-09-20 현재 이 PC는 설정과 실계정 검증을 완료했다. 프로젝트는 `Daily Career` (`daily-career-509214`), 웹 클라이언트는 `Daily Career Local`, 앱 이름은 `데일리 이직`이며 외부/테스트 상태다. 승인 계정 1개가 테스트 사용자와 서버 허용 계정에 등록되어 있다. 인증값은 추적 제외 `.env`에 저장했다. 아래 절차는 새 PC 또는 설정 변경 시 사용하며, 기존 프로젝트에서는 이미 만든 클라이언트를 확인한다. [검증 결과](auth-stage5-verification-20260920.md).

1. [Google Cloud Console](https://console.cloud.google.com/auth/clients) 상단 프로젝트 선택에서 이 앱용 프로젝트를 선택한다. 별도 프로젝트를 만들 경우 이름 예시는 `Daily Career`다. **Google 인증 플랫폼 → 시작하기**에서 앱 이름 `데일리 이직`, 지원·연락 이메일을 입력한다. 개인 Gmail은 대상 **외부**를 선택한다. 테스트 상태라면 **대상 → 테스트 사용자**에 로그인할 계정을 추가한다.
2. **클라이언트 → 클라이언트 만들기 → 웹 애플리케이션**을 선택하고 이름을 `Daily Career Local`로 입력한다. **승인된 리디렉션 URI**에 `http://localhost:8080/login/oauth2/code/google`를 정확히 등록한다. 현재 서버 redirect 방식에는 승인된 JavaScript 원본이 필요 없다. 앱은 openid/email/profile 범위만 요청한다. 로컬 테스트를 위해 앱을 운영 공개할 필요는 없다.
3. 저장소 루트의 **추적 제외된 `.env`**에 아래 키를 설정한다. 실제 값을 문서·채팅·GitHub·커밋에 복사하지 않는다.

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_ALLOWED_EMAILS=
APP_ORIGIN=http://localhost:8080
SESSION_COOKIE_SECURE=false
```

`GOOGLE_ALLOWED_EMAILS`는 허용할 Google 계정의 전체 이메일이며 여러 개는 쉼표로 구분한다. 전부 비어 있으면 로그인은 비활성화된다. 일부만 입력하면 서버는 잘못된 설정으로 시작을 거부한다.

4. 설정 후 Backend를 다시 생성하고 Nginx 설정을 반영한다.

```powershell
docker compose up -d --build --wait backend
docker compose up -d --force-recreate --wait nginx
```

5. 브라우저에서 `http://localhost:8080/oauth2/authorization/google`로 이동해 로그인한다. 성공하면 `/`로 돌아온다. 이 단계의 홈 화면은 기존 구현 그대로이며 로그인 UI 화면은 후속 작업이다. `http://localhost:8080/api/v1/users/me`에서 로그인 여부를 확인할 수 있다.

6. CSRF 조회→닉네임 수정→로그아웃→내 정보 401 순서로 확인한다. 닉네임 수정은 JSON nickname만, 직전 ETag의 If-Match와 새 X-CSRF-TOKEN이 필요하다. 로그아웃 POST는 본문을 보내지 않는다. 로그인·로그아웃 후에는 토큰을 재조회한다.

운영 HTTPS에서는 APP_ORIGIN을 실제 origin으로 바꾸고 해당 Google 리디렉션 URI를 별도로 등록한다. `SESSION_COOKIE_SECURE=true`일 때 쿠키는 `__Host-CAREER_SESSION`, 로컬 HTTP에서는 `CAREER_SESSION`이다. 두 경우 모두 HttpOnly/SameSite=Lax/Path=/, Domain 미설정이다.

`LOGIN_FAILED`는 state/공급자/설정 등 인증 실패, `ACCOUNT_NOT_ALLOWED`는 미허용·이메일 미검증·기존 연결 충돌·정지 계정이다. 공급자 원문 오류나 토큰을 화면 URL에 붙이지 않는다. 실패 화면 자체는 후속 화면 작업에 포함된다.

참고: [Google OAuth 클라이언트 설정](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid), [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect).
