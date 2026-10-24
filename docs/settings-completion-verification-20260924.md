# 설정·온보딩 연결 완성도 개선

검증일: 2026-09-24 07:41 KST

관련 이슈: [#8 기능: OpenAPI 전 범위 및 외부 연동 완성](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8)

## 결과

서버에 구현된 사용자 기본 설정이 화면에 연결되지 않았고, 온보딩은 학습 시간·휴식일을 고정값으로 시작했다. 설정의 Notion 관리 버튼 역시 실제 관리 화면이 구현된 후에도 비활성 상태였다. 이번 변경으로 기본값 저장 → 새 과정 생성 화면에서 불러오기와 외부 연동 관리 화면 진입을 연결했다.

| 항목 | 상태 | 내용 |
|---|---|---|
| 사용자 학습 기본값 | 완료 | 일일 15~480분, 휴식일 0~6개, 평일·주말 시작 시각을 GET/PATCH `/api/v1/settings`에 연결 |
| 저장 충돌 | 완료 | 수정 시작 시 revision 보존, If-Match 저장, 412 및 백그라운드 조회 충돌 안내, 명시적인 최신 값 재조회 |
| 실패 복구 | 완료 | 저장·조회 실패 시 입력 유지, 유효하지 않은 값과 중복 저장 차단 |
| 온보딩 기본값 | 구현·컴포넌트 검증 완료 | 저장된 학습 정책 사용, 조회 실패 시 재시도, 사용자가 수정한 값은 재조회 후에도 유지 |
| Notion·Slack 진입 | 완료 | `/settings/integrations` 직접 링크 연결, Slack 저장 실패 표시 |
| 알림 설정 보완 | 완료 | 저장 중 중복 조작 방지, 서버에 저장된 주간 리포트 요일 표시 |

`SETTING-001/002` 계약에 따라 기본 학습 정책은 다음 과정의 기본값이며 현재 과정 일정은 자동 변경하지 않는다. 평일·주말 시각은 사용자 기본 설정으로 저장하고, 알림 시각과는 별도임을 화면에서 안내한다. 현재 과정의 일정 변경은 기존 캘린더 preview/confirm 흐름을 사용한다.

## 변경 파일

- `frontend/src/features/settings/contract.ts`, `queries.ts`: 사용자 기본 설정 DTO 검증과 API 연결
- `frontend/src/features/settings/study-preferences-form.tsx`: 저장·충돌·실패 복구 입력 화면
- `frontend/src/features/settings/settings-screen.tsx`: 학습 기본값 편집, 관리 링크, 주간 리포트 요일
- `frontend/src/features/onboarding/onboarding-screen.tsx`: 서버 기본값과 사용자 편집값 연결
- `frontend/src/features/integrations/integration-settings-screen.tsx`: 저장 중 조작 차단, 주간 리포트 요일
- 설정 계약·폼 및 온보딩 테스트 파일 3개: 회귀 테스트 19개 추가

Backend 소스 및 migration은 이번 작업에서 변경하지 않았다. 시작 시 존재한 다른 작업의 미커밋 변경을 보존했으며 커밋·푸시·PR은 수행하지 않았다.

## 자동 검증

| 명령 | 결과 |
|---|---|
| `docker compose exec -T frontend npm test` | 31파일, 129/129 PASS |
| `docker compose run --rm --no-deps frontend npm run typecheck` | PASS |
| `docker compose exec -T frontend npm run lint` | PASS |
| `docker compose run --rm --no-deps -e NODE_ENV=production frontend npm run build` | PASS, 전체 route 빌드 |
| `docker compose exec -T frontend node scripts/smoke.mjs` | PASS, DB health·Frontend·API·management 비노출 |
| `docker compose ps` | PostgreSQL·Backend·Frontend·Nginx 4서비스 healthy |

처음 실행한 개발 서버 컨테이너 내 typecheck는 Next dev와 생성 타입 파일이 충돌해 실패했다. 독립 일회용 컨테이너에서 재검증해 통과했다. 개발 이미지의 `NODE_ENV=development`를 그대로 사용한 production build 역시 실패했으며, 위 명령처럼 `NODE_ENV=production`을 지정한 독립 빌드는 통과했다. 실행 중인 서버나 데이터 volume을 중지·삭제하지 않았다.

Backend 전체 테스트는 이번 작업에서 재실행하지 않았다. 기존 Backend 테스트 기록을 이번 실행 결과로 합산하지 않는다.

## 실제 브라우저·API 검증

- 설정에서 60분 → 75분 저장, 성공 안내 및 새로고침 후 75분 유지 확인.
- 두 화면에서 설정을 열고 한 화면의 미저장 90분 입력을 유지한 채 다른 화면에서 60분 저장.
- 오래된 화면의 저장 요청은 충돌 안내로 종료되고 90분 입력 보존·저장 버튼 비활성 확인.
- `최신 값 불러오기`로 원래 60분 복원. GET API는 200, ETag `"4"`, revision 4, 학습 시간 60분·휴식일 2일 확인.
- 변경하지 않은 평일 19:30·주말 10:00·토/일 휴식 유지. 라이트/다크 검증 후 기존 다크모드로 복원.
- 1280×900, 390×844, 375×812에서 가로 overflow 없음. 라이트·다크 표시 및 숫자 입력 → 시각 입력 키보드 포커스 확인.
- Notion 관리 링크를 눌러 실제 `/settings/integrations` 화면 진입 확인. 외부 미설정 상태와 비활성 동작 유지.
- 설정 브라우저 console error/warning 0건.

새 사용자 온보딩의 실제 과정 생성은 수행하지 않았다. 기존 계정에 활성 과정이 있어 `/onboarding`은 학습 공간으로 이동하는 조건이며, 사용자 과정을 추가·변경하지 않았다. 저장 기본값의 렌더링·요청 반영·재조회·조회 실패는 컴포넌트 테스트로 검증했다.

## 접속

- 대표 화면: [학습 기본값 설정](http://localhost:8080/settings#learning)
- 연동 관리: [알림 및 외부 연동](http://localhost:8080/settings/integrations)
- 온보딩: [새 과정 설정](http://localhost:8080/onboarding) — 활성 과정이 없는 사용자용. 현재 계정은 학습 공간으로 이동한다.
- API: [사용자 기본 설정](http://localhost:8080/api/v1/settings)
- 현재 실행 중, 공개 포트 8080, `DEV_AUTH_BYPASS=true` 개발 모드라 별도 로그인 없이 현재 개발 사용자로 확인할 수 있다.
- 재시작: 저장소 루트에서 `./scripts/ide.ps1 -Action Up`. Docker Desktop과 추적 제외 `.env`가 필요하다.
- 외부 기기 접속은 구성하지 않았다. 실제 외부 OAuth·AI·Notion·Slack·Push 공급자 및 운영 HTTPS 인수는 이번 검증에 포함하지 않는다.

## 릴리스 노트 후보

- 학습 기본값을 저장하고 새 과정 설정에 적용할 수 있습니다.
- 다른 화면에서 바뀐 설정을 덮어쓰지 않도록 충돌 안내와 새로 불러오기를 제공합니다.
- 설정에서 Notion 연결 관리와 Slack 전송 이력으로 이동할 수 있습니다.
