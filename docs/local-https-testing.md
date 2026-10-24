# Tailscale HTTPS로 외부 연동 테스트

관련 이슈: [#8 외부 연동 완성](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8)

이 문서는 2026-09-24에 사용한 Tailscale Serve 기반의 비공개 HTTPS 테스트 기록이다. 현재는 개인용 외부 접속을 위해 같은 주소의 443 포트를 Funnel로 전환했다. 현행 실행·로그인·접속 방법은 [개인용 인터넷 접속](public-internet-access.md)을 따른다.

## 주소 확인과 실행

프로젝트 루트의 `.env`에서 `APP_ORIGIN`을 확인하고, 그 주소 뒤에 `/settings/integrations`를 붙여 브라우저에서 연다. OAuth 연결 시작과 돌아올 주소는 같은 HTTPS 호스트여야 한다.

```powershell
tailscale serve status
.\scripts\ide.ps1 -Action Up
```

당시 Serve는 HTTPS 443에서 이 PC의 `http://127.0.0.1:8080`을 프록시했다. 아래 Serve 복구 명령을 현재 실행하면 443 포트의 공개 Funnel이 비공개 Serve로 바뀐다. 공개 접속을 계속 사용하려면 실행하지 않는다.

```powershell
tailscale serve --bg --https=443 http://127.0.0.1:8080
```

## 적용한 설정

- `APP_ORIGIN`: 실제 Tailscale HTTPS origin.
- `APP_HTTPS_HOST`: 위 주소의 호스트명만 입력. 스킴·포트·와일드카드는 넣지 않는다.
- `HTTP_BIND_ADDRESS=127.0.0.1`: 로컬 프록시만 원본 HTTP 포트에 접근한다.
- `SESSION_COOKIE_SECURE=true`: HTTPS에서 사용할 세션 쿠키.
- Notion·Slack·OpenRouter의 `*_REDIRECT_URI`: 같은 HTTPS origin과 각 서비스의 콜백 경로.
- Nginx는 명시된 HTTPS 호스트에만 HTTPS 스킴을 전달한다. 요청자가 보낸 `X-Forwarded-Proto`를 그대로 신뢰하지 않는다.
- Next.js 개발 서버에는 해당 호스트 하나만 추가 허용한다.

Notion 개발자 설정에는 HTTPS 콜백을 추가했고, Slack OAuth 설정에도 같은 HTTPS origin의 Slack 콜백을 저장했다. OpenRouter는 앱 등록 없이 설정한 콜백을 인증 시작 시 사용한다. 모델은 별도 선택이 필요하다.

## 로그인과 실제 연동 상태

당시에는 `DEV_AUTH_BYPASS=true` 개발 테스트 계정을 유지했다. 현재 공개 접속 설정은 `DEV_AUTH_BYPASS=false`이며 실제 Google 로그인을 사용한다.

이번 비공개 HTTPS 테스트에서는 Google 재로그인을 검증하지 않았다. 이후 공개 접속 단계에서 HTTPS 콜백을 등록하고 실제 Google 재로그인을 검증했다.

Slack은 앱의 연결 버튼으로 워크스페이스·채널을 승인해야 사용자 연결이 생긴다. Notion도 페이지 접근 승인과 저장 대상 검증이 필요하다. HTTPS 접속 성공만으로 공급자 전송·동기화 완료라고 판단하지 않는다.

설정 변경 후 반영:

```powershell
docker compose up -d --build --wait backend frontend nginx
```

검증 기록: [브라우저 연동 검증](browser-integrations-verification-20260924.md).

근거: [Tailscale Serve 공식 설명](https://tailscale.com/docs/features/tailscale-serve), [Serve 명령](https://tailscale.com/docs/reference/tailscale-cli/serve).
