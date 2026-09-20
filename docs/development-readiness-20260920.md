# 개발 착수 준비 상태 점검 — 2026-09-20

이번 작업은 기존 저장소 감사, 빌드 검증 및 최소 환경 수정으로 한정했다. 업무 기능, 인증 흐름, 실제 UI, Docker 통합환경의 신규 구축·기동, 운영 배포는 수행하지 않았다. 이전 문서의 PASS 기록과 이번 실행 결과를 구분한다.

## 1. 현재 프로젝트 상태

시작 시 Git 작업 트리는 깨끗했다. 저장소는 빈 프로젝트가 아니라 공통 기반이 이미 구현된 상태다.

```text
frontend/       Next.js App Router, 공통 UI·API transport·ID 타입, 테스트, Dockerfile
backend/        Spring Boot, ID JSON·trace·보안/health 기반, Maven Wrapper, Dockerfile
infra/          Nginx 템플릿, 이미지 digest 목록
compose.yaml    postgres/backend/frontend/nginx 4서비스
design-package/ 설계 복원 문서 및 실제 PNG 15개
docs/           기존 계획·결정·진행·gap·실행 기록과 감사 자료
.github/        GitHub Actions 검증 workflow
.tools/         기존 독립 docker-compose.exe (Git 제외)
.idea/          기존 IDE 설정 (Git 제외)
```

회원/Google 로그인, 학습·문제·시험·진척도·AI·외부 연동 등 업무 기능과 업무 Controller/Entity는 없다. 기존 준비 안내용 시작 페이지는 있지만 실제 대시보드/학습 화면은 없다. API client는 공통 transport와 unknown/decoder 경계만 제공한다.

## 2. 확인한 설계 문서

점검 시작 시 관련 MD 34개를 모두 읽었다. 파일별 목록과 역할은 이 문서 말미에 기록했다. 원본 설계 패키지는 수정하지 않았다.

설계가 요구하는 Java 21, Next.js/TypeScript, Spring Boot, PostgreSQL, Nginx, Docker Compose와 현재 기술 선택은 일치한다. 원본 제작 스펙은 Java 외 세부 버전을 고정하지 않으며 현재 버전은 기존 구현 결정/lockfile을 유지했다.

OpenAPI v1.2.1 YAML, DB v0.2의 46테이블/558컬럼/105FK 상세 DDL, 완전한 DTO/화면 상세 원본은 저장소에 없다. 현재 문서는 요약·복원본이며 외부 File Library의 원본을 참조한다. 문서의 과거 '개발 GO' 또는 정적 검증 PASS를 현재 런타임 검증이나 상세 계약 파일 보유로 해석하지 않는다.

## 3. 개발환경

| 영역 | 현재 상태 |
|---|---|
| Frontend | Next.js 16.3.5, React 19.3.0, TypeScript 5.9.3(lock), strict/bundler/ES2022, npm lockfile, standalone build |
| Node/npm | 호스트 22.22.0 / 10.9.4, engines >=22.13.0, Docker Node 22.22.0 digest 고정 |
| Backend | Spring Boot 3.5.16, Java 21, Maven Wrapper 3.9.9; pom.xml 있음, Gradle 없음 |
| Java | 호스트 OpenJDK 21.0.2, Docker Temurin JDK/JRE 21.0.12+8; patch 차이는 있으나 Java 21 계약 유지 |
| DB | PostgreSQL 17.11 이미지, Flyway 활성화/clean 금지, JPA validate; Spring Session migration만 존재, 업무 DDL/seed 없음 |
| Proxy/Container | Nginx 1.28 계열, 4서비스 Compose, 이미지 digest 고정, readiness 의존성 및 내부 health |
| 테스트 | Frontend Vitest 5.0.1 + Testing Library/jsdom; Backend JUnit/MockMvc/HTTP health 및 별도 postgres-it/Testcontainers 1.21.4 |
| lint/format | ESLint 9.39.5 + Next rules, warnings=0; 별도 Prettier/EditorConfig/Java formatter/lint는 없음. 이번 범위의 차단 요인 아님 |
| CI | GitHub Actions에 FE/BE 및 Compose 기동·migration·volume 보존 검증 정의. 이번 작업에서 원격 CI 실행 안 함 |
| 실행 환경 | PATH에 Docker CLI 없음; 기존 독립 Compose v5.5.1 있음. docker_engine pipe 없음, WSL 설치 필요 메시지 확인 |

회사/집 PC는 같은 Git revision과 lockfile/digest로 실행하는 구조다. Maven Wrapper와 CLI를 사용하므로 IDE 종속이 없다. Windows용 mvnw.cmd와 Linux용 mvnw가 있으며 Linux wrapper/Dockerfile은 LF다. 로컬 실행 절차는 환경파일 복사 → 로컬 DB 비밀번호 설정 → Compose build/up으로 이미 문서화되어 있다.

DB는 named volume에 저장되며 각 PC의 데이터는 독립적이다. 실제 재시작 후 보존은 Docker Engine 부재로 미검증이다. 컨테이너 간 주소는 서비스명이고 host 절대 경로가 없어 OCI Linux 및 향후 EC2/Lightsail로 옮길 구조적 기반은 있다. OCI Always Free 자원 용량, ARM 실기동 및 운영 TLS는 검증하지 않았다.

개발/운영은 profile, domain, bind/port, DB account, project/volume, secure cookie 값으로 구분한다. 현재 Compose는 빌드된 앱을 실행하는 방식이며 소스 bind mount/hot reload 설정은 없다. 운영 공개 배포 시 HTTPS 종단 설정이 추가로 필요하다.

Git ignore는 실제 .env 및 하위 환경파일, IDE, 빌드 결과, 도구, DB dump를 제외한다. 추적 환경파일은 .env.example뿐이며 실제 로컬 secret 파일을 생성하지 않았다. 이번에 Docker context에서도 환경파일을 제외하도록 보완했다.

## 4. 이번 단계 변경 파일

| 파일 | 변경 이유 |
|---|---|
| frontend/.dockerignore | 환경파일을 빌드 context 및 COPY 레이어에서 제외 |
| backend/.dockerignore | 환경파일을 빌드 context에서 제외 |
| backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java | Testcontainers 이미지 식별 오류 수정. 동일 PostgreSQL manifest digest를 유지하고 digest-only 표기로 변경 |
| docs/runtime.md | 일반 verify 테스트 수 8개를 실제 11개로 정정 |
| docs/development-readiness-20260920.md | 이번 점검 결과와 문서 목록 기록 |

새 라이브러리, 업무 코드, 공개 API, 페이지는 추가하지 않았다. npm ci/build와 Maven 실행으로 생긴 node_modules/.next/target/타입 캐시는 Git 제외 산출물이다.

## 5. 실행한 검증

Frontend 명령은 frontend, Maven 명령은 backend, Compose/Git 명령은 루트에서 실행했다.

| 명령 | 이번 결과 |
|---|---|
| node --version / npm --version / java -version | 22.22.0 / 10.9.4 / 21.0.2 확인 |
| npm ci | 성공: 466패키지 설치, audit 취약점 0; eslint/whatwg-encoding deprecated 경고 |
| npm run typecheck | 성공: next typegen 및 tsc --noEmit |
| npm run lint | 성공: ESLint --max-warnings=0 |
| npm test | 성공: 4파일, 30개 테스트 PASS |
| npm run build | 성공: Next.js production compile, TypeScript, 정적 페이지 및 standalone 산출물 |
| .\mvnw.cmd -B -ntp dependency:resolve verify | 성공: 의존성 resolve, compile/test/package, 11개 테스트 PASS, 실행 JAR 생성 |
| .\mvnw.cmd -B -ntp -Ppostgres-it failsafe:integration-test failsafe:verify | 최초 실패: 태그@digest의 이미지 호환성 판정 오류, Docker 연결 전 초기화 실패 |
| .\mvnw.cmd -B -ntp -Ppostgres-it verify | 표기 수정 후 compile/11개 일반 테스트/JAR 성공. 통합 테스트는 Docker 환경 없음으로 실패(1 error, skip 0). 최초 이미지 판정 오류는 해소 |
| docker compose config --quiet | 실행 불가: docker 명령 없음 |
| .\.tools\docker-compose.exe --env-file .env.example config --quiet | 개발/운영 변수 조합 각각 성공(exit 0). DATABASE_PASSWORD는 프로세스에 config-validation-only 예시값만 설정 |
| 위 config 명령, DATABASE_PASSWORD 빈 값 | 예상대로 실패(exit 1), 비밀번호 필수 검증 정상 |
| .\.tools\docker-compose.exe --env-file .env.example ps | 실패(exit 1): docker_engine named pipe 없음 |
| wsl --status / Test-Path '\\.\pipe\docker_engine' | WSL 미설치 안내 / False |
| git check-ignore (환경파일·IDE·도구·빌드·dump 경로) | 예상한 경로 모두 제외됨 |
| git ls-files --eol / Get-FileHash .tools/docker-compose.exe | wrapper/Dockerfile LF 확인; 독립 Compose는 기존 SHA256 파일과 일치 |
| rg 계약/파일 검색 및 소스 검토 | 아래 6절에 결과 기록 |
| git diff --check | 통과; 일부 파일의 Git CRLF 변환 안내만 있음 |

실제 Docker build/up, PostgreSQL migration, 컨테이너 간 통신, volume 보존, OCI 배포는 검증 완료로 표시하지 않는다. Mockito의 JDK agent 경고와 Vite의 향후 native config loader 호환성 안내는 현재 테스트 성공에 영향을 주지 않았다. 버전 변경이나 경고 숨김 설정은 추가하지 않았다.

## 6. 설계 충돌

**확정 업무 계약과 현재 코드의 명백한 충돌: 없음.** 아직 없는 기능의 계약 준수를 입증한 것은 아니다.

- ID: frontend/src/types/id.ts의 Id=string/readId와 Backend JsonId/StringLongIdDeserializer가 string ↔ Long 경계를 유지한다. JsonIdTest는 Long.MAX_VALUE, 숫자 ID 거부, overflow 및 일반 revision/숫자 유지 검증을 통과했다. number ID 또는 Number/parseInt/단항 + ID 변환은 발견하지 않았다.
- 진척도: progressRate/adherenceRate/completionRate/masteryRate/firstAttemptAccuracy 검색. adherenceRate는 JSON 테스트의 일반 숫자 검증에만 등장하며 실제 지표 계산은 없다. 네 지표 혼용도 없다. 통합 문서상 기간/분야 내부 progressRate는 허용되므로 해당 이름 전체를 금지하지 않는다.
- AI: 사용자 cancel API/버튼, 공개 CANCELED 없음. frontend/scripts/healthcheck.mjs:3의 response.body.cancel()은 HTTP 응답 스트림 정리이며 AI 작업 취소가 아니다.
- revision: API client의 ETag/If-Match 전달 및 412/428 오류 UX 존재. 실제 일정 revision 영속성·경쟁 업데이트는 미구현/미검증.
- 시험: 제출/채점 구현 없음. 답안 보존·새 grading run 계약을 변경한 코드도 없음.
- Spring Session CHAR(36) 식별자는 문서에서 업무 bigint PK와 구분하는 프레임워크 테이블로, 업무 ID 계약 위반이 아니다.
- ErrorEnvelope/공개 DTO/로그인 경로는 원본 OpenAPI 부재로 보류된 상태다. 이를 정합성 검증 완료로 해석하지 않는다.

문서 간 차이: 패키지 시작 README/IMAGE_ASSET_MANIFEST는 이미지 미포함이라고 하지만 PACKAGE_STATUS와 실제 파일에는 PNG 15개가 있다. 이전 감사 기록에 이미 명시된 차이이며 원본을 고치지 않았다. 과거 '빈 저장소' 기록은 당시 상태로, 현재 상태와 구분한다. runtime.md의 오래된 테스트 개수만 이번에 정정했다.

## 7. 다음 단계 준비 상태

**저장소 구성 기준으로 다음 'Docker 기반 통합 개발환경 구축' 단계에 진입할 수 있다. 현재 PC에서 즉시 실제 컨테이너 검증을 완료할 수는 없다.** 이미 있는 Compose를 재생성할 필요 없이 Docker Engine/WSL 환경 준비 후 기동·연결·migration·volume 보존을 검증해야 한다.

OpenAPI/업무 DDL 원본 부재는 인프라 환경 검증의 선행 차단 요인이 아니지만 업무 API/DB 기능 구현에는 별도 선행 자료가 필요하다. 이번 점검 뒤 다음 단계로 진행하지 않았다.

## 8. 미해결 사항

다음 환경 구축 단계 전에 필요한 것은 **Linux 컨테이너를 실행할 Docker Engine/Compose 환경 확보**다. 현재 Windows PC는 Docker Desktop/WSL 실행환경 준비가 필요하며 회사 PC에서도 Docker/가상화 사용 가능 여부를 확인해야 한다. 사용할 PC에서 로컬 DB 비밀번호를 설정해야 한다.

새 기술 스택/세부 버전을 결정할 필요는 없다. OpenAPI/DDL 원본, mastery 산식 및 콘텐츠 확보는 후속 기능 단계의 gap이며 다음 인프라 단계 이전의 필수 결정으로 올리지 않는다.

## 부록: 실제 읽은 MD 34개와 역할

| 파일 (저장소 기준) | 역할/문서 제목 |
|---|---|
| backend/src/main/resources/db/dev-seed/README.md | Development seed boundary |
| backend/src/main/resources/db/README.md | Migration provenance |
| design-package/daily_career_codex_implementation_20260919_full/00_START_HERE/PACKAGE_STATUS.md | PACKAGE STATUS |
| design-package/daily_career_codex_implementation_20260919_full/00_START_HERE/README.md | 데일리 이직 — Codex 구현 패키지 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_통합/IMPLEMENTATION_BASELINE_20260919.md | 구현 기준선 2026-09-19 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_통합/README.md | 데일리 이직 최종 설계 패키지 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/01_기능설계/데일리_이직_기능설계_최종기준.md | 데일리 이직 기능 설계 최종 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/02_제작스펙/데일리_이직_프로젝트_제작스펙_v1.1_최종기준.md | 데일리 이직 프로젝트 제작 스펙 v1.1 최종 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/데일리_이직_UIUX_v0.3_최종기준.md | 데일리 이직 UI/UX v0.3 최종 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/핵심시안_10종_완료목록.md | 핵심 시안 10종 완료 목록 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/04_DB_ERD/데일리_이직_DB_ERD_v0.2_복원_최종기준.md | 데일리 이직 DB/ERD v0.2 복원 최종 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/05_API/정적_검증_보고서_v1.2.1.md | OpenAPI v1.2.1 정적 검증 보고서 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/05_API/API_인터페이스_v1.2.1_최종기준.md | API 인터페이스 v1.2.1 최종 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/06_Backend/Backend_DTO_Controller_최종기준_복원본.md | Backend DTO / Controller 최종 기준 복원본 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/07_Frontend/front_ts_api_client_detailed_design_v1.0_20260918_복원본.md | Frontend TypeScript / API Client 상세설계 v1.0 복원본 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/08_Notion/Notion_연동_최종기준_복원본.md | Notion 연동 최종 기준 복원본 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/09_Slack_WebPush/Slack_WebPush_연동_최종기준_복원본.md | Slack / Web Push 연동 최종 기준 복원본 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/10_캐릭터/공식_캐릭터_디자인_기준.md | 데일리 이직 공식 캐릭터 디자인 기준 |
| design-package/daily_career_codex_implementation_20260919_full/01_design_docs/99_원본현황/ORIGINAL_FILE_STATUS.md | 원본 파일 현황 |
| design-package/daily_career_codex_implementation_20260919_full/02_assets/images/IMAGE_ASSET_MANIFEST.md | 이미지 자산 매니페스트 |
| design-package/daily_career_codex_implementation_20260919_full/02_assets/images/IMAGE_ASSET_MAPPING_STAGING.md | Daily Career Project - Image Asset Mapping (Staging) |
| design-package/daily_career_codex_implementation_20260919_full/02_assets/images/README.md | 이미지 자산 안내 |
| design-package/daily_career_codex_implementation_20260919_full/03_codex/ACCEPTANCE_CHECKLIST.md | 구현 인수 체크리스트 |
| design-package/daily_career_codex_implementation_20260919_full/03_codex/CODEX_MASTER_IMPLEMENTATION_PROMPT.md | Codex 구현 마스터 프롬프트 |
| design-package/daily_career_codex_implementation_20260919_full/03_codex/CODEX_START_PROMPT_SHORT.md | 구현 시작 요청 및 계약 준수 지침 |
| design-package/daily_career_codex_implementation_20260919_full/03_codex/IMPLEMENTATION_PHASES.md | 구현 단계 |
| design-package/daily_career_codex_implementation_20260919_full/04_runtime/ENVIRONMENT_TEMPLATE.md | 환경변수 템플릿 — 값은 저장소에 커밋하지 말 것 |
| docs/changed-files.md | 변경 파일 목록 |
| docs/implementation-decisions.md | 구현 결정 |
| docs/implementation-gap-log.md | 구현 Gap Log |
| docs/implementation-plan.md | 구현 계획 — 2026-09-19 |
| docs/implementation-progress.md | 구현 진행 |
| docs/runtime.md | 실행 및 검증 |
| README.md | 데일리 이직 |
