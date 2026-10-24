# 브라우저 연동 추가 계약

관련 이슈: #8. 기존 OpenAPI v1.2.1의 Slack/Notion/AI API는 유지하며 아래 계약을 추가한다.

## Slack · OpenRouter

`provider`는 `slack` 또는 `openrouter`만 허용한다. JSON은 기존 `success/data/error/meta` envelope와 `Cache-Control: no-store`를 사용한다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/integrations/browser/{provider}` | 현재 사용자 설정 준비·연결 상태 |
| POST | `/api/v1/integrations/browser/{provider}/authorization-requests` | 10분 유효한 인증 URL 발급. 로그인과 CSRF 필요 |
| GET | `/api/v1/integrations/browser/{provider}/callback` | 공급자 콜백. 로그인 세션·state 검증 후 설정 화면으로 302 |
| DELETE | `/api/v1/integrations/browser/{provider}` | 로그인·CSRF·`If-Match: "revision"` 필요. 로컬 키 제거와 대기 작업 취소 |

GET data:

```json
{
  "configured": false,
  "connected": false,
  "revision": 0,
  "channelName": null,
  "workspaceName": null,
  "model": null,
  "managementUrl": null
}
```

`model`은 OpenRouter일 때 서버 모델 ID 문자열(미설정은 빈 문자열)이다. 나머지 세부 문자열은 nullable이다. `configured`는 서버 필수 설정의 형식 검증이며 외부 승인·잔액·모델 이용 가능성을 보장하지 않는다. `connected`는 사용자별 자격정보가 저장된 상태다. 자격정보 원문은 어떤 응답에도 포함하지 않는다.

POST data는 `authorizationUrl`과 ISO 8601 `expiresAt`이다. 미설정은 `SERVICE_UNAVAILABLE`. 재시작 요청은 이전 대기 state를 소모한다. POST 재시도는 새 인증 시도이며 멱등 요청을 약속하지 않는다.

콜백 query는 `state`, `code` 또는 `error`. OpenRouter는 PKCE S256을 쓰며 state를 `callback_url` 안에 넣는다. state 원문을 DB에 저장하지 않는다. 사용자·세션·공급자·연결 revision을 결합하고 만료/재사용을 거부한다. 네트워크 교환은 DB 트랜잭션 밖에서 수행한다. 교환 도중 해제되면 이전 승인 결과를 적용하지 않는다.

DELETE data는 `disconnected: true`, `providerRevocationStatus: "MANUAL_REQUIRED"`, nullable `managementUrl`. 이미 전송 중인 작업은 끝날 수 있다. Slack 워크스페이스 앱 제거와 OpenRouter 원격 키 폐기는 사용자가 공급자에서 처리한다. 이 API가 원격 권한까지 폐기했다고 표시하지 않는다.

## Notion 설정·대상 검색

| 메서드 | 경로 | 응답 data |
|---|---|---|
| GET | `/api/v1/integrations/notion/setup` | `{ configured: boolean }` |
| GET | `/api/v1/integrations/notion/available-targets?query=...&cursor=...` | `{ content: [{ title, databaseId, dataSourceId }], nextCursor: string \| null }` |

두 API 모두 로그인 필요. 검색어 최대 100자, cursor 최대 200자, 공급자 페이지당 50개를 요청한다. 접근 가능한 데이터 소스만 반환하고 토큰은 서버에서만 사용한다. 대상 변경 도중 검색 결과가 돌아오면 기존 연결 결과를 적용하지 않는다. 검색 결과는 저장 적합성 보장이 아니며 기존 `PUT /notion/targets`가 실제 6종 속성을 검증한다.

## AI 실행

기존 일간 피드백 요청(`/api/v1/ai/daily-feedback`)은 연결한 사용자의 OpenRouter 키와 모델을 접수 시 고정한다. 현재 OpenRouter 어댑터는 `DAILY_FEEDBACK`만 지원한다. 요청 입력은 서버가 소유권을 확인한 과정·날짜의 학습 제목과 진행 상태로 구성한다. 한국어 요약·목록을 검증하고 ID와 날짜는 서버 입력에서 설정한다.

작업은 조건부 claim과 lease로 중복 실행을 막는다. 통신 결과가 불명확하거나 lease가 만료되면 내부 `UNKNOWN`으로 보존하고 자동 재전송하지 않는다. 기존 공개 AI 상태 계약에서는 `FAILED`와 `AI_PROVIDER_RESULT_UNKNOWN` 오류로 표현한다. 키 재연결 뒤 과거 작업에 새 키를 대입하지 않는다. 기존 서버 공용 AI 게이트웨이는 연결이 없는 사용자의 호환 경로로 남는다.

## 저장·운영

V14는 `browser_connection`, `browser_oauth_state`, `ai_job.browser_credential_ref`를 추가한다. 기존 migration은 변경하지 않는다. AES-GCM AAD에 사용자·공급자·자격 참조를 묶는다. 암호화 키 유실 시 재연결이 필요하다. 현재 암호화 키 ID별 자동 회전/원격 키 자동 폐기는 지원하지 않는다.

Nginx 인증 콜백 로그는 query string을 제외한다. 실제 운영자의 등록 절차는 [운영자 가이드](integration-operator-setup.md)를 따른다.
