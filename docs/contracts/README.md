# 작업용 계약의 기준 자료

2026-09-20 작업 1에서 적용 기준을 정리한 뒤 기능별 채택 기준을 누적했다. 이 디렉터리는 설계와 구현 사이의 계약 변경을 관리한다. 실행 DDL은 Flyway V2–V6에 반영했으며 원본 OpenAPI/HTML은 수정하지 않았다.

| 영역 | 기준 자료 | 적용 상태 |
|---|---|---|
| 공개 API | [OpenAPI v1.2.1](../../design-package/supplemental-20260920/openapi.yaml) | 경로/method/status/DTO/enum/required/nullable의 기준으로 채택 |
| Backend 공통 | [공통 계약·사용법](backend-stage4-alignment.md) | Envelope/Error·validation·ID/trace·ETag·DB 멱등성 구현. 업무 route별 연결은 각 기능 단계 |
| 인증·회원 | [인증·회원 기준](auth-stage5-alignment.md) | Google OIDC/CSRF/내 정보/닉네임/로그아웃 구현, 자동 검증 및 Google 실계정 검증 완료 |
| 커리큘럼·학습 | [배정·학습 기준](curriculum-stage6-alignment.md) | 발행 템플릿 검증/배정, 과정·일정·세션·콘텐츠 조회와 완료 API 18개. 운영 콘텐츠는 사용자 결정에 따라 비워 둠 |
| 휴식·일정 변경 | [일정 변경 기준](schedule-stage7-alignment.md) | preview/confirm, 휴식일 추가·해제, 정책 교체와 backlog 이동. 시작·완료 학습 및 인스턴스 ID 보존 |
| 문제·오답 복습 | [문제·오답 기준](problem-stage8-alignment.md) | 문제 조회·제출·동기 규칙 채점·신고와 오답 목록·메모·해결·재개. 기본 문제와 불변 버전 분리 |
| Frontend 공통 | [Next.js 공통 기반](frontend-stage9-alignment.md) | Envelope/DTO runtime decoder, 세션·CSRF 복원, DTO→ViewModel 경계, 반응형 앱 셸과 공통 상태 |
| DB 상세 | [DB HTML v0.1](../../design-package/supplemental-20260920/데일리_이직_DB_ERD_설계서_v0.1_20260918.html) | 46테이블/557컬럼/105FK를 V2에 채택·검증. API 보완은 V3와 별도 채택 기록 |
| DB 후속 요약 | [v0.2 복원 기준](../../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/04_DB_ERD/데일리_이직_DB_ERD_v0.2_복원_최종기준.md) | 이력·무결성 원칙 참고. 558컬럼 주장만으로 상세 컬럼 추가 금지 |
| 공통 업무 규칙 | [통합 기준선](../../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_통합/IMPLEMENTATION_BASELINE_20260919.md) | ID, 네 지표, 일정 보존, 제출/채점 분리, outbox 원칙 유지 |
| 화면 | [UI/UX 요약](../../design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/데일리_이직_UIUX_v0.3_최종기준.md), 원본 PNG | 시각·상태 기준. 화면별 상세 흐름은 구현 시 보완 |
| 실행 DB 변경 | [Flyway 디렉터리](../../backend/src/main/resources/db/migration) | V1–V6 적용. V6는 문제 신고와 오답 해결 근거를 추가하며 기존 migration은 불변. 실제 업무 57테이블/631컬럼/123FK |

## 충돌을 해소하는 순서

1. 최신 사용자 결정 및 [개발 계획](../implementation-plan.md)의 변경 절차를 적용한다.
2. 공개 인터페이스는 확인한 OpenAPI를 따른다. DB 내부 상태·명칭이 다르면 먼저 명시적인 매핑으로 해결할 수 있는지 검토한다.
3. 저장 구조가 부족하면 원본 HTML 대비 변경 목록과 이유를 작성한 뒤 후속 migration으로 구현한다. DB 컬럼을 API 필드와 무조건 1:1로 늘리지 않는다.
4. 제품 정책이나 공개 계약을 바꿔야 한다면 선택지와 영향을 기록하고 필요한 결정을 받는다. 원본 또는 클라이언트 타입만 조용히 고치지 않는다.
5. 반영한 변경은 [결정 기록](../implementation-decisions.md)과 API/DB 비교표, 관련 테스트에 함께 연결한다. 원본 사본은 보존한다.

## 원본에서 확인된 세부 계약

- 인증은 허용된 Google 계정의 OIDC 로그인이다. 공개 회원가입, 비밀번호 로그인, 자체 JWT 갱신을 기본 범위에 추가하지 않는다.
- OAuth 로그인 시작/콜백은 `/oauth2/authorization/google`, `/login/oauth2/code/google`이며 브라우저 이동 경로다. JSON API client를 통해 호출하지 않는다.
- CSRF 조회는 `GET /api/v1/auth/csrf`, 로그아웃은 `POST /api/v1/auth/logout`, 내 정보는 `GET /api/v1/users/me`다.
- 오류는 `ErrorEnvelope`의 필수 `success`, `data`, `error`, `meta`를 따른다. `Meta.traceId`는 32자리 소문자 16진수이며 서버 시각은 `+09:00` 표현이다.
- 초기 `Settings.timeZone`은 `Asia/Seoul`, 언어는 `KO`다. DB의 `ko-KR`·IANA timezone 저장 능력과 공개 선택지를 구분한다.
- `OverallProgress`의 네 비율은 required이면서 null을 허용한다. 숙련도 계산 방식은 여전히 미확정이다.
- 공개 AI 상태는 `READY`, `PROCESSING`, `SUCCESS`, `FAILED`다. HTML의 내부 `CANCELED`/`UNKNOWN`을 공개 enum으로 옮기지 않는다.

이 목록은 중요 항목의 발췌다. OpenAPI 전체를 대체하지 않으며, 전체 API/DB 의미 비교와 1,036개 참조의 스키마 유효성 전수 검증은 구분한다. 구체 차이와 해소 시점은 [1번 결과](../project-stage1-assessment-20260920.md)에 정리했다.
