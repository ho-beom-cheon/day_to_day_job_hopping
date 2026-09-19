# 데일리 이직 — Codex 구현 패키지

기준일: 2026-09-19

이 패키지는 데일리 이직 6개월 교육 웹앱 구현을 Codex Desktop/CLI에 인계하기 위한 기준 패키지다.

## 시작 순서

1. `03_codex/CODEX_MASTER_IMPLEMENTATION_PROMPT.md`를 Codex에 전달한다.
2. Codex가 먼저 전체 저장소와 `01_design_docs`를 읽고 구현 계획을 갱신하게 한다.
3. 구현은 `03_codex/IMPLEMENTATION_PHASES.md` 순서로 진행한다.
4. 각 단계에서 `03_codex/ACCEPTANCE_CHECKLIST.md`를 통과한 뒤 다음 단계로 진행한다.
5. 설계와 구현이 충돌하면 구현자가 임의로 계약을 바꾸지 말고 `docs/implementation-gap-log.md`에 기록한다.

## 계약 우선순위

충돌 시 다음 순서로 본다.

1. `01_design_docs/00_통합/IMPLEMENTATION_BASELINE_20260919.md`
2. API v1.2.1 최종 기준 및 정적 검증 결과
3. DB/ERD v0.2 최종 기준
4. Backend DTO/Controller 기준
5. Frontend TypeScript/API Client 기준
6. UI/UX v0.3 기준
7. 기능 설계

단, 실제 `openapi.yaml` 원본이 프로젝트에 제공되면 API 상세 계약은 그 파일을 canonical source로 사용한다.

## 중요 계약

- DB ID: bigint
- Java ID: Long
- 외부 JSON ID: string
- TypeScript ID: string
- 프론트 Number 변환 금지
- adherenceRate / completionRate / masteryRate / firstAttemptAccuracy 의미 분리
- AI 사용자 취소 없음
- 일정 변경 preview → confirm
- revision/ETag/If-Match 사용
- 필요한 mutation에 Idempotency-Key
- 시험 제출과 채점 상태 분리
- 외부 연동 실패로 핵심 업무 트랜잭션 롤백 금지

## 이미지 자산 상태

디자인 방에서 핵심 시안 10종 완료는 확정되었지만, 이전 대화의 생성 이미지 바이너리는 현재 런타임에서 직접 회수되지 않는다.
따라서 `02_assets/images/IMAGE_ASSET_MANIFEST.md`에 최종 시안 목록과 파일 배치 규칙을 기록했다.
Codex 프로젝트에 실제 이미지가 제공될 경우 해당 폴더에 동일 의미의 이름으로 넣고 디자인 기준으로 사용한다.
