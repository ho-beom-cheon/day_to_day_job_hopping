# 기능: DB 상세설계와 OpenAPI 기반 PostgreSQL 스키마 구현

## 배경

사용자는 3단계 구현을 재개하고 설계 차이는 구현자가 해결하도록 승인했다. 실제 제공된 DB 자료는 `데일리_이직_DB_ERD_설계서_v0.1_20260918.html`이며 OpenAPI는 `openapi.yaml` v1.2.1이다. 존재하지 않는 v0.2 확정 원본을 보유한 것으로 보고하지 않는다.

2026-09-20 HTML 컬럼 사전 집계 결과는 업무 테이블 46개, 컬럼 557개, FK 105개다. 이전 v0.2 요약의 558컬럼과 1개 차이가 있으나 어떤 컬럼인지는 입증할 수 없다. 558이라는 숫자를 맞추기 위한 임의 컬럼 추가는 하지 않는다.

## 승인된 범위

- 제공된 상세 DB 설계와 OpenAPI를 대조해 필요한 최소 보완을 결정하고 차이와 근거를 기록한다.
- PostgreSQL DDL/Flyway, PK/FK/UNIQUE/CHECK/INDEX/DEFAULT/NOT NULL 및 revision, 멱등성/outbox를 구현한다.
- 공식 Spring Session V1은 유지한다. 업무 스키마와 인프라 스키마의 집계를 분리한다.
- 업무 Java 로직/API/Frontend는 구현하지 않는다. 명시되지 않은 seed는 만들지 않는다.

## 사전 확인 결과 및 구현 검토 항목

- 상세 HTML은 업무 schema를 `daily_career`, 업무 PK를 `bigint GENERATED ALWAYS AS IDENTITY`, FK를 bigint로 정의한다. 기존 Session/Flyway는 public에 있으므로 업무 migration에서 schema를 명시하고 기존 인프라를 옮기지 않는 방안을 검증한다.
- DB 문서에서는 외부 ID string 전환이 미확정 제안이나, 이후 통합 기준 및 OpenAPI v1.2.1을 외부 계약 기준으로 적용한다. DB bigint를 varchar/UUID로 바꾸지 않는다.
- `schedule_revision`은 bigint/NOT NULL/default 0 및 음수 금지 CHECK가 정의되어 있다. `revision`, 콘텐츠 version, grading run 번호와 의미를 합치지 않는다.
- 시험 답안과 제출 기록, grading_run 및 grading_result를 구분하며 새 run으로 재채점하고 성공한 run만 공식 결과로 삼는 정책을 유지한다.
- HTML의 AI 내부 status에 CANCELED/UNKNOWN이 있다. 이를 공개 API enum에 그대로 노출하는 것으로 해석하지 않는다. 사용자 cancel API/UI를 추가하지 않는다. 상세 API enum 및 상태 변환 가능성을 전수 대조해야 한다.
- 업무 저장과 outbox는 같은 로컬 트랜잭션에 기록하고 외부 호출은 분리한다. 이번 단계는 이를 위한 저장 구조만 구현한다.
- 진척도 의미는 OpenAPI v1.2.1의 4지표를 따른다. 미확정 mastery 산식은 만들지 않는다.
- HTML의 인덱스는 '인덱스 후보'로 표시되어 있으므로 확정 제약과 구분해 각각 채택 이유를 기록한다.

## 완료 기준과 검증

1. 원본의 테이블/컬럼/제약을 기계 판독 가능한 기대 목록으로 추출하고 생성 DDL과 전수 대조한다.
2. 기존 V1 뒤에 의존 순서에 맞는 Flyway migration을 추가한다.
3. 기존 개발 volume을 보존하고 별도 빈 PostgreSQL 컨테이너에서 앱/Flyway를 실행한다.
4. 실제 catalog에서 테이블, 컬럼, PK/FK, unique/check/index, default/nullability, ID/revision 타입을 전수 확인한다.
5. 무효 FK/중복 키/잘못된 CHECK 값 거부와 migration 재실행의 안정성을 검증한다.
6. Backend build/test와 Compose config/기동/DB health를 검증한다. 기존 postgres-it 관리 포트 하드코딩 및 migration 개수=1 단정은 변경 필요성을 검토한다.
7. 최종 집계는 업무 46테이블 기준과 Session/Flyway 포함 총계를 별도로 보고한다. 557/558 차이와 실제 보완사항을 명시한다.

## 현재 실행 상태

이 초안은 2026-09-20 [이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2)로 등록했다. 작업 3의 채택 범위와 API 차이는 [DB 채택 기준](contracts/database-stage3-alignment.md), 구현·실제 DB 검증 결과는 [완료 보고서](database-stage3-completion-20260920.md)에 기록한다. 원본 기준 46테이블에 일정 preview 1테이블을 보완하며, 설정/알림 등 후속 기능의 저장 필드 보완을 별도로 추적한다.
