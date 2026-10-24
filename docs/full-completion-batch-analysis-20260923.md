# 100% 완성 일괄 작업 설계 분석

작성일: 2026-09-23

## 구현 진행 업데이트 (2026-09-23 23:38 KST)

관련 상위 이슈는 [#8 기능: OpenAPI 전 범위 및 외부 연동 완성](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8)으로 등록했다.

| 슬라이스 | 상태 | 검증 결과 |
|---|---|---|
| 1A 계약 정렬 | 완료 | V9 실제 upgrade, DTO·오류 계약 테스트 PASS |
| 1B 내부 누락 API 9개 | 완료 | 실제 PostgreSQL 조회·변경 및 ETag 검증 PASS |
| 1C 알림 센터 | 완료 | outbox 3건 처리, 알림 생성·목록·개별/전체 읽음·멱등 재실행·브라우저 검증 PASS |
| 1D Web Push | 구현 완료·외부 미검증 | NOTI-007~009, 암호화 저장, endpoint 방어, Service Worker, VAPID worker, 기기 관리 UI PASS |
| 1E Slack | 구현 완료·외부 미검증 | SLACK-003~005, 멱등 테스트 발송, worker, 선택 재시도·UNKNOWN, 이력 UI PASS |
| 1F Notion | 구현 완료·외부 미검증 | NOTION-003~011, V13, fake provider 전체 상태 전이, 실제 UI 안전 비활성 검증 PASS |

경로 기준 대응은 `91/91`, 누락은 `0/91`이다. 자동 검증은 Backend 단위 `57/57`, PostgreSQL 통합 `58/58`, Frontend `110/110`, typecheck, lint, production build가 모두 통과했다. `/settings/integrations`는 데스크톱·390px 모바일에서 overflow와 console 오류 없이 확인했다. 실제 Notion·Slack·Push 공급자 수신은 외부 자격정보 또는 VAPID 키·HTTPS·사용자 권한이 없어 미검증이며, 현재 수치는 경로 및 내부 구현 상태이지 최종 100% 판정이 아니다.

## 1. 결론

일괄 진행은 가능하다. 다만 하나의 거대한 변경으로 합치면 계약·DB·외부 통신·브라우저 권한·운영 배포의 실패 원인을 분리하기 어렵다. 따라서 하나의 완료 목표 아래 세 개의 매크로 배치를 순차 실행하고, 각 배치 안에서는 독립적인 세부 슬라이스를 완료 게이트로 사용한다.

`100%`는 다음을 모두 만족하는 상태로 정의한다.

1. OpenAPI v1.2.1에서 제품 범위로 채택한 91개 operation이 경로·method·DTO·상태·오류 계약과 일치한다.
2. 핵심 사용자 흐름과 알림·AI·Notion·Slack·Web Push가 실제 저장 및 조회 결과로 이어진다.
3. 외부 장애가 학습·문제·시험·평가의 성공을 되돌리지 않는다.
4. PC·모바일·다크모드·키보드·빈 상태·실패·충돌 상태를 실제 브라우저에서 검증한다.
5. 운영형 HTTPS, secure cookie, secret 분리, migration, backup/restore 및 재시작을 검증한다.
6. 실제 자격정보가 필요한 항목은 실제 공급자 성공까지 확인한 경우에만 100%로 판정한다.

## 2. 현재 기준선

### 완료되어 유지할 기반

- 승인된 개발용 6개월 과정, 학습 콘텐츠 6개, 문제 6개, 시험 4개가 있다.
- 로그인 우회 개발 흐름에서 학습·문제·오답·시험·진척도 사용자 흐름과 실제 PostgreSQL 검증 기록이 있다.
- Backend 일반 테스트 57개, PostgreSQL 통합 테스트 58개, Frontend 28파일 110개 테스트와 production build 통과 기록이 있다.
- 개발 Compose 4서비스, 운영형 로컬 Compose, DB 백업·별도 volume 복원 검증 기록이 있다.
- 주요 10개 route의 데스크톱·모바일 브라우저 순회 기록이 있다.
- 2026-09-23 분석 시점에 개발 Compose 4서비스는 모두 healthy이고 `/settings/integrations`는 HTTP 200이다.

### OpenAPI 경로 대조

Controller annotation과 OAuth 보안 설정을 OpenAPI 경로 형태로 정규화해 비교한 결과다. 이는 경로 존재 여부만 비교한 값이며, 동작과 응답 계약의 완전성을 의미하지 않는다.

| 항목 | 수량 |
|---|---:|
| OpenAPI operation | 91 |
| 현재 경로 대응 | 91 |
| 누락 경로 | 0 |

| 영역 | 누락 operation | 수량 |
|---|---|---:|
| 없음 | - | 0 |

앱 알림 3개 API와 outbox dispatcher, Web Push 구독 3개 API·전달 worker, Slack 테스트·이력 3개 API·전달 worker, Notion OAuth·대상·동기화 9개 API와 worker까지 구현됐다. Notion·Slack·Web Push는 실제 공급자 수신만 외부 선행조건으로 남아 있다.

## 3. 발견한 설계·계약 차이

| ID | 발견 사항 | 영향 | 채택 방향 |
|---|---|---|---|
| FC-01 | OpenAPI의 시험 알림 필드는 `beforeMinutes`와 범위 1~1440인데 현재 DB JSON·Java·TypeScript는 `minutesBefore`와 범위 5~10080이다. | 공개 DTO 계약 불일치 | 적용된 V8은 수정하지 않고 후속 migration에서 JSON key를 변환하고 Backend/Frontend를 OpenAPI에 맞춘다. |
| FC-02 | 해결: Slack 미구성 활성화와 테스트 발송을 409 `SLACK_NOT_CONFIGURED`로 정렬했다. | 클라이언트가 재시도 불가 설정 오류를 구분함 | 회귀 테스트를 유지한다. |
| FC-03 | 해결: Slack Webhook은 DB·응답에 저장하지 않고 환경의 credential reference로만 연결하며, HTTPS·host·DNS/IP를 발송 시 재검증한다. | 비밀 노출과 SSRF 위험 축소 | 운영 secret manager adapter와 실제 Webhook 검증은 배치 3에서 수행한다. |
| FC-04 | 해결: 학습 완료 outbox를 알림·Slack·Push 작업으로 변환하는 lease 기반 dispatcher와 중복 방지 routing을 구현했다. | 실제 외부 수신만 별도 검증 필요 | 회귀 테스트를 유지한다. |
| FC-05 | 해결: Push 구독 등록·목록·해제, endpoint/auth AES-GCM 암호화, endpoint SSRF 방어, Service Worker, VAPID 전달 worker를 구현했다. | 실제 공급자 수신만 미검증 | 실제 VAPID 키와 HTTPS origin에서 권한·백그라운드 수신·해제를 검증한다. |
| FC-06 | 해결: session-bound OAuth state, AES-GCM credential store, 대상 6종 원격 검증, batch/item worker와 비동기 폐기를 구현했다. | 실제 Notion 앱 인수만 남음 | 실제 workspace에서 OAuth와 token revoke를 검증한다. |
| FC-07 | 해결: V13에서 batch root와 item, count·부분 성공·실패·`UNKNOWN` 결과를 보존한다. | 실제 대량 데이터 검증 필요 | 운영 데이터 표본으로 1,000건 상한과 부분 실패를 확인한다. |
| FC-08 | 해결: `connectionRevision`과 `targetsRevision`을 분리하고 worker가 외부 호출 전 현재 revision을 확인한다. | 재연결·대상 변경 경합 방어 | 회귀 테스트를 유지한다. |
| FC-09 | 해결: Push·Slack·Notion 전용 오류 코드, method별 security route, provider allowlist를 구현했다. | 실제 provider별 오류 매핑 확인 필요 | 외부 인수에서 4xx·429·5xx·timeout을 확인한다. |
| FC-10 | 해결: Push·Slack·Notion 상태·mutation·이력·재시도를 실제 query/mutation에 연결했다. | 실제 계정 연결 화면은 미검증 | 실제 provider 자격정보로 브라우저 E2E를 수행한다. |

## 4. 목표 아키텍처

### 핵심 처리 흐름

```text
핵심 업무 트랜잭션
  -> outbox_event(PENDING)
  -> OutboxDispatcher가 lease 점유
  -> notification 및 delivery/sync 작업을 중복 없이 생성
  -> 외부 Worker가 별도 트랜잭션으로 작업 점유
  -> 네트워크 호출은 DB 트랜잭션 밖에서 수행
  -> lease token 조건부 갱신으로 SUCCESS/FAILED/UNKNOWN 확정
```

외부 호출 중 DB lock을 유지하지 않는다. 먼저 짧은 트랜잭션에서 `FOR UPDATE SKIP LOCKED`와 lease를 확보하고, 호출 후 새 트랜잭션에서 동일 lease token인 경우에만 결과를 반영한다. 응답 유실처럼 제공자 결과를 확정할 수 없는 경우 성공이나 실패로 추측하지 않고 `UNKNOWN`으로 남긴다.

### Backend 경계

| 구성요소 | 책임 |
|---|---|
| `NotificationService` | 목록, 읽음, 전체 읽음, 설정 계약 |
| `OutboxDispatcher` | 업무 이벤트를 in-app/Push/Slack 전달 작업으로 변환 |
| `PushSubscriptionService` | 구독 upsert·목록·해제, deviceId 소유권, endpoint 검증 |
| `SubscriptionCipher` | endpoint/auth AES-GCM 암호화와 keyId 기반 rotation |
| `NotificationDeliveryWorker` | Web Push·Slack delivery lease, 재시도, UNKNOWN 처리 |
| `CredentialStore` | Notion token을 UUID reference 뒤 AES-GCM ciphertext로 저장·조회·폐기하고 Slack은 서버 환경의 credential reference만 사용 |
| `NotionOAuthService` | 세션·사용자·connectionRevision에 결합한 10분 state 생성·소비 |
| `NotionTargetService` | 6종 target 원격 검증, targetsRevision/ETag 관리 |
| `NotionSyncService` | 기간·소유권·1,000건 상한 검증과 batch/item snapshot 생성 |
| `NotionSyncWorker` | SourceKey 조정, upsert/archive, 부분 성공, 실패 항목 재처리 |
| `SlackProvider` / `NotionProvider` / `WebPushProvider` | 외부 HTTP·프로토콜 adapter. 테스트에서는 fake로 대체 |

### DB 롤포워드 제안

적용된 V1~V13은 수정하지 않는다.

1. 알림 계약 정렬 migration
   - `notification_settings.testReminder.minutesBefore`를 `beforeMinutes`로 변환한다.
   - 기본 JSON과 CHECK/검증 범위를 OpenAPI 1~1440에 맞춘다.
   - Push expiration 시각 등 구독 수명주기에 필요한 필드를 보완한다.
2. 외부 전달 runtime migration
   - V11 Web Push, V12 Slack, V13 Notion runtime을 롤포워드로 추가했다.
   - V13은 OAuth state, 암호화 credential reference, revocation queue, 6종 target set/item, sync batch/item과 revision·결과 메타데이터를 보존한다.
   - 기존 `notification_delivery`, `sync_attempt`, `outbox_event`의 상태·lease·unique key는 재사용한다.

컬럼과 constraint는 DB catalog, clean/upgrade migration 테스트로 확정했으며 Flyway V1~V13 전체 적용과 기존 볼륨 V12→V13 upgrade를 검증했다.

### 비밀정보 경계

- DB에는 `credential_ref`, `key_id`, ciphertext만 저장한다.
- 실제 Notion token, Slack webhook, Push private/encryption key는 응답·로그·문서·Git에 기록하지 않는다.
- `CredentialStore`를 port로 분리한다. 로컬은 추적 제외된 암호화 secret 저장소, 운영은 배포 대상 secret manager adapter를 사용한다.
- Notion callback은 code를 앱 URL로 다시 노출하지 않고 성공·실패 고정 경로로만 redirect한다.
- Push endpoint는 HTTPS, 허용 provider host, DNS 재확인, 사설/loopback/link-local IP 거절, redirect 금지를 적용한다.

## 5. 일괄 실행 배치

### 배치 1 — 계약 및 기능 완성

| 슬라이스 | 범위 | 완료 게이트 |
|---|---|---|
| 1A 계약 정렬 | FC-01~03, 전용 오류 코드, security route | OpenAPI DTO/오류 계약 테스트와 기존 데이터 upgrade PASS |
| 1B 내부 누락 API | COMMON, DASH, ATTEMPT, PROGRESS, CAL, SETTING 9개 | 실제 PostgreSQL fixture로 9개 정상·빈 상태·소유권·ETag 검증 |
| 1C 알림 센터 | NOTI-001~003, outbox dispatcher, 알림 UI | 생성→목록→개별/전체 읽음과 재시작 보존 PASS |
| 1D Web Push | NOTI-007~009, 암호화, Service Worker, worker | 구독→목록→발송→해제, endpoint 비노출, 실패/만료 처리 PASS |
| 1E Slack | SLACK-003~005, delivery worker, 이력 UI | 고정 테스트 메시지→상태/이력, timeout UNKNOWN, 중복 방지 PASS |
| 1F Notion | NOTION-003~011, OAuth, targets, batch/item worker, UI | 연결→6종 대상→동기화→부분실패 재처리→해제 PASS |

### 배치 2 — 전체 통합 및 화면 검증

- 로그인 → 온보딩 → 학습 → 문제 → 오답 → 시험 → 진척도 흐름을 새 사용자와 재접속 조건으로 실행한다.
- 알림 이벤트가 핵심 업무 commit 이후 생성되며 외부 장애가 업무 성공을 롤백하지 않는지 확인한다.
- 다중 탭 stale ETag, 중복 Idempotency-Key, CSRF, 다른 사용자 ID, 세션 만료, worker 재시작과 lease 회복을 검증한다.
- 전체 route를 1280×900, 390×844, 375×812에서 light/dark로 확인한다.
- 가로 overflow, console error/warning, label, 키보드 focus, loading/empty/error/disabled/processing/partial 상태를 확인한다.
- Backend 일반 테스트, PostgreSQL IT, Frontend typecheck/lint/Vitest/build, Compose smoke를 전부 다시 실행한다.

### 배치 3 — 실제 외부 및 운영 완성

- Google OAuth를 인증 우회 없이 HTTPS 환경에서 로그인·로그아웃·재로그인한다.
- 실제 AI 공급자 생성과 사용량을 검증한다.
- 실제 Notion workspace OAuth, 6종 target 검증, upsert와 실패 재처리를 확인한다.
- 실제 Slack webhook에 고정 테스트 메시지를 보내고 delivery 상태를 확인한다.
- 실제 브라우저에서 Web Push 권한·구독·백그라운드 수신·해제를 확인한다.
- 운영형 HTTPS, secure cookie, secret manager, migration, backup/restore, 재시작, 로그·health를 검증한다.
- OCI 배포를 최종 범위로 유지한다면 tenancy, domain, DNS, certificate, secret manager, backup 보관 위치가 필요하다.

## 6. 테스트 전략

| 층 | 필수 검증 |
|---|---|
| 단위 | DTO 범위, enum, 상태 변환, 암호화 round-trip, endpoint 검증, backoff 결정 |
| Provider contract | 성공, 4xx, 429, 5xx, timeout, connection reset, malformed response, secret redaction |
| PostgreSQL IT | clean/upgrade migration, outbox 원자성, unique dedupe, lease 경합·만료 복구, 소유권, rollback |
| API | 27개 신규 경로의 정상·인증·CSRF·멱등성·ETag·페이지·정렬·전용 오류 |
| Frontend | decoder, mutation key 보존, stale 재조회, query invalidation, 상태별 렌더링 |
| Browser E2E | 실제 상태 전이와 다음 화면 handoff, Service Worker/권한, 모바일·다크모드·접근성 |
| 운영 | source mount 없음, secure cookie, secret 비노출, DB backup/restore, worker 재시작 |

자동 재시도는 제공자가 요청을 받지 않았다고 확정할 수 있는 retryable 실패에만 적용한다. 응답 유실처럼 결과가 불명확하면 `UNKNOWN`으로 보존하고 자동 중복 전송을 금지한다. 기본 재시도 정책은 최대 3회와 지수 backoff+jitter를 권장하며 실제 provider 제한과 원본 정책을 확인해 확정한다.

## 7. 선행 결정과 외부 준비

| 시점 | 필요한 결정·준비 | 없을 때 진행 가능한 범위 |
|---|---|---|
| 배치 1 시작 전 | 상위 이슈 등록 | 분석·이슈 초안까지만 가능 |
| Notion OAuth 전 | 로컬 credential store 방식과 운영 secret manager 선택 | provider fake와 disabled 상태까지 가능 |
| 외부 실검증 전 | AI endpoint/key/model, Notion 앱, Slack webhook ref, VAPID key | 구현·자동화·안전한 비활성 상태까지 가능 |
| 운영 E2E 전 | HTTPS origin, Google callback, domain/DNS/certificate | 로컬 개발·운영형 HTTP health까지 가능 |
| OCI 배포 전 | tenancy/compartment/network/database/backup 기준 | 배포 전 package와 runbook까지 가능 |

권장 secret 구성은 로컬의 추적 제외 암호화 저장소와 운영의 OCI Vault adapter다. OCI가 최종 배포 대상이 아니라면 운영 secret manager만 다시 선택하면 나머지 application port는 유지한다.

## 8. Git 및 작업 경계

- 현재 작업 트리는 대량의 기존 미커밋 변경을 포함하므로 기존 변경을 초기화하거나 덮어쓰지 않는다.
- 기존 `integration-stage19-issue-draft.md`는 설정·진단 UI 범위만 다루므로 전체 연동 완료 이슈를 대신하지 않는다.
- 구현 전에 [전체 완성 이슈 초안](full-completion-issue-draft.md)을 원격 이슈로 등록하고 번호를 확정한다.
- 실제 변경은 최소한 `계약/DB`, `알림·Push`, `Slack`, `Notion`, `통합 QA`, `운영` 목적별로 분리한다.
- Draft PR은 중간 점검이며 배치의 명시적 완료 게이트 또는 사용자 승인 지점까지 작업을 이어간다.

## 9. 완료 판정

코드가 존재하거나 mock 테스트만 통과한 상태는 100%가 아니다. 다음 셋을 분리해 보고한다.

- 완료: 실제 저장·조회·상태 전이와 요구 검증이 통과함.
- 실패: 재현 가능한 결함이 있으며 완료 조건을 충족하지 못함.
- 미검증: 자격정보·HTTPS·배포환경 등 외부 선행조건이 없어 성공을 확인하지 못함.

최종 100% 판정은 누락 operation 0개, 전체 자동 회귀 통과, 핵심 브라우저 E2E 통과, 실제 외부 공급자 성공, 운영 HTTPS/복구 검증이 모두 충족된 뒤에만 수행한다.
