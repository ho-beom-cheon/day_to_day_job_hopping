# Notion · Slack · OpenRouter 브라우저 연결 검증

대표 화면: [알림 및 외부 연동](http://localhost:8080/settings/integrations) · [AI 도우미](http://localhost:8080/assistant)

관련 이슈: [#8](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8). 확인일: 2026-09-24 08:20 KST.

## 결과와 경계

| 범위 | 상태 | 확인 내용 |
|---|---|---|
| Notion | 내부 구현 반영 | 설정 준비 상태, 기존 OAuth/해제, 공유 데이터 소스 검색·페이지 이동·6종 대상 선택 |
| Slack | 실제 연결·수신 검증 | `새 워크스페이스`의 `#일반` 승인, 사용자별 암호화 Webhook, 테스트 전송 `SUCCESS`와 채널 수신 확인 |
| OpenRouter | 내부 검증 통과 | PKCE S256, 사용자별 암호화 키, 일간 피드백 접수·서버 기록 snapshot·키 참조 고정 |
| 운영자 준비 | 문서·스크립트 완료 | 등록 화면·권한·콜백·환경변수·사용자 확인 절차, 로컬 암호화 키 준비 |
| 실제 공급자 | Slack 검증 완료 | Slack 앱 등록·HTTPS 콜백·사용자 승인·채널 수신 완료. Notion 동기화와 OpenRouter 실행은 미검증 |

08:20 검증 당시 로컬 API는 Notion/Slack/OpenRouter 모두 `configured=false`, 두 신규 연결 모두 `connected=false`, AI `enabled=false`를 반환했다. 이후 Notion 최초 등록과 Slack 실제 연결 결과는 아래 추가 검증을 따른다. OpenRouter 모델은 미선택 상태다. 암호화 키 두 개는 `.env`의 빈 항목에만 생성했으며 값은 출력하지 않았다.

OpenRouter 어댑터는 일간 피드백만 지원한다. AI 문제 자동 생성·저장·채점·운영 과정 변경까지 완료한 것이 아니다. Slack/OpenRouter 로컬 연결 해제와 공급자 권한 폐기는 구분하며 후자는 관리 페이지에서 사용자가 처리한다.

## 자동 검증

| 명령 / 범위 | 결과 |
|---|---|
| `docker compose build backend` | 최종 Slack 정책 회귀 테스트 포함 컴파일·패키징 성공, 단위 테스트 63개 통과 |
| Docker JDK에서 `sh mvnw -B -ntp -Ppostgres-it verify` | PostgreSQL IT 62개 실행. OAuth 4개 포함 61개 통과, 기존 스키마 전체 목록 테스트 1개가 V14 테이블 기준선 누락으로 실패 |
| 기준선 수정 후 `sh mvnw -B -ntp -Ppostgres-it '-Dit.test=BusinessSchemaIT' verify` | 단위 62개 + 해당 스키마 IT 7개 모두 통과. V1 업그레이드·세션 보존·V14 재실행 포함. 수정 후 전체 IT를 다시 반복하지 않음 |
| `docker compose exec -T frontend npm test` | 32개 파일, 133개 테스트 통과. 신규 4개는 준비 전 비활성, 인증 시작 실패, revision 기반 해제·원격 폐기 안내, 계약 오류 검증 |
| 별도 frontend 컨테이너 `npm run typecheck` | 최종 카드 변경 포함 통과 |
| `docker compose exec -T frontend npm run lint` | 최종 변경 포함 통과 |
| `docker compose run --rm --no-deps -e NODE_ENV=production frontend npm run build` | 성공. 이후 요약 카드 추가/순서 변경은 최종 typecheck·lint와 실제 브라우저로 확인 |
| `docker compose exec -T frontend node scripts/smoke.mjs` | Frontend→Backend/DB, Nginx→Frontend/API, 관리 포트 비공개 통과 |
| 키 준비 스크립트 | 테스트 env에서 기존 값 보존·빈 값 생성·재실행 무변경 확인 후 실제 `.env` 빈 키만 준비 |

로컬 로그는 Git 제외 경로 `.tools/browser-oauth-it.log`, `.tools/browser-schema-check.log`에 있다. 첫 전체 실행에서는 migration 개수·AI 테스트 API ID도 수정했으며, 이후 OAuth/AI 접수 IT는 통과했다. 이 로그의 fake provider 성공은 외부 공급자 성공의 증거가 아니다.

OAuth 테스트는 잘못된 사용자·세션, state 재사용·만료·취소, PKCE challenge/verifier 대응, 해제 중 token exchange 결과 무효화, 암호문 원문 미포함, Slack 설정·대기 취소, AI 사용자별 활성화·접수·snapshot을 검증한다. 공급자의 실제 응답 형식·모델 실행·Slack 수신·Notion 검색 결과는 실제 자격정보 준비 후 확인해야 한다.

## 브라우저와 실행 정보

- Chrome에서 `/settings/integrations`와 `/assistant`를 실제 열어 확인했다.
- 1920px 데스크톱에서 연동 3개와 앱 알림 요약, 연결 미설정 안내 표시 확인.
- 390px 모바일: `innerWidth=390`, 문서 폭 `375`, 가로 넘침 없음. AI 도우미 이동과 연결 화면 복귀 링크 확인.
- 확인한 브라우저 콘솔 error/warn 없음. 기존 다크 테마 유지.
- 잘못된 Slack 인증 콜백은 외부 호출 없이 `/settings/integrations?slack=failed`로 돌아간다.
- 현재 Compose의 backend/frontend/postgres/nginx 모두 healthy, 포트 `127.0.0.1:8080`.
- 개발 인증 우회 모드 `DEV_AUTH_BYPASS=true`를 유지했다. 이 모드에서는 익명 401 검증을 했다고 주장하지 않는다. 운영 전 실제 로그인 및 secure cookie 검증이 필요하다.
- 중지 후 시작: 프로젝트 루트에서 `.\scripts\ide.ps1 -Action Up`. Docker Desktop과 기존 `.env`/DB 필요.
- Tailscale의 비공개 HTTPS 주소를 구성해 실제 연동 화면과 Slack OAuth 콜백을 검증했다. 같은 Tailscale 네트워크에서만 접근한다.

## 운영자 다음 단계

[최초 앱 등록 가이드](integration-operator-setup.md)를 순서대로 수행한다. 세 앱의 설정을 반영한 뒤 실제 사용자 승인, Notion 기록 생성, Slack 테스트 수신, OpenRouter 한국어 피드백까지 확인해야 공급자별 완료로 변경할 수 있다.

추가 API 계약: [브라우저 연동 API](browser-integration-api.md).

## 08:45 추가 검증: Notion 최초 앱 등록

- 사용자가 확인한 설치 범위(현재 워크스페이스 1개)로 `Daily Leap` OAuth 연결을 실제 생성했다.
- 로컬 콜백 `/api/v1/integrations/notion/callback`을 등록하고 콘텐츠 읽기·업데이트·삽입 권한을 확인했다. 사용자 정보 접근은 없음으로 설정했다.
- Client ID와 Client Secret은 Git에서 제외된 로컬 `.env`에만 저장했다. 기존 암호화 키를 유지하고 백엔드를 재생성했다.
- 08:44 KST `GET /api/v1/integrations/notion/setup`에서 `configured=true`를 확인했다. Chrome의 [연동 화면](http://localhost:8080/settings/integrations)에서 `Notion 연결` 버튼이 활성화된 것을 확인했다.
- Compose 서비스 4개는 healthy이며 로컬 포트 8080, 개발 인증 우회 설정은 유지했다.
- 사용자 OAuth 승인, 공유할 페이지 선택, 대상 데이터베이스 검증 및 실제 동기화는 아직 수행하지 않았다. 최초 앱 등록 완료와 실제 사용자 연결 완료는 구분한다.

## 08:52 추가 검증: Slack 최초 앱 등록

- 사용자가 승인한 작업 공간에 `Daily Leap` 빈 앱을 실제 생성하고 Incoming Webhooks를 활성화했다.
- OAuth 봇 범위는 `incoming-webhook` 1개이며 사용자 토큰 범위는 없는 것을 공급자 설정 화면에서 확인했다.
- Client ID와 Client Secret은 Git 제외 로컬 `.env`에 저장하고 백엔드를 재생성했다. 기존 Notion 설정과 암호화 키는 유지했다.
- 프로젝트에 접속 가능한 HTTPS 주소가 확정되지 않아 공급자 Redirect URLs와 `SLACK_REDIRECT_URI`는 비워 두었다. Slack 앱 관리 사이트 주소는 프로젝트 콜백으로 사용할 수 없다.
- 08:52 KST 로컬 상태 API는 `configured=false`, `connected=false`를 반환했다. Chrome [연동 화면](http://localhost:8080/settings/integrations)에서 Slack 버튼 비활성, Notion 버튼 활성 상태를 확인했다.
- Compose 서비스 4개는 healthy이며 포트 8080에서 실행 중이다. HTTPS 설정, 사용자 채널 선택·승인과 실제 Slack 메시지 수신은 아직 수행하지 않았다.

## 후속 검증: 기존 Tailscale HTTPS 주소 연결

- Tailscale 상태가 Running/Online이며 기존 Serve가 HTTPS 443에서 `127.0.0.1:8080`으로 프록시하는 것을 확인했다. 공개 Funnel 설정은 없고 기존 네트워크 범위를 확대하지 않았다.
- 실제 HTTPS `/settings/integrations`가 200으로 열리고 Daily Leap 화면이 표시된다. 세션 쿠키의 Secure·HttpOnly 속성을 값 출력 없이 확인했다.
- Nginx의 명시적 HTTPS 호스트 스킴 처리와 Next.js의 해당 개발 호스트 허용을 추가했다. 앱 origin과 세 공급자 콜백, secure cookie 설정을 `.env`에 반영했다.
- Notion과 Slack 개발자 화면에 실제 HTTPS 콜백을 저장했다. Slack 상태 API의 `configured=true`, `connected=false`와 활성화된 연결 버튼을 확인했다.
- HTTPS 앱에서 Slack 연결을 눌러 실제 공급자의 채널 선택·허용 화면까지 도달했다. 채널 권한 승인 및 테스트 메시지 발송은 사용자 확인 단계다.
- `docker compose up -d --build --wait --wait-timeout 180 backend frontend nginx` 성공, 서비스 4개 healthy. 백엔드 빌드의 단위 테스트 62개 통과.
- Next.js 설정 lint, 별도 컨테이너 typecheck, Nginx 구성 검사, 기존 프록시/API/DB smoke 모두 통과했다.
- 개발 인증 우회는 유지했다. Google의 새 HTTPS 콜백 로그인, Notion 실제 동기화, OpenRouter 모델 실행은 이번 HTTPS 준비 검증에 포함하지 않았다.

접속 조건과 복구 절차: [로컬 HTTPS 테스트 안내](local-https-testing.md).

## 09:12 추가 검증: Slack 실제 연결과 채널 수신

- HTTPS 앱에서 `Daily Leap`을 `새 워크스페이스`의 `#일반` 채널에 승인했다. 상태 API와 화면에서 `configured=true`, `connected=true`, 대상 `#일반`을 확인했다.
- 첫 테스트 전송은 네트워크 호출 전에 `SLACK_PROVIDER_REJECTED`로 실패했다. 사용자 OAuth Webhook이 있어도 서버 공용 Webhook 설정을 요구하던 `SlackEndpointPolicy` 조건이 원인이었다.
- 사용자별 자격정보를 선택한 뒤에도 HTTPS, 허용 호스트, 443 포트, `/services/` 경로와 공인 DNS 검사를 유지하도록 조건을 수정하고 회귀 테스트를 추가했다.
- 수정 후 백엔드 빌드에서 단위 테스트 63개가 모두 통과했고 백엔드와 Nginx를 재시작했다.
- 두 번째 테스트 전송 ID `2`는 2026-09-24 09:12 KST에 `SUCCESS`로 완료됐다. Slack `#일반`에서 `[Daily Leap] Slack 연결 테스트` 메시지와 고정 본문을 실제로 확인했다.
- 반복 알림은 아직 `발송 안 함`이다. 실제 연결과 테스트 전송은 완료됐고, 예약 알림 발송은 연동 화면의 `Slack 알림 켜기`를 사용자가 선택할 때 활성화된다.
