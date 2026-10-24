# 1F Notion 구현·검증 결과

검증 시각: 2026-09-23 23:38 KST

## 구현

- OpenAPI의 기존 상태 조회와 누락된 Notion 9개 operation을 모두 연결했다.
- V13에 세션 결합 OAuth state, UUID credential reference와 AES-GCM ciphertext, revocation queue, 6종 target set/item, sync batch/item을 추가했다.
- callback은 성공·실패·로그인 필요의 고정 앱 경로로만 redirect하며 authorization code나 token을 앱 URL에 노출하지 않는다.
- target 저장 전에 Notion data source의 부모 database와 `Name`, `SourceKey`, `SourceVersion`, `EntityType`, `Summary`, `OccurredAt` 속성을 원격 검증한다.
- worker는 연결·대상 revision을 외부 호출 전후로 확인하고 SourceKey로 기존 page를 조회해 create/update/skip을 결정한다. 재시작 시 `PROCESSING` batch에 남은 `READY` item도 다시 claim한다.
- 부분 성공, 실패, 결과 불명 `UNKNOWN`, 선택 재시도, 재인증 중단, 연결 해제와 token 폐기 재시도를 구현했다.
- UI에 연결·해제, 6종 target, 기간·엔터티·교육과정 동기화, 이력·상세·재시도를 연결했다.

## 자동 검증

| 검증 | 결과 |
|---|---:|
| Backend 단위/MVC | 57/57 PASS |
| Notion PostgreSQL IT | 1/1 PASS |
| 전체 PostgreSQL IT | 58/58 PASS |
| Frontend Vitest | 110/110 PASS, 28 files |
| Frontend typecheck | PASS |
| Frontend lint | PASS |
| Frontend production build | PASS |

Notion IT는 OAuth 세션 binding, token ciphertext 저장, 6종 target, create/update/skip, worker 재시작 복구, 부분 실패와 `UNKNOWN` 재시도, 이력·상세, 연결 해제와 폐기를 fake provider로 검증한다. 최종 OAuth 경합·worker 복구 보강 후 단위 57개와 Notion IT를 다시 통과시켰다.

## 실제 로컬 검증

- Compose 4서비스 healthy, Nginx 공개 포트 `8080`.
- 기존 V12 volume에서 V13 migration 1건 적용 후 schema version 13 확인.
- `GET /api/v1/integrations/notion`은 HTTP 200, `DISCONNECTED`, `notionVersion=2026-03-11`, `targetsConfigured=false`를 반환했다.
- 서버 자격정보가 없는 상태에서 연결 버튼은 외부 호출 없이 503 안내로 종료됐다.
- `/settings/integrations` 데스크톱과 390×844에서 Notion 빈 이력, 무가로 overflow, console error 0건을 확인했다.

## 외부 미검증

실제 Notion 앱의 client ID/secret, callback 등록, 32-byte credential key, 접근 권한이 부여된 workspace data source가 없어 실제 OAuth, 원격 target 검증, page create/update, token revoke 성공은 확인하지 않았다. 이를 공급자 성공으로 기록하지 않는다.

필요한 로컬 환경 변수 이름은 `.env.example`의 `NOTION_CLIENT_ID`, `NOTION_CLIENT_SECRET`, `NOTION_REDIRECT_URI`, `NOTION_CREDENTIAL_KEY`, `NOTION_KEY_ID`다. Compose가 이를 Backend의 `APP_NOTION_*` 설정으로 전달한다. 실제 값은 문서·Git에 기록하지 않는다.

## 접속

- 대표 화면: <http://localhost:8080/settings/integrations>
- 상태 API: <http://localhost:8080/api/v1/integrations/notion>
- 현재 실행 명령: `docker compose up -d --build`
