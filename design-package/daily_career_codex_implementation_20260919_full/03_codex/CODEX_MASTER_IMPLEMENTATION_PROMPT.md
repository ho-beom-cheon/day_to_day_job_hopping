# Codex 구현 마스터 프롬프트

당신은 이 저장소의 구현 담당 시니어 풀스택 엔지니어다.
첨부/배치된 `daily_career_codex_implementation_20260919` 패키지를 프로젝트 최종 설계 기준으로 사용하여 **데일리 이직 6개월 교육 웹앱**을 실제 실행 가능한 상태로 구현하라.

## 0. 가장 먼저 할 일

코드를 바로 대량 생성하지 말고 다음을 먼저 수행한다.

1. 현재 저장소 전체 구조와 기존 코드를 검사한다.
2. `00_START_HERE/README.md`를 읽는다.
3. `01_design_docs` 아래 문서를 모두 읽는다.
4. `03_codex/IMPLEMENTATION_PHASES.md`와 `ACCEPTANCE_CHECKLIST.md`를 읽는다.
5. 프로젝트에 실제 `openapi.yaml`, DB DDL/Flyway, UI 시안 이미지가 별도로 존재하는지 검색한다.
6. 찾은 원본은 요약본보다 우선한다.
7. `docs/implementation-plan.md`를 만들고 현재 코드 상태 대비 구현 단계/파일 변경 범위를 작성한다.
8. `docs/implementation-gap-log.md`를 만들고 설계 원문이 없거나 서로 충돌하는 부분만 기록한다.

설계에 없는 업무 규칙, 엔드포인트, enum, 상태, DB 필드를 편의상 임의 추가하지 마라.

## 1. 기술 기준

Frontend:
- Next.js
- React
- TypeScript
- 모바일 우선 반응형 Web/PWA
- Tailwind CSS
- shadcn/ui 계열 컴포넌트
- TanStack Query
- API DTO → mapper → ViewModel → Component 분리

Backend:
- Java 21
- Spring Boot REST API
- Spring Security + Google OIDC
- Spring Session JDBC
- Spring Data JPA
- Flyway

Database:
- PostgreSQL
- 업무 PK bigint

Runtime:
- Docker Compose
- Nginx reverse proxy
- 단일 VM 배포 가능 구조

기존 저장소에 이미 결정된 빌드 도구/패키지 매니저/코드 스타일이 있으면 그것을 우선한다. 빈 저장소라면 최소한의 일반적인 구성을 선택하되 `docs/implementation-decisions.md`에 선택 근거를 기록한다.

## 2. 절대 변경하면 안 되는 공통 계약

### ID
- DB: bigint
- Java: Long
- JSON: string
- TypeScript: string
- 프론트에서 `Number(id)`, `parseInt(id)`, unary `+id` 등 숫자 변환 금지

### 진척도
- `adherenceRate`: 일정 이행률
- `completionRate`: 전체 커리큘럼 완료율
- `masteryRate`: 문제·시험 기반 숙련도
- `firstAttemptAccuracy`: 첫 풀이 정답률
- `masteryRate`와 `firstAttemptAccuracy`는 별도 지표
- 일/주/월 내부 단순 진행률은 계약에 정의된 `progressRate`를 사용할 수 있음
- `masteryRate`의 구체 가중치가 문서에서 확정되지 않은 경우 임의 공식을 만들지 말고 계산 계층을 교체 가능하게 두며 계산 불가 응답은 계약에 따라 null 처리한다.

### AI
- 사용자 취소 기능 없음
- 공개 cancel API 생성 금지
- 사용자용 취소 버튼 생성 금지
- 내부 운영 상태를 외부 계약에 임의 노출하지 말 것

### 일정
- 변경은 preview → confirm
- revision 기반 낙관적 동시성
- 과거 및 이미 시작한 학습은 이동 금지
- 미래 미시작 학습만 재배치
- 기본 정책은 일일 학습량 압축이 아니라 종료일 연장

### 시험
- 제출 상태와 채점 상태 분리
- 제출 성공 후 채점 실패해도 제출/답안 보존
- 재채점은 별도 grading run
- 성공한 grading만 공식 결과로 반영

### 멱등성
- 계약상 필요한 mutation은 `Idempotency-Key`
- 같은 키 + 같은 요청은 기존 결과 재사용
- 같은 키 + 다른 payload는 409
- 진행 중 중복 요청은 계약상 `REQUEST_IN_PROGRESS`

### 외부 연동
- PostgreSQL이 원본
- Notion/Slack/Web Push는 비동기
- 외부 장애 때문에 핵심 학습/시험 업무 트랜잭션을 롤백하지 않는다.
- outbox/event를 사용하여 commit 이후 처리한다.

## 3. API 구현 규칙

프로젝트에 실제 `openapi.yaml`이 있으면 그것을 공개 API의 canonical source로 사용한다.

- `/api/v1` 경로 유지
- Method/Path/Query/Header/Request/Response/HTTP Status/Enum/required/nullable을 임의 변경하지 않는다.
- 공개 API를 임의 추가하지 않는다.
- 공개 API가 필요해 보이는데 문서에 없다면 추가하지 말고 gap log에 기록한다.
- 공통 `ErrorEnvelope`와 traceId를 구현한다.
- 개인화/인증 응답은 계약의 Cache-Control을 따른다.
- ETag/If-Match 계약을 구현한다.
- CSRF가 필요한 쓰기 요청은 `X-CSRF-TOKEN`을 처리한다.

## 4. Backend 구현 규칙

레이어 권장 구조:

```text
controller
application/service
application/dto
application/mapper
domain/entity
domain/repository
infrastructure/persistence
infrastructure/security
infrastructure/integration
common/error
common/idempotency
common/outbox
```

기존 패키지 구조가 있으면 기존 구조를 보존한다.

- Controller에 업무 로직을 넣지 않는다.
- DTO와 Entity를 직접 공유하지 않는다.
- Long ID는 JSON에서 문자열 계약을 반드시 유지한다.
- 날짜는 OpenAPI 형식을 유지한다.
- 트랜잭션 경계는 Service/Application 계층에 둔다.
- revision 변경은 원자적으로 처리한다.
- idempotency record는 업무 성공/실패 정책과 함께 설계 문서를 따른다.
- 외부 API 호출을 핵심 트랜잭션 내부에서 수행하지 않는다.

## 5. Frontend 구현 규칙

구조 예:

```text
src/
  app/
  features/
  components/
  api/
  types/
  mappers/
  queries/
  mutations/
  lib/
```

- API DTO와 UI ViewModel을 분리한다.
- 모든 ID는 string.
- same-origin session cookie를 전제로 한다.
- CSRF token은 브라우저 메모리에서 관리한다.
- 401/403/409/412/422/428/429/5xx를 서버 ErrorCode와 함께 해석한다.
- TanStack Query key는 도메인/식별자/조건이 안정적으로 들어가야 한다.
- revision/idempotency가 중요한 mutation은 무분별한 optimistic update를 사용하지 않는다.
- 412/428은 최신 데이터 재조회 및 충돌 UX로 연결한다.
- AI job과 외부 sync는 제한된 polling/재조회 정책을 구현한다.

## 6. UI 구현 규칙

최종 디자인 방향:
- 밝은 배경
- 블루/퍼플 포인트
- 둥근 카드
- 충분한 여백
- 얇고 부드러운 shadow
- 가벼운 게임화
- 모바일 우선
- 캐릭터 비중은 낮게 유지

핵심 10화면:
1. 오늘 홈/대시보드
2. 6개월 커리큘럼/로드맵
3. 오늘 학습 상세
4. 문제 풀이
5. 시험 현황
6. 시험 응시
7. 시험 결과/오답 분석
8. 진척도/성장 분석
9. 휴식일/일정 변경
10. AI 학습 도우미/피드백

반드시 상태를 구현한다:
- Loading
- Empty
- Error
- Disabled
- Completed
- Processing
- Conflict/Stale
- External Sync Partial Failure

시험에서는 `응시 중 / 제출 완료 / 채점 중 / 채점 완료 / 채점 실패`를 하나로 합치지 않는다.

## 7. 이미지 자산

`02_assets/images/IMAGE_ASSET_MANIFEST.md`를 읽는다.
실제 PNG/JPG가 프로젝트에 추가되면 해당 이미지를 픽셀 참조로 사용한다.
이미지 바이너리가 없다고 해서 임의의 다른 캐릭터나 전혀 다른 UI 컨셉을 생성하지 않는다.
그 경우 문서의 디자인 시스템으로 먼저 구현하고 이미지 적용 지점을 컴포넌트/asset path로 분리한다.

## 8. 구현 방식

한 번에 91개 API와 모든 화면을 거대한 변경으로 만들지 마라.
`IMPLEMENTATION_PHASES.md` 순서로 vertical slice를 완성한다.

각 Phase에서:
1. 구현 전 영향을 받을 파일을 확인
2. 필요한 코드 작성
3. 테스트 작성
4. build/typecheck/lint/test 실행
5. 실패 수정
6. `docs/implementation-progress.md` 갱신
7. 다음 Phase로 넘어감

기존 코드가 있으면 파괴적 초기화/전면 재생성을 피하고 점진적으로 수정한다.

## 9. 테스트 최소 기준

Backend:
- Controller contract test
- Service unit/integration test
- Repository/Flyway integration test
- revision 경쟁 조건 테스트
- Idempotency-Key 중복 테스트
- 시험 제출 후 채점 실패 보존 테스트
- outbox commit 테스트

Frontend:
- TypeScript typecheck
- lint
- build
- API client error mapping test
- revision 412 UX test
- Idempotency-Key 재사용 정책 test
- 핵심 화면 상태 렌더 test

E2E 핵심 흐름:

```text
로그인
→ 과정 최초 배정
→ 대시보드
→ 오늘 학습
→ 세션 시작/완료
→ 문제 풀이
→ 시험 시작/답안 저장/제출
→ 채점 결과
→ 진척도 갱신
→ 휴식일 변경 preview/confirm
```

## 10. 구현 종료 조건

다음이 만족되어야 완료다.

- Backend 테스트/빌드 PASS
- Frontend typecheck/lint/build PASS
- clean PostgreSQL에 Flyway migration PASS
- Docker Compose 로컬 기동 PASS
- 핵심 E2E 흐름 PASS
- 10개 핵심 화면이 모바일/데스크톱에서 동작
- 공개 API 계약 위반 0건
- ID Number 변환 0건
- 사용자 AI cancel 기능 0건
- 발견된 설계 gap이 `docs/implementation-gap-log.md`에 기록됨

## 11. 지금 바로 실행할 작업

먼저 Phase 0을 수행하고 결과를 보고한 뒤, 즉시 Phase 1부터 구현을 시작한다.
질문을 반복해서 사용자에게 되묻기보다 문서와 기존 코드에서 확인 가능한 내용은 스스로 확인한다.
다만 설계에 없는 핵심 업무 규칙을 새로 발명해야만 진행 가능한 경우에는 해당 기능만 보류하고 gap log에 기록한 뒤, 다른 확정 영역 구현을 계속한다.

최종 보고에는 다음을 포함한다.
- 구현한 Phase
- 변경 파일
- 실행한 테스트/빌드 명령과 결과
- 남은 gap/blocked 항목
- 다음 구현 Phase
