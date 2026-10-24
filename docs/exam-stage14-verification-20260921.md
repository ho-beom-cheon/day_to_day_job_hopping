# 작업 14 시험 Backend 검증 — 2026-09-21

## 결과

- `TEST-001~008` route, 시험 스냅샷, 답안 revision 저장, 멱등 시작·제출, 비동기 규칙 채점과 결과 집계를 구현했다.
- 실패한 채점은 제출과 분리해 보존하며 `error_code`를 기록한다. 재채점은 새 run으로 수행한다.
- 시험 route를 명시적으로 인증 허용 목록에 포함했다.

## 검증

| 항목 | 결과 |
|---|---|
| Docker JDK 21 Maven verify | PASS, 40 tests |
| Backend image clean build | PASS |
| 실제 PostgreSQL Flyway | PASS, 7 migrations validated, schema version 7 |
| 실행 health | PASS, backend/postgres/frontend/nginx healthy |
| `GET /api/v1/tests` | PASS, 200 envelope 및 빈 목록 |

운영 시험 콘텐츠가 없어 실제 사용자 응시 생성부터 결과까지의 데이터 흐름은 현재 실행 DB에서 검증하지 않았다. 자동화된 PostgreSQL 시험 fixture 검증을 후속 보강 대상으로 남긴다.

