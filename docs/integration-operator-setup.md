# 운영자 최초 등록: Notion · Slack · AI(OpenRouter)

관련 이슈: [#8 외부 연동 완성](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8)

이 문서는 **운영자가 서비스마다 최초 한 번 하는 설정**이다. 설정 후 일반 사용자는 앱의 ‘연결’ 버튼을 눌러 공급자 사이트에서 로그인하고 승인한다. 일반 사용자가 `.env`, API 키, Webhook 주소를 직접 입력하지 않는다.

현재 확인 화면: [알림 및 외부 연동](http://localhost:8080/settings/integrations). 로컬 주소이며 외부 공개 주소가 아니다. 실제 공급자의 승인·전송 성공 여부는 별도 [검증 기록](browser-integrations-verification-20260924.md)을 따른다.

09/24 후속 설정으로 이 PC의 기존 Tailscale HTTPS 주소도 연결했다. 현재 OAuth 연결 테스트는 `.env`의 `APP_ORIGIN` 뒤에 `/settings/integrations`를 붙인 HTTPS 화면에서 시작한다. 접속 조건과 재실행 방법은 [로컬 HTTPS 테스트 안내](local-https-testing.md)를 따른다.

## 먼저 할 일

| 순서 | 운영자가 할 일 | 준비되는 값 |
|---|---|---|
| 1 | 실제 서비스 접속 주소 확정. Slack까지 테스트하려면 HTTPS 필요 | 앱 주소, 세 서비스의 콜백 주소 |
| 2 | 아래 키 준비 스크립트 실행 | 서버에 저장할 토큰 암호화 키 두 개 |
| 3 | Notion public connection 생성 | `NOTION_CLIENT_ID`, `NOTION_CLIENT_SECRET` |
| 4 | Slack 앱 생성, Incoming Webhooks와 OAuth 설정 | `SLACK_CLIENT_ID`, `SLACK_CLIENT_SECRET` |
| 5 | OpenRouter 사용 모델 선택 | `OPENROUTER_MODEL` — OAuth 앱 등록은 없음 |
| 6 | `.env`에 입력하고 서버 재생성 | 연결 버튼 활성화 |
| 7 | 앱에서 각 연결 버튼으로 승인하고 실제 결과 확인 | 사용자별 연결 |

비밀값은 프로젝트 루트의 `.env` 또는 배포 환경의 비밀 저장소에만 입력한다. 채팅, 이슈, PR, 화면 캡처에 복사하지 않는다. `.env.example`에는 실제 값을 넣지 않는다.

## 1. 접속 주소와 콜백 정하기

브라우저에서 로그인한 주소와 콜백 주소의 **스킴·호스트·포트가 같아야 세션을 유지**한다. `localhost`에서 연결을 시작한 뒤 다른 HTTPS 도메인으로 돌아오면 같은 로그인 세션이 아니다.

| 서비스 | 현재 로컬 개발용 콜백 | 운영용 콜백 경로 |
|---|---|---|
| Notion | `http://localhost:8080/api/v1/integrations/notion/callback` | `/api/v1/integrations/notion/callback` |
| Slack | HTTP 로컬 주소로는 사용 불가 | `/api/v1/integrations/browser/slack/callback` |
| OpenRouter | `http://localhost:8080/api/v1/integrations/browser/openrouter/callback` | `/api/v1/integrations/browser/openrouter/callback` |

운영용 경로 앞에 **실제로 확보한 HTTPS 앱 주소**를 붙인다. 이 문서에서 외부 주소를 생성하거나 DNS·인증서를 설정하지는 않는다. Slack은 Redirect URL에 HTTPS를 요구한다. [Slack OAuth 공식 문서](https://docs.slack.dev/authentication/installing-with-oauth/)

HTTPS를 준비할 때는 다음 순서로 확인한다.

1. 사용할 도메인과 TLS 종료 프록시를 구성한다. 이미 사용하는 HTTPS 주소가 있다면 그 주소를 사용한다.
2. 그 HTTPS 주소로 `/settings/integrations`가 열리고 로그인이 유지되는지 확인한다.
3. `APP_ORIGIN`을 같은 HTTPS 앱 주소로 설정한다. Google 로그인도 해당 주소의 `/login/oauth2/code/google`을 등록한다.
4. 운영에서는 `DEV_AUTH_BYPASS=false`, `SESSION_COOKIE_SECURE=true`로 설정한다.
5. TLS 종료 프록시의 전달 헤더 처리를 확인한다. 저장소 기본 Nginx는 내부 HTTP용 `$scheme`을 전달하므로, 외부 TLS 프록시를 앞에 둔다면 신뢰할 수 있는 프록시에서 원래 HTTPS 스킴이 백엔드까지 유지되도록 구성해야 한다. 임의 클라이언트가 전달한 헤더를 그대로 신뢰하지 않는다.
6. 확정된 주소로 **먼저 로그인한 뒤** 세 서비스의 연결을 시작한다.

로컬 HTTP에서는 Notion과 OpenRouter부터 확인할 수 있다. Slack 버튼은 HTTPS 설정이 끝날 때까지 비활성 상태가 정상이다.

## 2. 암호화 키 준비

프로젝트 루트의 PowerShell에서 실행한다.

```powershell
.\scripts\prepare-integration-keys.ps1
```

이 스크립트는 기존 `.env`에서 아래 항목이 비어 있을 때만 각각 32바이트 난수 키를 만들어 저장한다. 키 값은 터미널에 출력하지 않으며, 이미 입력된 키는 유지한다.

- `NOTION_CREDENTIAL_KEY`: 기존 Notion 토큰 암호화용.
- `INTEGRATION_CREDENTIAL_KEY`: Slack Webhook과 OpenRouter 키, PKCE verifier 암호화용.

`.env`가 없다면 먼저 `.env.example`을 `.env`로 복사하고 기존 실행 가이드에 따라 DB 설정을 입력한다. 기존 `.env` 위에 예제 파일을 덮어쓰지 않는다.

두 키는 DB 백업과 함께 복구할 수 있도록 별도 비밀 저장소에 보관한다. 재시작할 때 새로 만들거나 임의 변경하면 이미 연결된 계정의 자격정보를 읽을 수 없다. `NOTION_KEY_ID`는 최초 `notion-local-v1`을 유지해도 된다. 이 구현은 다중 키 자동 회전을 지원하지 않는다.

## 3. Notion 최초 앱 등록

### 3-1. OAuth 연결 만들기

1. [Notion 개발자 포털](https://www.notion.so/profile/integrations)에 로그인한다.
2. **Build → Public connections → Create new connection**을 선택한다. 메뉴 이름은 계정 UI에 따라 다를 수 있으나 OAuth client ID/secret이 발급되는 public connection이어야 한다.
3. 이름을 `Daily Leap` 등으로 입력하고 개발용 워크스페이스를 선택한다.
4. 설치 범위를 선택한다. 개인 테스트라면 **Selected workspaces only**에서 테스트 워크스페이스를 지정한다. 다른 사용자 워크스페이스도 지원하려면 **Any workspace**를 선택한다. 이 선택은 생성 후 변경할 수 없으므로 실제 제공 범위에 맞춰 고른다.
5. Redirect URI에 앞에서 정한 **전체 Notion 콜백 URL**을 등록한다. 로컬 테스트는 표의 localhost URL을 그대로 사용한다.
6. Capabilities에서 **Read content, Insert content, Update content**를 허용한다. 현재 기능에는 사용자 이메일·댓글 권한이 필요하지 않다.
7. 생성 후 **Configuration**에서 OAuth client ID와 OAuth client secret을 확인한다.

위 절차와 설치 범위는 [Notion public connections 공식 안내](https://developers.notion.com/guides/get-started/public-connections)를 기준으로 한다. Marketplace 공개 등록은 별도 절차이며, 이 앱의 개인 테스트 완료와 같지 않다.

### 3-2. 서버에 입력

`.env`에서 다음 항목을 편집한다. 꺾쇠 설명은 실제 값으로 바꾸며 문자 그대로 저장하지 않는다.

```dotenv
NOTION_CLIENT_ID=<발급받은 OAuth client ID>
NOTION_CLIENT_SECRET=<발급받은 OAuth client secret>
NOTION_REDIRECT_URI=http://localhost:8080/api/v1/integrations/notion/callback
NOTION_KEY_ID=notion-local-v1
```

`NOTION_CREDENTIAL_KEY`는 앞의 스크립트가 준비한다. public connection의 client secret 대신 개인 토큰을 넣으면 이 OAuth 흐름이 동작하지 않는다.

### 3-3. 기록을 받을 Notion 데이터베이스 준비

이 부분은 OAuth 앱 등록과 별개의 **기록 저장 대상 준비**다. 처음에는 데이터베이스 하나에 6종 기록을 모으는 방식이 가장 간단하다.

1. Notion에서 ‘Daily Leap 기록’ 페이지를 만든다.
2. 페이지 안에 실제 데이터베이스 표를 만든다. 단순 표 블록은 사용할 수 없다.
3. 다음 속성을 정확한 이름과 유형으로 만든다. 이름은 대소문자를 구분한다.

| 속성 이름 | Notion 속성 유형 | 용도 |
|---|---|---|
| `Name` | 제목 / Title | 기록 제목. 기존 제목 속성을 이름 변경 |
| `SourceKey` | 텍스트 / Text | 같은 원본의 중복 생성 방지 |
| `SourceVersion` | 숫자 / Number | 최신 기록 판별 |
| `EntityType` | 선택 / Select | 학습·문제·시험·오답·주간·월간 분류 |
| `Summary` | 텍스트 / Text | 기록 요약 |
| `OccurredAt` | 날짜 / Date | 기록 날짜 |

`EntityType` 선택지는 `LEARNING_RESULT`, `PROBLEM`, `TEST_RESULT`, `WRONG_ANSWER`, `WEEKLY_REPORT`, `MONTHLY_REPORT`로 만들 수 있다. 속성 검증과 사용하는 이름은 이 저장소의 구현 규칙이다.

### 3-4. 일반 사용자 흐름 확인

1. 서버 설정을 반영한 뒤 앱의 **Notion 연결**을 누른다.
2. Notion에서 로그인하고 워크스페이스를 선택한다. 위 기록 페이지를 허용한다.
3. 앱으로 돌아오면 **대상 불러오기**를 누른다. 필요하면 데이터베이스 이름으로 검색한다.
4. 준비한 데이터베이스를 선택하고 **6종 기록을 선택한 대상으로 지정**을 누른다.
5. **대상 검증 후 저장**을 누른다. ID를 직접 찾아 입력할 필요가 없다. 유형별로 다른 DB를 쓰는 경우 아래 상세 입력에서 개별 대상을 지정할 수 있다.
6. 진행 중인 학습 과정과 날짜 범위를 확인하고 **현재 과정 동기화**를 누른다.
7. 최근 동기화의 성공 상태뿐 아니라 Notion 데이터베이스에 실제 행이 생성됐는지 확인한다. 해당 기간에 원본 기록이 없으면 생성 0건이 정상이다.

검색 결과가 비어 있으면 승인한 페이지와 데이터베이스 접근 권한을 먼저 확인한다. 새로 공유한 대상은 검색 반영에 시간이 걸릴 수 있다. 대상 발견은 [Notion Search API](https://developers.notion.com/reference/post-search)로 처리하며 저장 시 속성을 다시 검증한다.

## 4. Slack 최초 앱 등록

### 4-1. 앱 생성과 권한 설정

1. [Slack 앱 관리](https://api.slack.com/apps)에 로그인한다.
2. **Create New App**에서 빈 앱을 만드는 항목(From scratch)을 선택한다.
3. 앱 이름과 개발용 워크스페이스를 선택해 생성한다.
4. **Incoming Webhooks** 메뉴에서 **Activate Incoming Webhooks**를 켠다.
5. **OAuth & Permissions → Redirect URLs**에 확정된 HTTPS 주소와 `/api/v1/integrations/browser/slack/callback`을 합친 URL을 추가하고 저장한다.
6. **Bot Token Scopes**에 `incoming-webhook`이 있는지 확인한다. 현재 기능에는 채널 이력, DM 읽기, 사용자 프로필 권한이 필요하지 않다.
7. **Basic Information → App Credentials**에서 Client ID와 Client Secret을 확인한다.
8. 다른 워크스페이스에도 설치할 서비스라면 앱 관리의 배포 설정에서 필요한 정보를 입력하고 배포를 활성화한다. 워크스페이스 정책에 따라 관리자 승인이 먼저 필요할 수 있다.

이 구현은 일반 Slack의 `slack.com` OAuth를 사용한다. GovSlack OAuth는 구현 범위에 포함하지 않는다. [Slack Incoming Webhooks 안내](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/), [OAuth 설치 안내](https://docs.slack.dev/authentication/installing-with-oauth/)

### 4-2. 서버에 입력

```dotenv
SLACK_CLIENT_ID=<Slack Client ID>
SLACK_CLIENT_SECRET=<Slack Client Secret>
SLACK_REDIRECT_URI=<실제 HTTPS 앱 주소>/api/v1/integrations/browser/slack/callback
```

`INTEGRATION_CREDENTIAL_KEY`는 키 준비 스크립트가 만든 값을 유지한다. 이 OAuth 방식에서는 `SLACK_WEBHOOK_URL`, `SLACK_CREDENTIAL_REF`, `SLACK_CHANNEL_NAME`을 운영자가 복사할 필요가 없다. 이 세 항목은 기존 서버 공용 Webhook 방식의 호환 설정이다. 사용자별 연결만 운영하려면 공용 설정은 비워 둔다.

앱에서 공급자 토큰을 갱신하는 방식이 아니라 설치 응답의 incoming webhook을 사용한다. Event Subscriptions, Slash Commands, Signing Secret, 별도 사용자 토큰을 등록할 필요가 없다.

### 4-3. 일반 사용자 흐름 확인

1. **HTTPS 앱 주소로 로그인**하고 외부 연동 화면을 연다.
2. **Slack 연결** → Slack 워크스페이스와 알림을 받을 채널 선택 → 허용을 누른다.
3. 돌아온 화면에서 연결된 워크스페이스·채널을 확인한다.
4. **테스트 메시지**를 한 번 누른다. 전송 이력과 실제 Slack 채널에서 메시지를 확인한다.
5. 원하는 경우 **Slack 알림 켜기**를 누른다. 최초 연결만으로 정기 알림을 켜지는 않는다.
6. **연결 해제**하면 이 앱의 해당 사용자 연결과 대기 전송이 해제된다. Slack 워크스페이스의 설치 자체를 없애려면 ‘Slack 권한·앱 관리’에서 제거한다. 같은 앱을 쓰는 다른 사용자도 영향을 받을 수 있으므로 워크스페이스 단위 삭제인지 확인한다.

## 5. AI(OpenRouter) 최초 설정

### 5-1. 운영자에게 필요한 것

OpenRouter는 이 PKCE 흐름에 client ID/secret을 발급받는 앱 등록 단계가 없다. 운영자는 콜백 주소, 암호화 키, 사용할 모델을 설정한다. 사용자는 연결 버튼을 통해 자기 계정의 키 발급을 승인한다. [OpenRouter OAuth PKCE 공식 안내](https://openrouter.ai/docs/guides/overview/auth/oauth)

1. [OpenRouter 모델 목록](https://openrouter.ai/models)에서 한국어 일간 피드백에 사용할 모델을 정한다.
2. 모델 ID를 복사한다. 표시 이름이 아니라 `공급자/모델` 형식의 ID를 사용한다.
3. `response_format`의 JSON 출력을 지원하는 모델인지 확인한다. 이 구현은 `json_object` 응답을 요청한다. 지원 여부는 모델별로 다르다. [OpenRouter 구조화 출력 안내](https://openrouter.ai/docs/guides/features/structured-outputs)
4. `.env`에 입력한다.

```dotenv
OPENROUTER_REDIRECT_URI=http://localhost:8080/api/v1/integrations/browser/openrouter/callback
OPENROUTER_MODEL=<선택한 실제 모델 ID>
```

운영 HTTPS 주소를 쓰면 콜백도 같은 주소로 바꾼다. `INTEGRATION_CREDENTIAL_KEY`를 유지한다. 사용자별 연결에는 `AI_PROVIDER_API_KEY`가 필요 없다. 기존 `AI_PROVIDER_ENDPOINT/API_KEY/MODEL`은 이전 서버 공용 게이트웨이 호환용이다.

### 5-2. 일반 사용자 흐름 확인

1. **OpenRouter 연결**을 누른다.
2. OpenRouter에 로그인하거나 계정을 만든다.
3. 발급할 키의 사용 조건·한도를 확인하고 연결을 승인한다. 요금은 연결한 OpenRouter 계정에 적용된다.
4. 앱으로 돌아와 연결 상태를 확인한다. 키 원문은 화면에 표시하지 않는다.
5. [AI 도우미](http://localhost:8080/assistant)에서 진행 중인 과정을 확인하고 **오늘 피드백 만들기**를 누른다.
6. 한국어 피드백과 OpenRouter 사용 내역을 확인한다. API 이용 가능 잔액·한도는 사용자의 OpenRouter 계정에서 관리한다.
7. **연결 해제**는 앱이 저장한 키와 대기 요청을 제거한다. OpenRouter 키 자체를 폐기하려면 ‘OpenRouter 권한·한도 관리’에서 해당 키도 삭제한다.

현재 OpenRouter 어댑터는 **일간 피드백**을 지원한다. 해당 날짜의 학습 제목·진행 상태를 원본으로 전달한다. AI 연습문제의 저장·채점·커리큘럼 자동 변경까지 활성화하는 설정은 아니다. 다른 목적의 기존 게이트웨이 계약은 별개다. 앱 요청 제한과 공급자의 실제 결제 한도도 서로 다르다.

## 6. 설정 반영과 완료 기준

`.env`를 저장한 뒤 프로젝트 루트에서 실행한다.

```powershell
docker compose up -d --build backend
docker compose restart nginx
```

환경변수 변경은 단순 `restart backend`로 반영되지 않는다. `up -d`로 컨테이너를 재생성한다. 전체 스택이 꺼져 있다면 다음 명령을 사용한다.

```powershell
.\scripts\ide.ps1 -Action Up
```

로컬 포트는 기본 8080이다. 운영에서는 localhost 링크 대신 실제 HTTPS 주소로 접근한다.

| 확인 항목 | 완료로 볼 수 있는 증거 |
|---|---|
| 최초 앱 설정 | 해당 연결 버튼이 활성화됨 |
| OAuth 연결 | 공급자 승인 후 앱에 연결된 계정·워크스페이스 상태 표시 |
| Notion 사용 | 대상 검증 통과, 실제 기록 행 생성 |
| Slack 사용 | 테스트 전송 SUCCESS, 실제 채널 메시지 도착 |
| AI 사용 | 일간 작업 SUCCESS, 한국어 피드백, 공급자 사용 내역 |
| 해제 | 앱의 연결 해제 상태와 대기 작업 취소. 공급자 권한 삭제는 별도 확인 |

버튼 활성화와 자동 테스트 통과만으로 실제 연동 완료라고 판단하지 않는다.

## 자주 막히는 지점

| 증상 | 확인 순서 |
|---|---|
| 연결 버튼 비활성 | 필수 `.env` 값 → 암호화 키 → 컨테이너 재생성 → 화면 새로고침 |
| Slack만 비활성 | `SLACK_REDIRECT_URI`가 HTTPS인지 확인 |
| callback 실패 / 로그인 필요 | 시작 주소와 돌아온 주소가 같은지, 로그인 세션이 유지되는지 확인 |
| `bad_redirect_uri` | 공급자 등록 URL과 `.env`의 경로·포트·끝 슬래시까지 일치시키기 |
| 승인 후 다시 실패 | 인증 요청은 10분·1회용. 뒤로가기 대신 앱 연결 버튼으로 새로 시작 |
| Notion 대상 검증 실패 | 페이지 권한과 6개 속성 이름·유형 확인 |
| Slack 설치 승인 대기 | 워크스페이스 관리자의 앱 설치 정책 확인 |
| AI 연결은 됐지만 생성 실패 | 모델 ID·JSON 지원·계정 잔액·키 한도 확인 |
| 재시작 후 기존 연결이 안 됨 | 최초 암호화 키가 유지됐는지 확인. 키를 잃었으면 사용자 재연결 필요 |

콜백 URL 전체에는 일회용 코드가 포함되므로 공유하지 않는다. 이 저장소 Nginx는 인증 콜백의 query string을 접근 로그에서 제외한다. 외부 HTTPS 프록시를 추가할 때도 같은 처리가 필요하다.
