# 개인용 인터넷 접속

이 PC에서는 Tailscale Funnel이 `https://hocheon.tail4ddad6.ts.net/`을 `http://127.0.0.1:8080`으로 전달한다. 접속하는 휴대폰·다른 PC에는 Tailscale 앱이 필요 없다. 호스트 PC, Tailscale, Docker Desktop이 켜져 있어야 접속할 수 있다. Funnel은 앱 주소를 인터넷에 공개하므로 Google 로그인을 유지해야 한다.

대표 화면: [AI 도우미](https://hocheon.tail4ddad6.ts.net/assistant). 로그인 전에는 [로그인 화면](https://hocheon.tail4ddad6.ts.net/login)에서 Google로 계속하기를 누른다. 서버의 `GOOGLE_ALLOWED_EMAILS`에 등록된 Google 계정만 로그인할 수 있다. 계정과 API 키 값은 Git에 포함하지 않은 `.env`에서 관리한다.

## 설정과 시작

프로젝트 루트의 `.env`에 `APP_ORIGIN=https://hocheon.tail4ddad6.ts.net`, `APP_HTTPS_HOST=hocheon.tail4ddad6.ts.net`, `HTTP_BIND_ADDRESS=127.0.0.1`, `SESSION_COOKIE_SECURE=true`, `DEV_AUTH_BYPASS=false`를 설정한다. Google Cloud OAuth 웹 클라이언트에는 `https://hocheon.tail4ddad6.ts.net/login/oauth2/code/google`을 승인된 리디렉션 URI로 등록한다. 프런트엔드는 `compose.public.yaml`의 production runtime 이미지를 사용하며, 기존 개발 DB와 `APP_PROFILES=dev,dev-seed`는 유지한다. 기존 개발 DB의 Flyway seed 이력 때문에 이 DB에서 `dev-seed`를 임의로 제거하지 않는다.

```powershell
docker compose -f compose.yaml -f compose.public.yaml up --build -d --wait
tailscale funnel --bg --https=443 http://127.0.0.1:8080
tailscale funnel status
```

`scripts/ide.ps1 -Action Up`은 기본 `Auto` 모드에서 `APP_ORIGIN`이 HTTPS이거나 `APP_HTTPS_HOST`가 있으면 `compose.public.yaml`을 적용한다. 공개 실행을 명시하려면 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public`을 사용한다. 이 모드는 HTTPS origin/host 일치 여부를 확인하고, 인증 우회 비활성·secure cookie·production 프런트엔드·소스 mount 제거를 적용한다. 기동 후 Nginx upstream을 갱신하고 연결 smoke를 실행한다. 개발 서버가 필요하면 `-Mode Development`를 명시한다. 이 스크립트는 Funnel을 새로 공개하거나 DNS·계정을 변경하지 않는다.

Tailscale Funnel을 처음 활성화할 때 관리 화면에서 허용을 요청할 수 있다. 공개 DNS 전파에는 최대 약 10분이 걸릴 수 있다.

공개를 중단할 때는 `tailscale funnel --https=443 off`를 실행한다. 앱 컨테이너까지 중지하려면 `docker compose -f compose.yaml -f compose.public.yaml stop`을 실행한다. 다시 시작할 때는 위 시작 명령을 재실행한다.

공개 DB의 수동 백업·격리 복원 절차는 [운영형 로컬 실행·백업·복원](production-local-runbook.md)을 따른다.

## 2026-09-24 확인 결과

- 배포용 프런트엔드 빌드에서 타입 검사, lint, 133개 테스트, Next.js 빌드가 통과했다. `public` 이미지와 push worker 응답도 200이었다.
- 개발 인증 우회를 끈 상태에서 비로그인 `/api/v1/users/me`와 `/api/v1/ai/usage`는 401이었다.
- HTTPS 앱에서 허용된 Google 계정으로 로그인한 뒤 `/assistant`에 기존 AI 사용량과 토큰 기록이 표시됐다.
- Funnel 상태가 `AllowFunnel=true`였고, 공개 DNS에서 나온 중계 IP `103.84.155.153`으로 직접 접속해 `/login` 200과 비로그인 `/api/v1/users/me` 401을 확인했다. 이는 호스트의 Tailscale 내부 DNS 경로와 별도로 확인한 결과다.

Tailscale의 [Funnel 설명](https://tailscale.com/docs/features/tailscale-funnel)에 따르면 Funnel은 일반 인터넷 접속을 제공하며 공개 DNS 전파와 대역폭 제한이 있다.
