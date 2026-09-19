# 변경 파일 목록

## 추가 변경 — 개발환경 운영 기준

기존 목록과 Phase 0 산출물을 유지한 추가 기록이다.

- `README.md`: 새 PC의 최소 설치·실행·로그·종료·재빌드·DB/volume 삭제 절차.
- `docs/implementation-plan.md`: Phase 1 환경 운영 기준 및 실제 실행 게이트 추가.
- `docs/implementation-decisions.md`: 이미지/주소/내부 health/seed 운영 결정 추가.
- `docs/runtime.md`: Compose 기본 운영, profile/volume/health/seed 세부 설명 보완.
- `docs/implementation-gap-log.md`: Docker/WSL blocker 재확인, health 계약 경계 및 seed 보류 기록 추가.
- `docs/implementation-progress.md`: 이번 구현·테스트 결과와 미완료 게이트 추가.
- `compose.yaml`, `.env.example`, `.gitignore`, `.gitattributes`: 버전/환경 분리, health dependency, named volume, secret/DB 아티팩트 제외, Linux wrapper 줄바꿈.
- `infra/images.lock.json`: 이미지 digest 및 플랫폼 기록(신규).
- `infra/nginx/default.conf`: 도메인 템플릿 및 management 경로 외부 차단.
- `backend/Dockerfile`, `frontend/Dockerfile`: 고정 이미지와 probe/script 배치.
- `backend/mvnw`: Linux image에서 실행 가능하도록 LF 정규화.
- `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/java/dev/dailycareer/infrastructure/security/SecurityConfiguration.java`: 내부 Actuator health/보안/DB 주소 구성.
- `backend/src/main/java/dev/dailycareer/infrastructure/HealthProbe.java`: 컨테이너 probe(신규).
- `backend/src/main/resources/application-dev-seed.yml`, `backend/src/main/resources/db/dev-seed/README.md`: 개발 seed 경계(신규, SQL 데이터 없음).
- `backend/src/test/java/dev/dailycareer/infrastructure/ManagementHealthTest.java`: 내부 health HTTP/보안 테스트(신규).
- `backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java`: 고정 PostgreSQL 이미지 및 실제 DB health 검사 추가.
- `frontend/scripts/healthcheck.mjs`, `frontend/scripts/smoke.mjs`, `frontend/scripts/verify-runtime.mjs`, `frontend/scripts/verify-runtime.test.mjs`: 컨테이너 probe/연결 smoke 및 테스트(신규).
- `.github/workflows/verify.yml`: 고정 Java/Node, Compose 기동·연결·volume 보존 검증 job.

Phase 0 감사 및 Phase 1 공통 기반. 기존 .idea 사용자 설정은 수정하지 않았다.

## 구현/구성/테스트/문서

- `.env.example`
- `.github/workflows/verify.yml`
- `.gitignore`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_\355\206\265\355\225\251/IMPLEMENTATION_BASELINE_20260919.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/00_\355\206\265\355\225\251/README.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/01_\352\270\260\353\212\245\354\204\244\352\263\204/\353\215\260\354\235\274\353\246\254_\354\235\264\354\247\201_\352\270\260\353\212\245\354\204\244\352\263\204_\354\265\234\354\242\205\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/02_\354\240\234\354\236\221\354\212\244\355\216\231/\353\215\260\354\235\274\353\246\254_\354\235\264\354\247\201_\355\224\204\353\241\234\354\240\235\355\212\270_\354\240\234\354\236\221\354\212\244\355\216\231_v1.1_\354\265\234\354\242\205\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/\353\215\260\354\235\274\353\246\254_\354\235\264\354\247\201_UIUX_v0.3_\354\265\234\354\242\205\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/03_UIUX/\355\225\265\354\213\254\354\213\234\354\225\210_10\354\242\205_\354\231\204\353\243\214\353\252\251\353\241\235.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/04_DB_ERD/\353\215\260\354\235\274\353\246\254_\354\235\264\354\247\201_DB_ERD_v0.2_\353\263\265\354\233\220_\354\265\234\354\242\205\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/05_API/\354\240\225\354\240\201_\352\262\200\354\246\235_\353\263\264\352\263\240\354\204\234_v1.2.1.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/05_API/API_\354\235\270\355\204\260\355\216\230\354\235\264\354\212\244_v1.2.1_\354\265\234\354\242\205\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/06_Backend/Backend_DTO_Controller_\354\265\234\354\242\205\352\270\260\354\244\200_\353\263\265\354\233\220\353\263\270.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/07_Frontend/front_ts_api_client_detailed_design_v1.0_20260918_\353\263\265\354\233\220\353\263\270.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/08_Notion/Notion_\354\227\260\353\217\231_\354\265\234\354\242\205\352\270\260\354\244\200_\353\263\265\354\233\220\353\263\270.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/09_Slack_WebPush/Slack_WebPush_\354\227\260\353\217\231_\354\265\234\354\242\205\352\270\260\354\244\200_\353\263\265\354\233\220\353\263\270.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/10_\354\272\220\353\246\255\355\204\260/\352\263\265\354\213\235_\354\272\220\353\246\255\355\204\260_\353\224\224\354\236\220\354\235\270_\352\270\260\354\244\200.md"`
- `"design-package/daily_career_codex_implementation_20260919_full/01_design_docs/99_\354\233\220\353\263\270\355\230\204\355\231\251/ORIGINAL_FILE_STATUS.md"`
- `backend/.dockerignore`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/Dockerfile`
- `backend/mvnw`
- `backend/mvnw.cmd`
- `backend/pom.xml`
- `backend/src/main/java/dev/dailycareer/common/json/JsonId.java`
- `backend/src/main/java/dev/dailycareer/common/json/StringLongIdDeserializer.java`
- `backend/src/main/java/dev/dailycareer/common/trace/TraceIdFilter.java`
- `backend/src/main/java/dev/dailycareer/DailyCareerApplication.java`
- `backend/src/main/java/dev/dailycareer/infrastructure/security/SecurityConfiguration.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V1__spring_session.sql`
- `backend/src/main/resources/db/README.md`
- `backend/src/test/java/dev/dailycareer/common/json/JsonIdTest.java`
- `backend/src/test/java/dev/dailycareer/common/trace/TraceIdFilterTest.java`
- `backend/src/test/java/dev/dailycareer/infrastructure/security/SecurityConfigurationTest.java`
- `backend/src/test/java/dev/dailycareer/infrastructure/SessionMigrationIT.java`
- `compose.yaml`
- `docs/audit/design-contact-sheet.png`
- `docs/audit/package-checksums.json`
- `docs/audit/package-inventory.json`
- `docs/audit/source-zip.sha256.txt`
- `docs/implementation-decisions.md`
- `docs/implementation-gap-log.md`
- `docs/implementation-plan.md`
- `docs/implementation-progress.md`
- `docs/runtime.md`
- `frontend/.dockerignore`
- `frontend/Dockerfile`
- `frontend/eslint.config.mjs`
- `frontend/next-env.d.ts`
- `frontend/next.config.ts`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/postcss.config.mjs`
- `frontend/src/api/client.test.ts`
- `frontend/src/api/client.ts`
- `frontend/src/app/globals.css`
- `frontend/src/app/layout.tsx`
- `frontend/src/app/page.tsx`
- `frontend/src/app/providers.tsx`
- `frontend/src/components/api-error-feedback.test.tsx`
- `frontend/src/components/api-error-feedback.tsx`
- `frontend/src/components/ui/button.tsx`
- `frontend/src/components/ui/state-panel.test.tsx`
- `frontend/src/components/ui/state-panel.tsx`
- `frontend/src/lib/utils.ts`
- `frontend/src/mappers/api-error.ts`
- `frontend/src/test/setup.ts`
- `frontend/src/types/id.ts`
- `frontend/tsconfig.json`
- `frontend/vitest.config.ts`
- `infra/nginx/default.conf`
- `README.md`
- `docs/changed-files.md`

## 설계 패키지 보존

`design-package/daily_career_codex_implementation_20260919_full/`에 원본 ZIP을 변경 없이 풀어 보존했다.
전체 원본 파일 목록 및 크기는 `audit/package-inventory.json`, 체크섬 검증은 `audit/package-checksums.json` 참조.

## 생성되었으나 버전 관리에서 제외한 항목

- `backend/target/`: JAR, 단위/통합 테스트 리포트
- `frontend/node_modules/`, `frontend/.next/`: 설치 및 빌드/standalone 산출물
- `frontend/tsconfig.tsbuildinfo`: 타입 검사 캐시
