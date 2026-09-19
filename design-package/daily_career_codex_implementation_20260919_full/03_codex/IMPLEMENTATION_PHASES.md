# 구현 단계

## Phase 0 — 저장소 감사 및 기준선 고정
- 전체 설계 문서 읽기
- 기존 코드/구성 확인
- 기술스택 충돌 탐지
- `docs/implementation-plan.md` 작성
- `docs/implementation-gap-log.md` 작성
- 새 공개 API나 새로운 업무 규칙을 만들지 않음

## Phase 1 — 프로젝트 골격 및 공통 기반
- Next.js + TypeScript 프론트 골격
- Spring Boot Java 21 백엔드 골격
- PostgreSQL / Flyway
- Docker Compose
- Nginx 리버스 프록시 기본 설정
- 공통 응답/ErrorEnvelope
- JSON ID string 직렬화 정책
- traceId / 로깅
- API Client 공통 계층

## Phase 2 — 인증/세션/보안
- Google OIDC
- Spring Security
- Spring Session JDBC
- CSRF 토큰 조회/전달
- 로그인/로그아웃
- 401/403 처리

## Phase 3 — 온보딩/커리큘럼/일정
- 커리큘럼 템플릿
- 최초 과정 배정
- 6개월 로드맵
- 휴식 정책
- reschedule preview → confirm
- revision / ETag / If-Match
- Idempotency-Key

## Phase 4 — 대시보드/오늘 학습
- DASH-001
- 오늘 학습/학습일
- 세션 시작/완료
- 학습 콘텐츠
- 핵심 UI 시안 1/3 적용

## Phase 5 — 문제/오답
- 문제 풀이
- 첫 풀이 정답률
- 오답 원장
- 재풀이/해결 상태

## Phase 6 — 시험/채점
- 시험 현황
- 시험 응시
- 답안 임시저장/수정
- 제출
- 제출 상태와 채점 상태 분리
- grading run
- 결과/오답 분석

## Phase 7 — 진척/월말평가
- adherenceRate
- completionRate
- masteryRate
- firstAttemptAccuracy
- masteryRate 산식은 설계에서 확정되지 않은 가중치를 임의 발명하지 않음
- 월말 평가 버전 보존

## Phase 8 — AI
- 비동기 job 생성/조회
- 사용자 cancel API/버튼 금지
- 성공/실패 상태
- AI Provider adapter 분리
- 개발 환경 mock provider 지원 가능하나 외부 계약 변경 금지

## Phase 9 — Notion / Slack / Web Push
- outbox 기반 비동기 처리
- 원 업무 commit 이후 외부 처리
- 실패/재시도/부분실패
- UI 상태 분리

## Phase 10 — 품질/배포
- API 계약 테스트
- DB/Flyway 통합 테스트
- 백엔드 단위/통합 테스트
- 프론트 타입체크/린트/테스트
- 핵심 10화면 반응형 확인
- Docker Compose 실행 검증
- 운영용 환경변수 문서화
