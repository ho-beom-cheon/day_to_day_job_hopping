# AI·외부 연동 실검증 결과 (2026-09-21)

## 실행 환경

- 검증 시각: 2026-09-21 04:00~04:03 KST
- 실행 명령: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Up`
- 상태: PostgreSQL, Backend, Frontend, Nginx 모두 `healthy`
- 대표 화면: <http://localhost:8080/assistant>
- 외부 연동 설정: <http://localhost:8080/settings#integrations>
- 개발 인증: 로컬 `DEV_AUTH_BYPASS=true`; 테스트 사용자 비밀번호나 토큰은 사용하지 않음

## 완료한 실검증

| 대상 | 실제 확인 | 결과 |
|---|---|---|
| AI 사용량 API | `GET /api/v1/ai/usage` | 200. `enabled=false`, 오늘 사용량 0/20, 토큰 0으로 실제 설정 상태를 반환 |
| AI 화면 | Chrome에서 `/assistant` 접속 | 공급자 연결 대기 상태, 과정 정보와 사용량 표시, 콘솔 error/warn 0, 2560px에서 수평 overflow 없음 |
| AI 생성 차단 | CSRF와 Idempotency-Key를 발급받아 `POST /api/v1/ai/daily-feedback` 호출 | 503 `AI_DISABLED`. 가짜 성공 결과나 작업을 생성하지 않음 |
| 알림 설정 | `GET /api/v1/notifications/settings` | 200. 저장된 설정과 revision 2 반환 |
| Web Push | `GET /api/v1/push/config` | 200. `enabled=false`, 공개 키 `null` |
| Notion | `GET /api/v1/integrations/notion` | 200. `DISCONNECTED`, workspace/동기화 시각 없음 |
| Slack | `GET /api/v1/integrations/slack` | 200. `configured=false`, 채널·이벤트 없음 |
| Slack 활성화 차단 | CSRF와 If-Match를 포함해 활성화 PATCH 호출 | 503 `SERVICE_UNAVAILABLE`. 미설정 상태를 연결 성공으로 저장하지 않음 |
| 외부 연동 화면 | Chrome에서 설정 화면 접속 | Web Push/Notion/Slack 모두 실제 미설정 상태로 비활성 표시, 콘솔 error/warn 0, 2560px에서 수평 overflow 없음 |

## 발견 결함

### AI 작업 목록의 선택 필터 NULL 바인딩 오류

- `GET /api/v1/ai/jobs`: 500 `INTERNAL_SERVER_ERROR`
- `purpose`만 전달하거나 `status`만 전달해도 500
- `purpose=DAILY_FEEDBACK&status=SUCCESS`처럼 두 필터를 모두 전달하면 200과 빈 페이지를 반환
- Backend 로그: `BadSqlGrammarException`
- 원인 범위: `AiService.list`의 `(? is null or job_type=?)`, `(? is null or status=?)` 형태에서 PostgreSQL이 NULL JDBC 파라미터 타입을 추론하지 못하는 경로
- 영향: API 계약상 선택 항목인 필터를 생략하는 기본 목록 조회가 실패함
- 이번 검증에서는 코드를 수정하지 않았으며, 필터별 조건 조립 또는 명시적 타입 캐스팅과 회귀 테스트가 필요함

## 미검증과 차단 사유

- AI 공급자 실제 성공 호출: `AI_PROVIDER_ENDPOINT`, `AI_PROVIDER_API_KEY`, `AI_PROVIDER_MODEL`이 모두 비어 있어 미검증
- Web Push 실제 구독·발송: 공개 키가 없고 구독/발송 worker가 구현되지 않아 미검증
- Notion OAuth·동기화: 연결 callback과 sync worker가 구현되지 않아 미검증
- Slack 실제 메시지 발송: credential ref가 없고 전송 worker가 구현되지 않아 미검증
- Google OAuth: 이번 AI·외부 연동 검증은 개발 인증 우회 세션으로 수행했으며, 별도의 실제 로그인 재검증은 하지 않음

## 사용자 확인 방법

현재 스택은 실행 중이다. 대표 화면은 <http://localhost:8080/assistant>, 외부 연동은 <http://localhost:8080/settings#integrations>에서 확인한다. 중지 후 다시 실행하려면 저장소 루트에서 다음 명령을 사용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Up
```

외부 성공 경로를 검증하려면 실제 값을 Git에 기록하지 말고 무시된 로컬 `.env`에 공급자 설정을 준비한 뒤 Backend를 재생성해야 한다. Notion·Push·Slack은 자격정보만으로는 부족하며 해당 worker 구현이 선행되어야 한다.
