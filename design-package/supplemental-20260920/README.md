# 추가 확보한 설계 원본 — 2026-09-20

프로젝트·설계 현황 정리(작업 1)에서 사용자 Downloads 폴더의 아래 파일을 발견해 **바이트 변경 없이 복사**했다. 기존 `daily_career_codex_implementation_20260919_full/` 패키지는 수정하지 않았다.

| 파일 | 확인한 버전 | 바이트 | SHA-256 |
|---|---|---:|---|
| [openapi.yaml](openapi.yaml) | OpenAPI 3.1.1 / 문서 1.2.1 | 1,222,892 | `6c9d02efda9e093d9385ba676168e71dd7786e46d9f55d680963979ec90a22c5` |
| [DB 상세 HTML](데일리_이직_DB_ERD_설계서_v0.1_20260918.html) | v0.1 검토본 / 2026-09-18 | 376,954 | `fdc8883ca80ad2d3ebb3c2b27f3697fa3f67d695a718db97f06a02e46364db96` |

원래 위치는 `%USERPROFILE%\Downloads\openapi.yaml` 및 같은 폴더의 `데일리_이직_DB_ERD_설계서_v0.1_20260918.html`이다. 저장소 사본과 원본의 SHA-256 일치를 확인했다. 파일의 진위나 작성자의 과거 검증 결과 전체를 인증한 것은 아니다.

HTML에는 테이블·컬럼 사전과 관계 정보가 포함된다. 본문이 참조하는 `schema_metadata.json`, `schema_draft.sql`, `table_dictionary.md`, `design.md`, `validation.json`, `erd_all.mmd`는 이번 저장소/Downloads 검색에서 별도 파일로 발견하지 못했다. HTML을 v0.2 확정 DDL로 취급하지 않는다.

HTML은 '외부 API 호출 없음'이라고 설명하지만 외부 `script src` 1개가 있다. 이번에는 브라우저에서 실행하지 않고 정적으로 파싱했다. 원본 HTML을 애플리케이션의 공개 정적 자산으로 복사하지 않는다.

채택 기준은 [계약 자료 안내](../../docs/contracts/README.md), 집계와 차이는 [1번 결과](../../docs/project-stage1-assessment-20260920.md), 기계 판독 목록은 [정적 분석 JSON](../../docs/audit/design-baseline-20260920.json)을 따른다. 이후 보완은 원본을 덮어쓰지 않고 작업용 계약과 변경 기록에 남긴다.
