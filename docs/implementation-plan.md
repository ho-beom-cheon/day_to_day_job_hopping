# 구현 계획 — 2026-09-19

## Phase 0 감사 결과

- 시작 시 Git 추적 파일은 README.md 1개. 미추적 .idea 설정은 사용자 파일로 보존한다. 애플리케이션, 빌드, 테스트, 배포 구성은 없었다.
- 저장소와 제공 ZIP 전체 파일 목록 확인. 실제 openapi.yaml, SQL DDL, Flyway migration은 모두 없다.
- `design-package/daily_career_codex_implementation_20260919_full/`에 ZIP을 변경 없이 보존했다. 시작 문서, 설계 문서 전부, 이미지 설명, 마스터 프롬프트, 단계 및 체크리스트, 런타임 템플릿을 읽었다.
- 이미지 15개(핵심 11장: 결과/오답 분리, 추가 3장, 캐릭터 1장)를 `audit/design-contact-sheet.png`로 함께 시각 확인했다. 실제 PNG가 이미지 부재를 주장하는 이전 매니페스트보다 우선한다.
- Java 21.0.2, Node 22.22.0, npm 10.9.4 및 IntelliJ 번들 Maven 사용 가능. Docker/PostgreSQL CLI는 PATH에 없다.
- 기존 코드와 설계의 차이: 모든 구현이 신규다. 원본 OpenAPI 91개 인터페이스/217개 스키마와 DB 46개 테이블/558개 컬럼을 요약 문서에서 복원하지 않는다.

## 기준 우선순위와 적용 경계

사용자의 명시적 계약 → 실제 원본 OpenAPI/DDL → 통합 기준선 → 분야별 최종 문서. 문서 내 과거 완료/PASS 선언은 현재 구현 검증 결과가 아니다. 첨부 프롬프트는 설계 참고자료이며 사용자 요청 범위에서 적용한다.

DB bigint / Java Long / JSON string / TS string을 유지한다. ID에만 명시적 직렬화를 적용하여 일반 숫자와 revision까지 문자열로 변경하지 않는다. 네 진척 지표는 분리하며 mastery 산식을 만들지 않는다. 공개 API, 업무 enum, 업무 DB 컬럼은 원본 확인 전 생성하지 않는다.

## 순차 구현

| Phase | 범위 및 변경 위치 | 통과 기준 / 의존성 |
|---|---|---|
| 0 | docs 감사/계획/gap/진행 기록, 원본 패키지 | 전체 문서·이미지 검토 및 실제 원본 유무 확인 |
| 1 | backend Maven/Spring Boot, frontend Next/TS, infra Nginx, Compose, 공통 ID/trace/transport | 단위 테스트, backend verify, frontend typecheck/lint/build; ErrorEnvelope은 원본 필요 |
| 2 | backend security/session, frontend CSRF 기반 | Google OIDC 및 CSRF/로그아웃 경로 계약 필요. 확정된 보안 기반부터 구현 |
| 3 | curriculum/schedule vertical slice | OpenAPI, DB v0.2, 과정 콘텐츠 원본; preview/confirm 경쟁·멱등 테스트 |
| 4 | dashboard/learning, 시안 1/3 | 원본 DTO/경로/DDL; 학습 상태 테스트 |
| 5 | practice/wrong-answer | 원본 계약; 첫 풀이와 재풀이 분리 테스트 |
| 6 | exam/submission/grading | 원본 계약; 채점 실패 후 답안 보존, 새 run 테스트 |
| 7 | progress/evaluation | 원본 계약 및 산식; 네 지표 구분/null 테스트 |
| 8 | AI jobs | 원본 job 계약; 취소 API/버튼 없이 상태 테스트 |
| 9 | integrations/outbox | 원본 outbox/sync 스키마; commit/부분실패/재시도 테스트 |
| 10 | 배포·E2E·반응형 검증 | Docker, 원본 계약, 인증 테스트 환경 필요 |

각 Phase는 변경 → 테스트 → Backend build/test → Frontend typecheck/lint/build → 실패 수정 → progress 기록 순서로 진행한다. 선행 계약이 없는 기능만 차단하고 기반 코드 등 독립적으로 확정 가능한 영역은 진행한다. 미검증/부분 완료는 완료로 표시하지 않는다.

## Phase 1 추가 기준 — 회사/집 PC 개발환경 재현 (2026-09-19 추가)

기존 Phase 0 감사 결과와 Phase 순서 및 확정 설계 계약은 유지한다.

1. PostgreSQL은 Windows/macOS에 직접 설치하지 않고 Docker Container로만 실행한다. Frontend, Backend, PostgreSQL, Nginx 전체를 Docker Compose로 구성한다.
2. 새 PC는 Git + Docker Desktop(Linux containers/Compose) 또는 Linux Docker Engine + Compose를 준비한다. clone → `.env.example` 복사/로컬 값 설정 → `docker compose up -d --build`로 전체 환경을 실행한다. 호스트 Java/Node/PostgreSQL 설치는 필수가 아니다.
3. 주요 이미지의 태그와 manifest digest, Maven Wrapper 및 npm lockfile을 고정한다. 회사/집 PC는 같은 Git revision의 고정값을 사용한다. 서로 다른 CPU에서는 동일 manifest의 해당 아키텍처 이미지를 사용한다.
4. DB schema는 기동 시 Flyway로 자동 구성한다. 필요 시 공통 개발/테스트 데이터는 개발 전용 seed migration으로 재현하며 운영에는 포함하지 않는다. 미확정 업무 schema/콘텐츠를 seed라는 명목으로 발명하지 않는다.
5. DB는 Docker named volume으로 유지한다. 회사/집 PC의 volume은 각 Docker host에 독립적으로 존재하며 Git에 넣거나 PC 사이 직접 복사하는 방식을 기본으로 사용하지 않는다. 재시작/재생성/일반 down은 데이터를 유지한다.
6. `.env.example`에는 이름/예시/설명을 제공하고 실제 Secret/API Key/OAuth Secret 및 `.env`는 커밋하지 않는다. 개발과 운영의 Secret/Domain/Volume/cookie/bind 설정은 환경변수 또는 Profile로 분리한다.
7. README에 최소 설치, 최초 실행, 재실행, 종료, 로그 확인, DB 초기화/volume 삭제, 재빌드 명령과 데이터 삭제 영향을 기록한다.
8. 컨테이너 간 주소는 Compose 서비스명으로 연결한다. 특정 PC 절대 경로를 사용하지 않고 OCI VM에서도 같은 서비스 구조를 사용한다. 운영 HTTPS/도메인/secret 설정과 배포 검증은 별도다.

### 추가 실제 실행 인수 게이트

- [ ] PostgreSQL Container 정상 실행 및 volume 재시작 후 데이터 유지
- [ ] clean DB Flyway migration 성공
- [ ] Spring Boot / Next.js / Nginx 정상 실행
- [ ] Backend → PostgreSQL 연결 성공
- [ ] Frontend runtime → Backend Health API 호출 성공

Health 검증은 공개 업무 API 신설과 구분한다. OpenAPI가 없는 현재에는 외부에 proxy/publish하지 않는 내부 management health만 사용해 Frontend 컨테이너의 서버 런타임에서 검사한다. 브라우저용 공개 Health API가 필요하면 canonical OpenAPI 확인 후 반영한다. 실행 환경이 없으면 게이트는 미완료로 남긴다.
