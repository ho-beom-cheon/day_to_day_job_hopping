# 프로젝트 전체 점검 및 다음 단계 착수 — 2026-09-24

점검 시각: 2026-09-24 11:20–11:55 KST. 관련 이슈: [#8](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8).

## 현재 판정

| 영역 | 현재 상태 | 근거와 남은 일 |
|---|---|---|
| 작업 트리 | 대량의 진행 중 변경 | `main`에 수정 36, 스테이징 추가 91, 스테이징 후 수정 17, 미추적 108 항목. 기존 변경은 보존했다. |
| 실행 환경 | 정상 실행 | PostgreSQL·Backend·Frontend·Nginx 4서비스 healthy, 공개 포트 `127.0.0.1:8080`. 런타임 smoke 통과. |
| DB·개발 콘텐츠 | 사용 가능 | 실행 DB Flyway V14. 발행된 개발용 과정 1, 문제 버전 6, 시험 버전 4, 사용자 과정 1. `content/operational-v1`은 편집 초안이며 DB 적재 대상이 아니다. |
| Backend | 자동 회귀 통과 | 수정 전 전체 PostgreSQL IT 62/62 통과, V1→V14 clean/upgrade 검증 포함. 시험 수정 후 단위 66/66, 관련 PostgreSQL IT 22/22 통과. |
| Frontend | 자동 회귀 통과 | 수정 후 Vitest 136/136, lint, typecheck, production build 통과. 공개 화면과 주요 보호 화면 경로는 HTTP 200. |
| 인증 | HTTPS 로그인 성공 | `.env`의 `DEV_AUTH_BYPASS=false`, `SESSION_COOKIE_SECURE=true`. 미인증 사용자 조회 API 401, 기존 허용 Google 계정으로 HTTPS 로그인 후 `/workspace`에서 저장된 과정·학습 기록 확인. 이전의 인증 우회 안내는 현재 실행 상태가 아니다. |
| 외부 연동 | 공급자별 별도 판정 | 이전 기록에는 Slack 실제 채널 수신 성공이 있다. Notion 사용자 승인·6종 대상 동기화, OpenRouter 모델 실행, Web Push 백그라운드 수신은 아직 성공 근거가 없다. 이번 점검에서 공급자 상태를 재검증하지 않았다. |

OpenAPI 경로 91/91은 2026-09-23 분석 기록의 **경로 대응 수치**다. 이번 점검은 전체 DTO·상태·오류 계약을 재감사하지 않았으므로 최종 완성률로 해석하지 않는다.

## 이번에 실행한 검증

| 명령 또는 확인 | 결과 |
|---|---|
| `docker compose ps --format json` | 4서비스 running/healthy |
| `docker compose exec -T frontend node scripts/smoke.mjs` | Frontend→Backend/DB, Nginx→Frontend/API, management 비노출 통과 |
| `npm.cmd test`, `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build` (`frontend`) | 최초 133개 테스트 및 모든 명령 통과 |
| `docker compose build backend` | 성공. Maven verify 단계는 Docker cache 재사용 |
| `docker run --rm ... daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify` | 새 실행, PostgreSQL IT 62개, 실패·오류·skip 0, `BUILD SUCCESS` |
| 로컬 HTTP `/`, `/learning`, `/roadmap`, `/practice/1`, `/tests`, `/progress`, `/settings`, `/settings/integrations`, `/assistant` | HEAD 200. 이는 화면의 인증 후 기능 성공을 뜻하지 않음 |
| 미인증 `/api/v1/users/me`, HTTPS Google 로그인 | 401 확인 후 허용 계정 로그인·세션 복원 성공 |
| 시험 수정 후 Docker Backend build 및 `LearningOperationsIT` | 단위 66개, 관련 PostgreSQL IT 22개 통과. 전체 IT는 수정 전 62개 통과했고 수정 후 반복하지 않음 |
| 시험·학습 문구 수정 후 Frontend production Docker build | typecheck·lint·Vitest 136개·build 통과. Backend·Frontend 각 서비스 healthy, smoke 통과 |
| 실제 HTTPS 브라우저 | 완료 학습·제출 문제·오답노트·시험 목록·결과 재방문 확인. `/tests`와 완료 학습 화면 390px에서 가로 overflow·console error/warn 없음 |

`compose.public.yaml`은 Frontend를 production runtime 이미지로 바꾸므로 실행 컨테이너의 `npm test`는 `vitest: not found`로 종료했다. 동일 현재 소스의 호스트 의존성으로 133개 테스트를 재실행해 통과했다. 이 실행 방식 차이는 제품 결함으로 판정하지 않는다.

## 배치 2에서 발견하고 수정한 결함

1. 완료된 학습 콘텐츠 목록에 `문제 풀이 필요`가 계속 표시되고, 완료 화면이 다시 완료를 요구했다. 콘텐츠 상태가 `COMPLETED`면 완료 안내를 보이도록 바꾸고 실제 HTTPS 화면에서 확인했다.
2. 시험의 제출 원장은 `SUBMITTED`로 보존하면서 현재 채점 run은 `SUCCEEDED`가 될 수 있다. 시험 목록과 새 응시 시작 조건이 원장 상태만 읽어 이미 채점 완료된 응시를 활성으로 간주했다. 두 조회에서 현재 성공 채점 run을 제외하도록 고치고 PostgreSQL HTTP 통합 테스트를 추가했다.
3. 활성 표시를 바로잡은 뒤 과거 시험 결과로 돌아갈 링크가 없었다. 기존 시험 응시 이력 API에서 완료 응시를 조회해 `결과 보기`를 제공했다. 실제 HTTPS 목록에서 완료 응시가 활성에서 제외되고 결과 상세로 이동함을 확인했다.

기존 개발 계정의 학습·시험 결과를 읽어 검증했으며 새 답안 제출이나 과정 생성은 하지 않았다.

## 다음 단계 — 배치 2 통합 사용자 흐름

1. 별도 새 사용자에서 온보딩 → 과정 배정 → 학습·문제·시험 제출 → 진척·알림의 실제 저장·새로고침·재접속을 검증한다. 이번 단계는 기존 계정의 읽기 흐름과 시험 결과 재방문까지 확인했다.
2. 1280×900, 375×812와 light 모드의 가로 overflow, 키보드 포커스, console, 빈·오류·충돌 상태를 확인한다. 이번에는 390×844 dark 화면의 변경 영역만 확인했다.
3. 재현되는 결함을 관련 이슈 #8 범위에서 최소 수정하고, 해당 영역 회귀를 다시 실행한다. 공급자 실동작과 운영 인수는 배치 3으로 별도 판정한다.

## 접속 정보

- 대표 화면(HTTPS 로그인 후 실제 검증): [시험 현황](https://hocheon.tail4ddad6.ts.net/tests). 같은 Tailscale 네트워크에서 접속한다.
- 이번에 변경한 화면: [완료한 학습](https://hocheon.tail4ddad6.ts.net/workspace/learning/7) · [시험 결과](https://hocheon.tail4ddad6.ts.net/tests/attempts/3/result). 기존 허용 계정에서 실제 검증한 기록이며 다른 계정에서는 해당 기록이 없을 수 있다.
- 로컬 화면: [학습 공간](http://localhost:8080/workspace) · [시험 현황](http://localhost:8080/tests). 로컬 HTTP 화면은 응답을 확인했으며 보호 기능 이용에는 허용 Google 계정 로그인이 필요하다.
- [학습](http://localhost:8080/learning) · [문제 풀이](http://localhost:8080/practice/1) · [시험](http://localhost:8080/tests) · [진척도](http://localhost:8080/progress) · [알림](http://localhost:8080/notifications) · [연동 설정](http://localhost:8080/settings/integrations)
- 현재 실행 중, 로컬 포트 8080·Tailscale Serve HTTPS 포트 443. 보호 화면은 Google 허용 계정 로그인 필요. 위 HTTPS 주소에서 로그인과 변경 화면을 실제 검증했다.
- 재시작: 저장소 루트에서 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up`. Docker Desktop과 추적 제외 `.env`가 선행 조건이다.
