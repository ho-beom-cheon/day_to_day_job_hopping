# 작업 5 인증·회원 구현 및 검증

관련 [이슈 #4](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/4). **구현·자동 검증·개발 서버 적용 및 Google 실계정 검증을 마쳐 작업 5를 완료**했다.

## 사용할 수 있는 기능

- 허용된 Google 계정만 최초 연결하고 이후 issuer+subject로 로그인한다. 검증되지 않은 이메일, 미허용 이메일, 다른 subject의 동일 이메일 연결, 정지 계정은 거절한다.
- 로그인 성공 시 세션 ID와 CSRF를 교체한다. Google 액세스/ID 토큰을 세션에 보관하지 않고 내부 사용자 ID만 남긴다.
- `GET /api/v1/auth/csrf`, `GET /api/v1/users/me`, `PATCH /api/v1/users/me`, `POST /api/v1/auth/logout`을 공통 응답에 연결했다. 닉네임만 수정하며 ETag/If-Match를 사용한다. 같은 값은 수정 시각/revision을 유지한다.
- 로그아웃은 현재 세션을 무효화하고 동일 이름·Path의 쿠키를 지운다. 재조회/재로그아웃은 401이다. Google 전체 로그아웃은 수행하지 않는다.
- Nginx 로그인 시작/콜백 라우팅과 인증 쿼리 비노출 로그를 적용했다. 공급자 주소와 성공/실패 목적지를 고정했다.

세부 정책과 DB/API 차이는 [채택 기준](contracts/auth-stage5-alignment.md), 사용 설정은 [Google 로컬 설정](google-login-local-setup.md)을 따른다. 로그인·실패·온보딩 화면 자체는 작업 16 범위이며 현재 홈은 기존 준비 화면이다.

## 검증 결과

| 검증 | 실제 결과 |
|---|---|
| `docker build --target build -t daily-career-backend-check ./backend` | PASS, 일반·계약 테스트 38개 및 실행 JAR |
| `docker run --rm --name daily-career-postgres-it-stage5 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify` | PASS, 일반 38개 + PostgreSQL IT 30개 = 68개, 실패/오류/skip 모두 0 |
| AuthenticationIT 15개 | 서명된 모의 공급자의 code 교환/PKCE, 정상 로그인, 잘못된 nonce/issuer/audience/만료/서명/verified/허용 계정 거절, state 세션 결합/10분 만료/재사용/동시 소비, 닉네임 동시 수정·무변경·검증, 사용자 격리, 정지 계정, 로그아웃 |
| 세션 보존 검사 | 로그인 전후 ID 다름, 로그인 전 CSRF 거부, DB 세션에 AppPrincipal 저장·OidcIdToken/DefaultOidcUser/access token 없음, 로그아웃 후 세션 행 삭제 |
| Cookie 단위 검사 2개 | HTTPS용 __Host-CAREER_SESSION 및 로컬 CAREER_SESSION 발급·삭제의 Path=/, HttpOnly, SameSite=Lax, Domain 미설정·Secure 모드 |
| `docker compose config --quiet` | PASS |
| Backend build/up 및 Nginx 재생성 | PASS, 4서비스 running/healthy |
| `python .tools/stage5/verify-local.py` | 실제 Nginx 경유 익명 CSRF 200/no-store, 내 정보/로그아웃 401, 잘못된 콜백 302. 인증 설정 전 실패 경로 및 설정 후 Google 인증 페이지로 302 확인 |
| `docker compose exec -T frontend node scripts/smoke.mjs` | PASS, 실제 DB/Backend/Frontend/Nginx 연결 및 관리 경로 외부 차단 |
| Nginx 로그 | 콜백 경로/상태 1건 확인, 테스트 code/state 원문 매치 0건 |
| Google 실계정 | **PASS**, Chrome에서 Google 동의 후 홈 복귀. 같은 브라우저 세션으로 내 정보 200·CSRF 200·닉네임 무변경 PATCH 200·로그아웃 200·로그아웃 후 내 정보 401 확인 |

로컬 상세 빌드/IT 로그는 추적 제외된 `.tools/stage5/`에 있다. 기존 500 응답 계약 테스트의 의도된 ERROR 로그는 테스트 실패가 아니다.

## DB 적용 및 보존

V1–V3 체크섬은 각각 `1873624569`, `-1313458866`, `1647492594`로 유지했다. 새 V4 체크섬은 `-803042091`이다. 개발 DB cluster ID `7687576957880160290`도 유지했다.

V4는 app_user의 nullable HTTPS 프로필 URL과 일회성 state hash/생성·만료 시각을 추가한다. 업무 스키마는 **48테이블·571컬럼·107FK·178CHECK·248인덱스**, 컬럼 주석 571개다. [실제 catalog](audit/database-stage5-catalog-20260920.json)를 남겼다.

신규 설치, V1 Session 데이터 보존 업그레이드, V3의 nullable email/긴 표시명 기존 사용자 보존, 새 연결 재실행 0 migrations를 통합 테스트했다. 개발 DB/volume을 삭제하지 않았다.

## Google 실계정 인수 검증

사용자의 프로젝트 생성·정책 동의·OAuth 생성 및 로컬 저장 승인에 따라 프로젝트 `Daily Career` (`daily-career-509214`)와 웹 클라이언트 `Daily Career Local`을 설정했다. 앱 이름은 `데일리 이직`, 대상은 외부/테스트이며 승인된 계정 1개를 테스트 사용자와 서버 허용 계정에 등록했다. 결제 연결이나 유료 리소스 생성은 하지 않았다.

callback은 `http://localhost:8080/login/oauth2/code/google`다. 클라이언트 자격증명과 허용 이메일은 추적 제외 `.env`에 저장하고 Backend를 재생성했다. 값은 이 보고서에 기록하지 않는다.

Chrome의 Google 동의 화면에서 이름·프로필 사진·이메일 제공 승인을 받은 뒤 실제 로그인 및 홈 복귀를 확인했다. 임시 로컬 검증 페이지에서 같은 로그인 세션으로 다음을 확인했다.

| 실제 요청 | 결과 |
|---|---|
| `GET /api/v1/users/me` | 200, 사용자 ID는 string, role은 USER |
| `GET /api/v1/auth/csrf` | 200, X-CSRF-TOKEN 발급. no-store는 별도 로컬 HTTP 검사로 확인 |
| `PATCH /api/v1/users/me` | 기존 닉네임과 If-Match/CSRF를 보내 200, revision·updatedAt 유지 |
| `POST /api/v1/auth/logout` | 200, loggedOut=true |
| 로그아웃 후 `GET /api/v1/users/me` | 401 AUTH_REQUIRED |

브라우저 결과는 모두 PASS 및 `실계정 인증 검증 완료`였다. 실제 계정의 닉네임은 변경하지 않았으며 검증 후 앱 세션은 로그아웃 상태다. 임시 페이지는 실행 중인 Frontend 컨테이너에서 제거했다. 계정 정보·토큰·인증 코드는 검증 결과에 기록하지 않았다. 4서비스 healthy 및 연결 smoke도 통과했다.

커밋/푸시/PR은 수행하지 않았으며 기존 미커밋 변경을 보존했다. 작업 6은 아직 시작하지 않았다.
