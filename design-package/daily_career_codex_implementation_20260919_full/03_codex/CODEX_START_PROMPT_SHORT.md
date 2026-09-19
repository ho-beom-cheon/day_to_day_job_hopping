첨부한 `daily_career_codex_implementation_20260919` 패키지를 이 프로젝트의 최종 설계 기준으로 사용해 구현을 시작해줘.

먼저 `00_START_HERE/README.md`, `01_design_docs/**`, `03_codex/CODEX_MASTER_IMPLEMENTATION_PROMPT.md`, `IMPLEMENTATION_PHASES.md`, `ACCEPTANCE_CHECKLIST.md`를 전부 읽고 현재 저장소와 비교해.

중요:
- 설계를 임의 변경하거나 새 공개 API를 만들지 마.
- DB bigint / Java Long / JSON string / TypeScript string ID 계약을 지켜.
- adherenceRate, completionRate, masteryRate, firstAttemptAccuracy 의미를 혼용하지 마.
- AI 사용자 취소 기능은 만들지 마.
- 일정 변경은 preview → confirm + revision/If-Match.
- 필요한 mutation은 Idempotency-Key.
- 시험 제출과 채점 성공을 분리하고, 채점 실패 시 제출/답안을 보존해.
- Notion/Slack/Web Push 실패가 핵심 업무를 롤백하게 만들지 마.
- UI는 핵심 10화면과 UI/UX v0.3 디자인 기준을 따른다.

한 번에 전체를 대량 생성하지 말고 Phase 0 감사 → Phase 1 공통 기반부터 순차 구현해. 각 Phase마다 build/test/typecheck를 실행하고 `docs/implementation-progress.md`를 갱신해.

설계 원문이 없거나 충돌하는 항목은 임의 결정하지 말고 `docs/implementation-gap-log.md`에 기록하되, 다른 확정 영역 구현은 계속 진행해.
