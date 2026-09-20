# 작업 6 커리큘럼·학습 운영 완료

2026-09-21 / [이슈 #5](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/5)

**구현·실제 PostgreSQL 검증·개발 서버 적용을 완료했다.** 사용자 선택대로 운영 콘텐츠는 적재하지 않고 테스트 전용 콘텐츠로 검증했다. 채택 근거는 [API/DB 대조](contracts/curriculum-stage6-alignment.md), 발행 절차는 [템플릿 운영](curriculum-catalog-operations.md)에 정리했다.

## 사용할 수 있는 기능

- 검증·발행한 교육 템플릿을 선택해 개인 과정을 배정한다. 사용자당 진행 과정은 1개이며, 휴식 요일을 제외한 가장 이른 날부터 1일 1학습 단위를 배치한다. 시작일은 오늘부터 90일 이내, 전체 배치는 366일 이내다.
- 현재/이전 과정, 6개 교육 월차, 교육 주차와 목표, 날짜별 학습, 세션·콘텐츠를 조회한다. 교육 모듈과 달력 월/ISO 주를 구분하며 휴식일과 미편성 날짜는 명시적인 빈 상태로 반환한다.
- 예정일이 된 세션을 시작하고, 읽음 또는 확인 문제 제출 조건에 따라 콘텐츠를 완료한다. 필수 콘텐츠를 마친 뒤 자기보고 학습 분으로 세션을 완료한다. 조회만으로 시작/완료 상태를 바꾸지 않는다.
- 동일 완료를 다시 요청해도 최초 시각·학습시간·이벤트를 중복 저장하지 않는다. 완료 과정 뒤 새 과정을 배정해도 이전 버전과 완료 이력은 남는다. 다른 사용자의 과정·세션·콘텐츠는 404로 차단한다.

연결한 API는 TEMPLATE-001, CURR-001–006/008, DAY-001–004, SESSION-001–004, CONTENT-001/002의 **18개**다. 시험 버전과 문항 구성은 배정 시 검증·고정하며 응시/제출/채점은 작업 9 범위다. 확인 문제 제출 API는 작업 8이다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `docker build --target build -t daily-career-backend-check ./backend` | PASS, 컴파일·일반/계약 테스트 38개·실행 JAR |
| `docker run --rm --name daily-career-postgres-it-stage6 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify` | PASS, 일반 38 + PostgreSQL IT 44 = **82개**, 실패/오류/skip 0 |
| LearningOperationsIT 14개 | 실제 HTTP·JDBC 세션·CSRF·PostgreSQL로 전체 읽기 계약, 배정/완료 수명주기, 소유권, 동시성, rollback, 발행/폐기 이력 검증 |
| OpenAPI 원본 DTO 대조 | 실제 JSON의 required/nullable/type/enum/길이/범위/추가 필드/ID string/+09:00 검사 |
| 새 날짜 경계 | 휴식일 시작, 미래 시작 거절, 과거 미완료 시작, 93일 조회 상한, 366일 배정 초과 rollback |
| 기존 테스트 | Google 인증·사용자/세션, 공통 API, 멱등성, 기존 DB 사전 전수 비교와 신규/업그레이드 migration 통과 |
| 개발 적용 | Backend build/up, V5 적용, Nginx reload, 4서비스 running/healthy |
| `docker compose exec -T frontend node scripts/smoke.mjs` | PASS, 실제 DB/Backend/Frontend/Nginx 연결 및 관리 경로 비공개 |
| Chrome 실계정 | 기존 Google 계정 로그인 성공. 템플릿 200/[], 현재 과정 200/null, 오늘 학습 200/null, 기간 학습 200/[] 및 no-store 확인 |
| 실제 로그아웃 경계 | 로그아웃 200 후 현재 과정 401 AUTH_REQUIRED |
| 테스트 데이터 분리 | 운영 JAR에 fixture/IT/계약 테스트 자료 없음. 개발 DB의 운영 템플릿·배정 콘텐츠 0건 유지 |
| `docker compose config --quiet`, `git diff --check` | PASS |

동시 완료 테스트에서 서로 다른 멱등성 키의 외래키 KEY SHARE와 사용자 FOR UPDATE 잠금 간 교착을 재현했다. 사용자 잠금을 FOR NO KEY UPDATE로 바꿔 직렬 처리를 유지하면서 충돌을 해소했고, 실제 경합 검증을 통과했다. outbox 저장 오류를 의도적으로 발생시킨 테스트에서는 완료 시각·시간·revision·멱등성 기록이 모두 rollback된 뒤 같은 키 재시도가 성공했다. 해당 테스트의 의도된 ERROR 로그는 테스트 실패가 아니다.

Chrome에서 Cache-Control을 읽으면 같은 `no-store` 헤더가 `no-store, no-store`로 합쳐진다. 지시어를 분리해 모두 no-store임을 확인했다. 개인정보·인증 코드·토큰을 결과에 기록하지 않았다. 임시 브라우저 검증 페이지는 실행 컨테이너에서 제거했고 앱 세션은 로그아웃 상태다.

## DB 보존

V1–V4 체크섬은 각각 `1873624569`, `-1313458866`, `1647492594`, `-803042091`로 유지했다. V5 체크섬은 `53147123`이다. 개발 cluster ID `7687576957880160290`과 사용자/외부 계정 연결 건수도 유지했다.

V5는 기존 테이블을 교체하지 않고 공개 버전 메타데이터, 교육/콘텐츠 편성, 개인 학습일·세션·콘텐츠 완료 확장 테이블 8개와 발행 후 변경 방지 trigger를 추가한다. 실제 업무 DB는 **56테이블·618컬럼·119FK·201CHECK·264인덱스**, 컬럼 주석 **618/618**이다. [실제 catalog](audit/database-stage6-catalog-20260921.json)를 남겼다.

## 다음 범위

운영용 학습 콘텐츠는 사용자 결정에 따라 이번 범위에서 제외했다. 초기 템플릿 조회가 빈 배열인 것은 정상이다. 실제 템플릿 발행은 검토된 콘텐츠 적재 후 운영 명령으로 수행한다. 학습 화면은 후속 화면 작업이며 현재 홈은 준비 화면을 유지한다.

다음은 사용자 요청 시 **작업 7 휴식·일정 변경**이다. preview/confirm에서 이번에 고정한 학습 단위·세션·콘텐츠 ID와 완료 이력을 보존해야 한다. 작업 7과 커밋/푸시/PR은 진행하지 않았다.
