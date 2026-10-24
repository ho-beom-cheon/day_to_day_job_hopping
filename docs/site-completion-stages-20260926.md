# 사이트 완성도 후속 작업 — 2026-09-26

관련 이슈: [#8 기능: OpenAPI 전 범위 및 외부 연동 완성](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8).

## 1. 로그인 진입과 제공 범위 — 구현·검증 완료

### 분석

- `/login`이 `error` 유무와 관계없이 실패 화면을 렌더링했다.
- 첫 화면은 6개월 학습을 제공한다고 안내하지만 현재 발행 데이터는 6학습일 분량이다.

### 변경

- `frontend/src/app/login/page.tsx`: 오류 값이 없거나 비어 있으면 일반 시작 화면을 보여준다. 반복 query 값도 정상 처리하며 오류 원문을 출력하지 않는다.
- `frontend/src/features/session/login-landing.tsx`: 첫 화면과 일반 로그인 화면에서 실제 제공 범위를 안내한다. 3단계 발행 후에는 과정별 기간·학습일·필수 세션을 로그인 후 확인하도록 문구를 갱신했다. 좁은 화면에서 한국어 단어가 음절 단위로 잘리지 않도록 했다.
- 실제 오류 `LOGIN_FAILED`와 `ACCOUNT_NOT_ALLOWED`는 기존 실패 안내를 유지한다.

### 검증

- 호스트 typecheck·lint PASS.
- Docker 운영 빌드에서 typecheck·lint·Vitest 151/151·Next production build PASS.
- 브라우저에서 `/login`, 빈 error, 일반 인증 실패, 미허용 계정 안내를 확인했다.
- 1280×900·390×844의 다크 화면에서 가로 넘침 없음. DOM `scrollWidth`는 각각 1265·375였으며 viewport는 1280·390이다. 확인 구간 console error/warn 0.
- 전체 라이트 모드·키보드·신규 사용자 완주 검증을 완료했다고 주장하지 않는다.

## 2. 공개 운영 이미지와 시작 경로 — 적용·검증 완료

### 분석

- 공개 Funnel은 작동 중이나 프런트엔드가 `NODE_ENV=development`, `npm run dev -- --webpack`, 소스 bind mount 상태였다.
- `scripts/ide.ps1 -Action Up`이 항상 개발 Compose만 실행하므로 공개 설정을 다시 개발 서버로 바꿀 수 있었다.

### 변경

- `scripts/ide.ps1`: `Auto`, `Development`, `Public` 모드를 추가했다. 기본 Auto는 프로세스 환경변수와 로컬 `.env`의 HTTPS origin/host를 확인하여 공개 overlay를 선택한다.
- Public 기동은 HTTPS origin/host 일치 여부를 검사하고 `compose.public.yaml`을 적용한다.
- 컨테이너 교체 후 Nginx upstream 주소를 reload하고 runtime smoke를 실행한다.
- `docs/public-internet-access.md`에 시작 모드와 실행 명령을 반영했다.
- 실제 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up`을 실행하여 Auto→Public 선택과 기동을 확인했다.

### 검증

- 프런트엔드 명령 `["node","server.js"]`, `NODE_ENV=production`, Mounts `[]`.
- 네 서비스 모두 healthy, runtime smoke PASS.
- 공개 `/`, `/login`, `/push-worker.js` HTTP 200. 비로그인 `/api/v1/users/me` 401, `/actuator/health` 404.
- HTTPS 브라우저 새로고침 후 수정된 문구와 운영 정적 파일을 확인했고 개발 도구 표시가 사라졌다.
- 허용된 기존 Google 계정으로 OAuth 로그인 후 홈·로드맵에서 기존 학습 기록과 완료 상태 조회 성공.
- 사용자 DB volume을 삭제하거나 초기화하지 않았다. 기존 `dev,dev-seed` Backend 프로필을 보존한 개인용 공개 실행이다. 별도 운영 DB·OCI 배포 완료를 뜻하지 않는다.

## 3. 4주 과정 — 구현·검증·운영 발행 완료

사용자가 이슈 초안 등록과 구현을 승인하여 [#9](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/9)를 만들었다. 원고·검수 근거는 [과정 README](../content/java-four-week-v1/README.md), [기술 검수 기록](../content/java-four-week-v1/review.md), [계약 정렬](contracts/four-week-course-alignment.md)에 있다.

- 독립 1개월·4주·20학습일·20필수 세션. 하루 본문·Java 실행 예제·실습·수락 기준·확인 문제 10개. 확인 문제 200개와 평가 은행 40개, 시험 26개를 작성했다.
- 기존 6개월 고정을 1~6개월 계약으로 확장했고 기존 6개월의 저장 데이터·목표일을 유지했다. 온보딩과 로드맵이 신규 4주 과정을 미완성 6개월 자료로 오인하지 않는다.
- `catalog.ps1`과 카탈로그 운영 CLI에서 DRAFT 적재 → digest/DB 구성 검증 → 검수 근거 기록 및 발행을 수행한다. V19는 원고 이력만 추가하며 자동 seed·사용자 재배정을 하지 않는다.
- 같은 원고 재적재는 재사용, 변경 원고는 새 버전 요구, 불완전 참조·정답·일수 및 DB 변조는 거부한다. 적재와 발행 실패는 각각 트랜잭션 rollback한다.
- 기존 공개 DB에 templateId `2`, `JAVA-CORE-4W:2026.09.1`을 **17:56:28 KST에 PUBLISHED**했다. 원고 SHA-256은 검수 기록에 있다. 기존 templateId `1`도 PUBLISHED로 유지된다.
- 사용자 학습 관련 7개 테이블(user_curriculum, learning_day, learning_day_item, assigned_session_detail, assigned_content, problem_attempt, test_attempt)의 배포 전후 행 수·행 내용 digest가 일치한다. 기존 과정과 답안·시험 기록은 보존됐다.

## 4. 통합 QA — 핵심 흐름 검증·발견 결함 수정 완료

### 발견과 수정

- 시험이 완료율 분모에 들어가 20세션을 46개로 집계했다. 필수 학습 세션 기준으로 전체·일간·카테고리·캘린더·완료 상태를 정렬했다.
- 세션 완료 때 입력한 시간이 통계에 잡히지 않았다. 배정 상세의 실제 시간을 배정 학습일에 반영하고 이전 timer 자료와 중복 계산하지 않는다.
- 미래 미완료 학습일이 현재 연속 학습일을 0으로 만들었다. 미래를 제외하고 오늘의 미완료는 이전 학습일까지의 연속 기록을 유지한다.
- 과정이 있는 사용자에게 첫 과정 선택 안내와 가상의 경력 단계를 보여줬다. 실제 과정·휴식일·단계·완료 기록으로 교체했다.
- 학습 본문이 마크다운 원문으로 보였다. HTML을 실행하지 않는 본문 렌더러와 코드 내부 스크롤을 추가했다. 모바일 390px에서 전체 화면이 늘어나는 grid 최소 폭도 수정했다.
- 첫 제출 직후 재풀이 2회차 배너가 보이는 문제와 영문 난이도 표시를 수정했다.
- 시험 목록의 첫 20개만 읽어 최종 시험이 숨겨졌다. 전체 페이지를 읽고 일부 페이지 실패를 정상 목록으로 처리하지 않는다.
- 제출은 202로 성공했지만 DB 상태 QUEUED를 그대로 응답해 프런트엔드가 실패로 해석했다. 공개 계약 WAITING으로 정렬하여 제출 → 대기 → 결과 자동 전환을 복구했다.
- 학습·시험·통계가 같은 Clock을 사용한다. 월간 집계의 잘못된 조인 컬럼도 수정했다.

### 검증 근거

- 최종 백엔드 단위 **74/74**, PostgreSQL 통합 **68/68 PASS**. Java 예제 **20/20 PASS**.
- 실제 HTTP·보안·세션·CSRF·DB·채점 worker를 거쳐 **20학습일/200문항/26시험 완주**, 과정 COMPLETED, 완료율 100%, 1,800분, 연속 20일 확인. 테스트 Clock을 진행시킨 자동 검증이며 4주간 실사용 실측은 아니다.
- 프런트엔드 typecheck·lint·Vitest **156/156**·Next production build PASS.
- 별도 QA DB의 브라우저: 온보딩→첫날 학습 시작→10문항 연속 제출→오답 메모/재풀이→콘텐츠/90분 세션 완료→시험 답안 저장→제출→실제 채점 100점→재접속 확인. 홈과 로드맵 1/20·5%, 통계 90분·연속 1일 일치.
- 브라우저 QA 중 서버 교체로 한 번 502가 발생했다. 최신 정보 조회 후 답안 재저장 성공. 별도로 발견한 QUEUED 응답 결함 수정 후 재응시에서 대기→100점 결과 자동 전환을 확인했다.
- 학습 본문 390×844 라이트·다크와 1280×900 라이트에서 페이지 가로 넘침 없음. 코드 영역만 내부 스크롤, 키보드 포커스 및 방향키 확인. 전체 화면의 스크린리더 감사와 모든 기종 검증은 미완료.
- 공개 기존 Google 로그인 후 홈 2/6세션·75분 기록 확인. runtime smoke PASS, `/login` 200, 비로그인 `/api/v1/users/me` 401, `/actuator/health` 404. frontend는 production `node server.js`, 소스 mount 없음.
- 후속 점검에서 홈의 최근 활동 카드가 오늘 세션만 읽어 이전 학습이 있는 사용자에게도 첫 활동 안내를 보여주는 결함을 수정했다. 통합 `GET /api/v1/dashboard`가 같은 DB 스냅샷에서 현재 과정의 진행·완료 세션 최근 4건을 반환하며 홈은 날짜·상태·본문 링크를 표시한다. 미시작 세션과 다른 사용자의 활동은 포함하지 않는다.
- 2026-09-26 18:17~18:31 KST에 QA 모바일 390×844에서 핵심 10화면(`/workspace`, `/learning`, `/practice`, `/tests`, `/review`, `/progress`, `/calendar`, `/notifications`, `/assistant`, `/settings`)과 로드맵, 상세 5화면(학습·문제·시험 응시·시험 결과·오답), 연동 설정을 점검했다. 렌더 후 확인한 화면에 페이지 가로 넘침·이름 없는 버튼·브라우저 error/warn이 없었다. 정적 DOM 점검이며 스크린리더 사용성·모든 상호작용의 수동 접근성 감사는 아니다.
- 후속 변경 검증: 백엔드 단위 74/74, PostgreSQL 통합 68/68, 프런트엔드 typecheck·lint·Vitest 160/160·production build PASS. 첫 통합 실행은 원고 디렉터리 마운트 누락으로 3개 시나리오가 실패했고 읽기 전용 마운트 후 재실행에서 모두 통과했다. QA 홈에는 완료한 첫날 활동이, 공개 HTTPS 기존 사용자 홈에는 실제 이전 활동 4건이 표시됐다. 공개 재기동 후 네 컨테이너 healthy 및 runtime smoke PASS.

로그는 추적 제외 `output/course-postgres-it-final-20260926.log`, `output/course-public-frontend-final-20260926.log`, `output/course-examples-20260926.log`, `output/course-public-{import,validate,publish}-20260926.log`에 있다. 초기 실패와 발견·수정한 문제를 통과 결과와 구분했다.

## 5. 외부 연동·운영 — 정기 알림 부분 구현, 나머지 검증 진행 중

2026-09-26 18:00 KST 공개 `/settings/integrations`를 읽기 전용으로 확인했다. 현재 상태를 아래처럼 구분한다.

| 항목 | 확인한 상태 | 남은 작업 |
| --- | --- | --- |
| OpenAI | 현재 공개 계정의 일간 피드백 작업이 `SUCCEEDED`, 공급자 `OPENAI`, 입력 149·출력 138토큰으로 확인됨 | 계정 측 비용 상한·잔액 소진 오류 경로 재검증 |
| Slack | 연결 및 알림 켜짐, 기존 전송 이력 4건 표시 | 실제 수신 결과·실패 재시도 확인. 이번 단계에서 새 메시지는 발송하지 않음 |
| Notion | OAuth 연결 있음. `Daily Leap 기록` 데이터베이스의 6종 대상 스키마를 앱에서 검증·저장했고 대상 revision 1. 사용자 승인 후 현재 과정 15건 동기화 성공: 생성 15, 실패 0. Notion 조회에서도 15건 확인 | 중복 실행 시 건너뛰기·원본 수정 시 갱신·실패 재시도 검증 |
| Web Push | VAPID/구독 암호화 키를 공개 서버에 설정, Chrome 브라우저 구독 1대 확인. 전체 전송은 꺼진 상태 | 사용자 동의 후 실제 수신·실패 재시도 검증 |
| 정기 알림 | 명시적 동의 후 한국 시간 기준 학습·시험·연속 학습·주간 기록 알림 자동 생성. 주간 알림은 발송일 포함 최근 7일의 실제 필수 학습 완료/전체·학습 시간·첫 풀이 정답/전체를 스냅샷으로 표시한다. 기존 사용자는 모두 기본 비동의 | 실제 사용자 수신 및 외부 채널 전달·재시도 확인 |
| 배포 운영 | 개인 PC Docker+기존 Funnel 실행, 공개 DB custom-format 수동 백업과 격리 복원 실습 통과 | 재부팅 복구, 독립 운영 환경, 모니터링·장애 대응 기준 |
| 콘텐츠 품질 | Codex 기술 검수 및 예제 실행 통과 | 독립적인 사람의 교육 검수·실제 학습 시간 측정 |

2026-09-26 19:59 KST Notion 실제 전송을 검증했다. 수동 동기화 기간은 2026-08-27~2026-09-26, 현재 과정 ID는 1이다. 전송 전 읽기 전용 원본 조회 결과는 완료 학습 2, 제출 문제 8, 제출 시험 1, 오답 3, 주간 리포트 1, 월간 리포트 0건이었다. 사용자에게 전송 범위와 목적지(`Daily Leap 기록`)를 명시하고 승인받은 뒤 동기화 작업 #1을 실행했다. 앱 이력은 성공·생성 15·갱신 0·건너뜀 0·실패 0을 표시했고, Notion 데이터소스 조회도 종류별 `2|8|1|3|1`로 일치했다. 중복 실행·갱신·재시도는 이번에 수행하지 않았다.

2026-09-26 20:21 KST 사용자가 기존 Notion 페이지 본문이 비어 있고 주간 리포트가 `완료 2개 / 전체 9개`만 표시된다고 지적했다. 원인은 동기화 스냅샷에 제목·요약·날짜만 있고 공급자가 페이지 속성만 쓴 것이었다. 학습 본문, 제출한 문제·답과 채점 완료 후 해설, 시험 문항·점수, 오답 메모, 날짜별 주간 항목, 월간 지표를 `details` 스냅샷으로 만들고 Notion 페이지의 앱 전용 본문 블록에 생성·갱신하도록 수정했다. 사용자가 직접 작성한 다른 블록은 건드리지 않는다. 채점 대기 중인 문제·시험의 정답과 해설은 전송하지 않는다. 백엔드 단위 테스트 83개, 프런트 테스트 166개와 typecheck·lint·production build가 통과했고, 공개 DB 읽기 전용 SQL로 6종 원본 건수 `2|8|1|3|1|0`, 주간 상세 9항목, 제출 시험 상세 1문항을 확인했다. 공개 앱 코드는 재배포했으나 기존 15개 Notion 페이지의 상세 내용 재전송은 별도 승인 대기 중이다.

2026-09-26 21:32 KST 사용자가 기존 15개 페이지 내용 채우기를 승인했다. 공개 앱의 수동 동기화 작업 #2를 같은 기간(2026-08-27~2026-09-26), 같은 6종 대상으로 실행했고, 이력은 성공·생성 0·갱신 15·건너뜀 0·실패 0을 표시했다. Notion 데이터소스에서 여전히 15개 페이지를 확인했고, 주간 리포트 페이지 본문에서 날짜별 일정 9항목·완료 학습·다음 학습을 직접 확인했다. 학습 결과, 오답, 시험 페이지 본문도 각각 학습 자료, 문제·내 답·정답·메모·피드백, 문항·점수·해설을 확인했다.

2026-09-26 21:55 KST Notion 답안 형식과 본문 구성 후속 수정: 제출 순번(예: `1`)과 콘텐츠 정답 ID(예: `A`)를 모두 `1번 · 보기 문구`로 변환하고, 문제·시험·오답 페이지에 전체 보기 목록을 추가했다. Notion 본문은 앱 전용 단일 문단 대신 종류별 아이콘이 있는 콜아웃 안에 날짜·요약, 문제 인용, 보기 목록, 내 답·정답·채점·점수 카드, 해설·메모·주간 내역을 구분한 블록으로 구성한다. 기존 앱 블록만 휴지통으로 이동하고 사용자의 다른 블록은 보존한다. 시험 종류·제출 방식·오답 상태를 한국어로, 점수는 `10/10점`처럼 표시한다. 백엔드 전체 테스트 87/87과 공개 스택 헬스체크가 통과했다. 먼저 문제 1건 시험 동기화 #3을 성공·갱신 1로 확인하고, 전체 동기화 #4는 성공·갱신 14·건너뜀 1·실패 0, 최종 형식 #5는 성공·갱신 15·실패 0을 확인했다. Notion 데이터소스는 15건 그대로이며 문제·시험·오답·주간·학습 페이지 본문에서 새 블록 구조와 동일 형식 답안을 확인했다. Notion 브라우저 직접 시각 검증은 데스크톱 앱 리디렉션 때문에 수행하지 못했고 API가 반환한 실제 블록 구조를 검증했다.

2026-09-26 19:10~19:12 KST에 PowerShell 바이너리 리디렉션을 사용하지 않는 백업·복원 스크립트를 추가하고 QA DB와 공개 DB에서 각각 실행했다. 두 백업 모두 custom-format 목차와 복사본 SHA-256이 일치했고, 외부 포트 없는 별도 PostgreSQL에서 `pg_restore --exit-on-error`가 통과했다. 공개 복원본과 원본의 Flyway 성공 이력·사용자·과정·학습 항목 수는 `21|1|1|10`으로 일치했다. 이 시점에 공개 DB에는 다른 학습 작업의 V21이 포함되어 있다. 테스트용 컨테이너와 volume은 모두 제거했고, 실제 백업 archive와 manifest만 Git 추적 제외 `backups/`에 남겼다. 재부팅 복구·자동 주기·원격 보관·실제 장애 시 서비스 교체는 검증하지 않았다.

후속 구현에서 과정 배정·완주 알림에 박혀 있던 “6개월” 표현을 기간 중립 문구로 바꿨다. 4주 과정의 알림에서 잘못된 기간을 말하지 않는다. 백엔드 단위 테스트 77개 PASS이며 실제 외부 메시지 발송 검증은 수행하지 않았다.

정기 알림의 V20 마이그레이션은 기존 저장 설정을 그대로 보존하며 모든 계정의 새 수신 동의를 `false`로 시작한다. `/settings`에서 별도 체크박스를 켜고 저장해야 스케줄이 활성화된다. 서버는 한국 시간으로 학습일의 남은 필수 항목, 미응시 시험의 시작 전 시각, 직전 학습일 완주 여부, 설정한 주간 요일을 확인한다. 예정 시각부터 5분 이내에만 생성하고 동의 전의 지난 일정은 소급하지 않으며 `(사용자, 유형, 날짜, 시험)` 키로 중복을 막는다. outbox 발행 직전에 원래 예정 시각과 현재 동의 시각을 다시 비교해 철회 후 재동의가 오래된 대기 이벤트를 되살리지 않게 한다. 철회 시 대기 중인 정기 알림 채널 전송을 취소하며 이미 생성된 앱 알림 이력은 보존한다.

검증: 백엔드 단위 **78/78**, PostgreSQL 전체 통합 **69/69**, 마지막 재동의 보강 후 관련 통합 **1/1**, 프런트 typecheck·lint·Vitest **162/162**·production build PASS. 통합 DB에서 동의 전·지난 일정·중복·철회 및 재동의 후 오래된 대기 이벤트 차단을 확인했다. QA V20 적용, `/settings`에서 기존 세부 옵션은 유지되지만 정기 알림 수신은 꺼진 상태, 브라우저 오류/경고 0건. 공개 DB V20 적용과 동의자 0명·신규 예약 이벤트 0건을 확인했다. 외부 Slack/Push 실제 수신은 발생시키지 않았고 검증하지 않았다.

외부 키가 있다는 것, 연결되었다는 것, 실제 수신·동기화·AI 생성 성공은 각각 다른 완료 조건이다. 전체 사이트가 상용 운영 수준으로 완성됐다고 평가하지 않는다.

2026-09-26 후속 점검에서 공개 `/assistant`에 학습 기록이 없는 휴식일의 피드백이 이미 생성돼 있음을 확인했다. 공개 DB의 해당 일간 피드백 작업은 `SUCCEEDED`·`OPENAI`이며 사용 토큰이 기록됐다. 이는 현재 키의 실제 생성 성공 근거지만 학습 내용이 없는 피드백의 품질 문제였다. 서버는 시작 또는 완료한 당일 학습 항목이 없으면 새 일간 피드백 작업을 만들지 않도록 바꾸고, 화면은 휴식일·미시작 학습일에 버튼을 비활성화하고 이유를 안내한다. 기존 작업과 사용자 학습 기록은 보존했다. PostgreSQL 대상 통합 5/5, 백엔드 단위 78/78, 프런트 typecheck·lint·Vitest 166/166·production build PASS. 공개 `/assistant`에서 휴식일 버튼 비활성 및 안내 문구를 확인했다.

같은 단계에서 `scripts/prepare-web-push-keys.ps1`로 공개 `.env`에 P-256 VAPID 키 쌍과 별도 32바이트 구독 암호화 키를 생성했다. 기존 키를 보존하고 부분 설정을 거부하는 동작, 생성 키의 길이·서명 검증을 확인했다. 공개 Backend를 재생성한 뒤 `/settings/integrations`에서 **이 브라우저 연결** 버튼이 활성화됐고 연결된 브라우저는 0대로 표시됐다. 사용자 기기 권한·구독·백그라운드 수신은 실행하지 않았다. 공개 네 컨테이너 healthy, runtime smoke PASS.

2026-09-26 22:17 KST [이슈 #11](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/11) 범위로 정기 주간 알림 본문을 구현했다. 설정한 발송일을 포함한 최근 7일의 현재 활성 과정 통계를 기존 `ProgressService.periodProgress`로 계산해 outbox payload에 고정한다. 앱 알림과 연결된 Slack·Web Push 전달은 같은 알림 본문을 사용한다. 학습 0건은 빈 상태를 표시하고, 첫 풀이가 없으면 정답률을 꾸며 쓰지 않는다. 예약 worker의 기존 시험 알림 SQL에서 누락된 바인딩 하나도 수정했다. Backend 단위 **88/88**, `ScheduledReminderIT` PostgreSQL 통합 **2/2**, 공개 백엔드 재생성·헬스체크·runtime smoke PASS. 전체 PostgreSQL IT 실행은 이 변경과 무관한 4주 원고 마운트 누락 및 기존 V21 스키마 기대값 불일치로 실패했으므로 통과로 기록하지 않는다. 실제 사용자 알림을 강제로 발송하지 않았으며 외부 채널 수신은 별도 검증 대상이다.

## 접속과 재시작

[대표: 정기 알림 QA 설정](http://localhost:18081/settings) · [공개 로그인](https://hocheon.tail4ddad6.ts.net/login) · [공개 설정](https://hocheon.tail4ddad6.ts.net/settings)

2026-09-26 18:56~19:03 KST 새 빌드와 마지막 재동의 보강을 두 환경에 적용했고 모두 실행 중이다. QA 설정 화면·API와 공개 HTTPS 로그인 화면을 직접 확인했다. 정기 알림 동의값은 QA 계정과 공개 DB의 모든 기존 계정에서 꺼져 있다. 이전 단계의 공개 Google 계정 홈·핵심 흐름 검증은 위 4단계 근거를 참조한다.

| 확인 화면 | 운영 주소 | 새 과정 QA 주소 |
| --- | --- | --- |
| 첫 화면·로그인 | [첫 화면](https://hocheon.tail4ddad6.ts.net/) · [로그인](https://hocheon.tail4ddad6.ts.net/login) | [로그인](http://localhost:18081/login) |
| 과정 선택 | [온보딩](https://hocheon.tail4ddad6.ts.net/onboarding) | 이미 QA 과정 배정됨. 최초 온보딩은 브라우저로 검증함 |
| 홈 | [홈](https://hocheon.tail4ddad6.ts.net/workspace) | [홈](http://localhost:18081/workspace) |
| 로드맵 | [로드맵](https://hocheon.tail4ddad6.ts.net/roadmap) | [4주 로드맵](http://localhost:18081/roadmap) |
| 학습 본문 | 기존 계정의 로드맵에서 배정된 세션 선택 | [1일차 본문](http://localhost:18081/workspace/learning/1) |
| 문제 풀이 | [문제 목록](https://hocheon.tail4ddad6.ts.net/practice) | [첫 문제](http://localhost:18081/practice/1) |
| 시험 | [시험](https://hocheon.tail4ddad6.ts.net/tests) | [26시험 목록](http://localhost:18081/tests) · [검증 결과](http://localhost:18081/tests/attempts/2) |
| 진도 | [통계](https://hocheon.tail4ddad6.ts.net/progress) | [통계](http://localhost:18081/progress) |
| 알림 | [알림 센터](https://hocheon.tail4ddad6.ts.net/notifications) | [QA 알림 센터](http://localhost:18081/notifications) |
| 외부 연동 | [연동 관리](https://hocheon.tail4ddad6.ts.net/settings/integrations) | 외부 자격 증명을 넣지 않은 별도 환경 |
| 정기 알림 설정 | [공개 설정](https://hocheon.tail4ddad6.ts.net/settings) (Google 로그인 필요) | [QA 설정](http://localhost:18081/settings) (기본 비동의 확인) |

- 운영: HTTPS 443, 로컬 127.0.0.1:8080. Google 로그인은 HTTPS 주소에서 추적 제외 `.env`의 `GOOGLE_ALLOWED_EMAILS`에 허용된 계정으로 한다. 기존 계정은 기존 배정을 유지한다. 신규 사용자만 과정 선택에서 4주 템플릿을 선택하며 기존 계정의 과정을 이 단계에서 바꾸지 않았다.
- QA: 127.0.0.1:18081 전용이며 외부 공개하지 않았다. 별도 DB의 `과정 검증 사용자`가 자동 적용되므로 비밀번호 입력 없이 새 4주 과정을 확인할 수 있다. 기존 사용자 기록과 무관하다. 개발 인증 우회가 켜진 환경이므로 공개 overlay와 합치지 않는다.
- 직접 검증: QA 핵심 학습 흐름·정기 알림 설정 UI/API·공개 HTTPS 로그인·연동 상태와 DB 발행. 알림 센터 URL은 실제 접속 가능한 화면이지만 새 기간 중립 문구는 이후 과정 배정·완주 이벤트에서만 생성되므로 기존 알림을 소급 변경하지 않는다. 공개 서버 신규 Google 계정으로 새 과정 생성이나 실제 Slack/Push 정기 알림 수신은 수행하지 않았다.

운영 재시작:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public
```

QA 재시작(현재 PC의 이미지·DB·추적 제외 `.env.course-qa.local` 보존 전제):

```powershell
docker build --target build -t daily-career-backend-course-20260926 ./backend
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml up --build -d --wait
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml exec -T nginx nginx -s reload
```

선행 조건: Docker Desktop, 각 환경의 추적 제외 설정 파일. 운영 HTTPS에는 기존 Tailscale proxy도 필요하다. 비밀번호·토큰·키 값은 채팅이나 추적 문서에 기록하지 않는다. 커밋과 PR은 만들지 않았으며 기존의 다른 변경도 보존했다.
