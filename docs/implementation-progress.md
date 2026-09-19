# 구현 진행

## Phase 0 — 완료

저장소·패키지 문서 및 실제 이미지 전체 감사, 계획/gap 작성 완료. 기존 코드나 테스트가 없어 이 단계의 코드 빌드 대상은 없다. 패키지 체크섬 결과는 `audit/package-checksums.json`에 기록했다. 원본 문서는 변경하지 않는다.

## Phase 1 — 확정 가능한 기반 구현, 일부 인수 조건 차단

구현:

- Java 21 / Spring Boot 3.5.16 / Maven Wrapper 3.9.9. JPA는 validate, Flyway는 clean 금지.
- ID 전용 JsonId 직렬화/역직렬화: Java Long을 JSON string으로 유지하며 일반 숫자·revision은 변경하지 않음.
- 서버 생성 X-Trace-Id와 MDC 정리. 인증 전 기본 닫힘, CSRF 보호, 기본 로그인/로그아웃 endpoint 비활성화.
- Spring Session JDBC 공식 PostgreSQL 스키마 migration. 업무 테이블/공개 Controller 0개.
- Next 16.3.5 / React 19.3.0 / strict TypeScript / Tailwind / TanStack Query / shadcn 계열 Button.
- 실제 수치를 꾸미지 않은 반응형 시작 화면과 8종 상태 컴포넌트.
- same-origin API transport, 메모리 CSRF, ETag/If-Match, 논리 액션별 고정 payload/Idempotency-Key, 자동 mutation retry 금지.
- unknown response → decoder 경계. 오류 → ViewModel mapper → Component 연결, 412/428 재조회 UX.
- Dockerfile 2개, PostgreSQL/Backend/Frontend/Nginx Compose, CI 검증 workflow, 실행 문서.

아직 완료로 처리할 수 없는 조건:

- 공통 ErrorEnvelope 및 공개 DTO/상태 계약은 원본 OpenAPI 부재로 보류(GAP-001).
- Docker/clean PostgreSQL 검증은 실행 환경 부재로 차단(GAP-005).
- 전체 10화면, PWA, 학습 E2E는 이후 Phase 범위이며 아직 구현하지 않았다.

### 검증 결과

| 명령 / 확인 | 결과 |
|---|---|
| backend `mvnw.cmd -B -ntp verify` | PASS, 단위/MVC 테스트 8개, 실행 JAR 생성 |
| backend `mvnw.cmd -B -ntp -Ppostgres-it verify` | 환경 차단: Docker 미발견. 통합 테스트 1개 ERROR, skip 0. migration 성공 주장 없음 |
| `docker compose config` | 환경 차단: docker 명령 없음 |
| frontend `npm ci` | PASS, lockfile 재설치 성공. Windows optional package cleanup 경고 발생 |
| frontend `npm run typecheck` | PASS (`next typegen && tsc --noEmit`) |
| frontend `npm run lint` | PASS, 경고 0개 (`--max-warnings=0`) |
| frontend `npm test` | PASS, Vitest 5.0.1 / 3파일 / 27개 테스트 |
| frontend `npm run build` | PASS, production 정적 페이지 및 standalone 출력 생성 |
| standalone 서버 및 브라우저 | PASS, 127.0.0.1:3000 기동. 390/1280 viewport에서 시작 화면 확인, 수평 넘침 없음(콘텐츠 폭 375/1265), console error 0, 학습 버튼 disabled |
| npm 의존성 audit | 수정 후 알려진 취약점 0건 |
| ID 숫자 변환 / AI cancel 소스 검색 | production source에서 발견 0건. 아직 없는 업무 API의 계약 준수까지 검증한 것은 아님 |

수정한 실패: Maven Wrapper 생성 명령의 PowerShell 인수 분리, SessionRepository 내부 타입 접근 컴파일 오류, Next Link lint 오류, PostCSS default-export 경고, Vitest 취약 의존성. ESLint 10/React plugin 비호환은 검증 가능한 9.39.5 고정으로 해결(지원 종료 경고는 decisions에 기록).

브라우저 검증은 Phase 1 시작 화면만 대상으로 했다. 핵심 10화면 반응형/E2E 완료를 의미하지 않는다.
CI workflow는 작성했으며 원격 실행하지 않았다. Compose build/up 및 실제 migration은 아직 검증되지 않았다.
변경 파일 전체 목록은 `changed-files.md`에 기록한다.

## 다음 Phase

Phase 1의 원본 계약/런타임 게이트를 해소한 뒤 Phase 2 인증/세션/보안 vertical slice.
보안·세션 기술 기반은 이미 준비했지만 Google OIDC, CSRF 조회, 로그인/로그아웃은
경로·응답을 발명해야 하므로 구현하지 않았다. Phase 3–9는 OpenAPI/DB 원본에
의존하며 순서를 건너뛰어 추정 API와 업무 스키마를 만들지 않는다.

## Phase 1 추가 진행 — 회사/집 PC 개발환경 운영 기준 반영

기존 Phase 0 기록과 계획은 유지하고 관련 섹션만 증분 보완했다. 원본 설계 패키지는 수정하지 않았다.

### 추가 구현

- PostgreSQL Docker 전용, 4서비스 Compose, 서비스명 기반 주소, named volume/환경별 project·domain·bind·cookie·secret 변수.
- Node 22.22.0, Temurin JDK/JRE 21.0.12+8, PostgreSQL 17.11 및 Nginx 1.28 계열 manifest digest 고정. 각 이미지의 amd64/arm64 제공 확인, Maven Wrapper 3.9.9 유지.
- PostgreSQL → Backend → Frontend → Nginx health 기반 시작 순서. DB 포함 내부 management health, 외부 management 차단, Frontend runtime 연결 smoke script.
- 개발 seed profile/디렉터리 경계 준비. 실제 seed는 승인된 업무 DDL/콘텐츠 부재로 미생성.
- README에 신규 PC 설치/최초 실행/재실행/종료/로그/재빌드/DB 초기화·volume 삭제 절차 추가.
- CI에 4서비스 시작, smoke test, Flyway 이력 검사, down/up 후 volume 보존 비교 추가. 원격 CI는 실행하지 않았다.

### 이번 변경의 검증

| 확인 | 결과 |
|---|---|
| Backend `mvnw.cmd -B -ntp verify` | PASS, 11개 테스트 및 실행 JAR 생성 |
| 내부 management 실제 HTTP 테스트 | PASS, UP/200·모의 DB DOWN/503·상세 비노출·main port/다른 management 경로 차단. 실제 PostgreSQL 검증과는 구분 |
| Frontend typecheck / lint / build | 모두 PASS |
| Frontend `npm test` | PASS, 4파일 / 30개 테스트 (기존 27 + smoke script 검증 3) |
| Compose config (dev/prod 변수 조합) | PASS, 내부 서비스 port 비공개·named volume·healthcheck·profile/domain 분리 확인 |
| 공식 image manifests | 5개 digest 확인, 각각 Linux amd64/arm64 포함 |
| Compose `up -d --build` | BLOCKED, Docker Engine named pipe 없음. 컨테이너가 시작되었다고 보고하지 않음 |

기존 미리보기 standalone 서버가 `.next/standalone`을 점유해 첫 Frontend build가 EBUSY로 실패했다. 해당 작업에서 띄웠던 서버만 확인 후 종료하고 재빌드 PASS. Docker 기반 실행으로 전환할 수 있도록 현재 호스트 미리보기는 종료 상태다.

### 아직 통과하지 않은 실제 실행 게이트

- [ ] PostgreSQL Container 정상 실행 / clean Flyway 성공
- [ ] Spring Boot / Next.js / Nginx **컨테이너** 정상 실행
- [ ] Backend → 실제 PostgreSQL 연결
- [ ] Frontend 컨테이너 → Backend health 연결
- [ ] 재시작/재생성 후 named volume의 DB 데이터 보존

Docker Engine/WSL 실행 환경이 준비되면 README의 `docker compose up -d --build` 및 smoke 절차부터 재개한다. Phase 1 전체 완료나 Phase 2 완료로 승격하지 않는다.
