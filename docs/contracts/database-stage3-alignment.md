# 작업 3 DB 채택 기준

2026-09-20 / 관련 [이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2)

## 구현 전 비교와 결정

실제 DB HTML v0.1의 46테이블·557컬럼·105FK를 물리 스키마의 출발점으로 채택한다. 원본은 수정하지 않는다. V1 Spring Session과 public의 Flyway 이력은 유지하고 업무 테이블은 `daily_career`에 한정한다. PostgreSQL 18 제안은 현재 고정된 17.11 실행환경으로 바꾸지 않고, 17.11에서 검증한다.

| 항목 | 차이와 채택 결정 | 적용/검증 |
|---|---|---|
| DB 원본 수치 | 후속 요약의 558컬럼은 상세 근거 없음. 557컬럼을 기준으로 비교 | 추출 사전과 실제 catalog 대조 |
| PK/FK | bigint identity와 복합 소유권 FK 유지 | 전수 catalog 비교, 타 사용자 참조 거부 |
| Flyway 이력 | DB 역할 이름과 업무 schema 이름이 같으면 기본 search_path의 `$user`가 새 schema를 우선함 | `spring.flyway.default-schema=public`으로 고정. 실제 역할명으로 신규 설치/업그레이드/새 연결 재실행 검증 |
| 인덱스 후보 | 초기 기준선은 후보 전체 채택. 부분 UNIQUE는 업무 불변식이며 일반 인덱스는 조회·작업 점유·FK 경로용 | 정의/조건/유일성 대조. 운영 통계 기반 정리는 후속 migration |
| 최종시험 | DB의 DAILY/WEEKLY/MONTHLY에 API `TestType.FINAL` 누락 | CHECK에 FINAL 추가 |
| 일정 preview | API previewId, scheduleRevision, 10분 만료, 1회 소비를 보관할 전용 구조 없음 | `schedule_preview` 추가. 요청/미리보기 JSON 스냅샷과 사용자·과정·revision·만료·소비 시각 보관 |
| 설정 | user_setting의 분 단위 목표는 다음 과정 기본값이며 schedule_policy는 현재 과정 정책. API 테마·평일/주말 시작시각·기본 휴식요일은 원본 저장소에 없음 | 작업 22에서 설정 초기값/저장 필드를 후속 migration으로 확정. 현재 과정 정책을 기본 설정 대신 덮어쓰지 않음 |
| 알림 설정 | 원본 notification_preference는 채널/이벤트별 행, API는 전체 설정 revision·주간 요일·시험 몇 분 전 알림 | 작업 16/22에서 집합 revision과 누락 필드를 보완. 원본만으로 API 저장 완료라고 보고하지 않음 |
| 사용자 | email nullable, 표시명 길이 80은 API의 필수 email/닉네임 최대 30보다 넓음. 프로필 URL 저장 없음 | 작업 5 OIDC 도입 시 검증된 email/닉네임 계약과 프로필 저장 결정. 로그인 식별은 issuer+subject 유지 |
| API 범위 | StudyPolicy는 15..480분·휴식 0..6, 내부 DB는 1..1440·0..7 | 내부 저장 범위 유지. 사용자 요청은 API DTO에서 좁게 검증하며 기본값 60분/휴식2일은 양쪽에 부합 |
| revision | DB bigint와 API integer 상한이 다름 | DB 유지, API 범위와 증가/충돌 처리는 서비스 검증. 콘텐츠 version 및 grading run_no와 분리 |
| AI 상태/목적 | 내부 QUEUED/RUNNING/SUCCEEDED와 공개 READY/PROCESSING/SUCCESS가 다름. 내부 UNKNOWN/CANCELED 및 범용 job_type 존재 | 작업 14/15 명시적 매핑과 복구 정책. 공개 cancel 또는 임의 UNKNOWN 성공 변환 금지 |
| 외부 연동 | 연결 설정 JSON, credential_ref, 매핑/재시도/lease를 원본대로 채택 | 자격증명은 실제 값 대신 참조만 저장. 공급자별 DTO 매핑은 작업 17/18 |
| 진척 | 원천과 월별 스냅샷 존재, 숙련도 산식은 미확정 | 새 임의 집계/점수 seed 없음. 조회 계산과 null 응답은 해당 기능에서 구현 |

## DB와 서비스의 책임

PK/FK/UNIQUE/CHECK로 존재·소유권 조합·숫자/상태 범위·공식 성공 run 유일성을 보장한다. 업무 저장과 outbox의 동일 트랜잭션 구조, 멱등성 키 중복 제한을 검증한다. 제출 이후 답안 수정 금지, 과거 일정 보존, published 본문 불변, 권한, 외부 전달/lease 복구와 revision 증가는 후속 서비스의 트랜잭션 및 검증 책임이다. DDL만으로 이 동작이 구현되었다고 간주하지 않는다.

preview 소비는 서비스가 과정 행을 잠그고 사용자·만료·schedule_revision·미소비를 검사한 뒤, 일정 변경과 소비 시각 기록을 같은 트랜잭션으로 처리해야 한다. JSON은 고정된 요청/응답 스냅샷 용도이며 관계·소유권은 FK 컬럼으로 보존한다. 10분 TTL은 DB 기본값/시간 CHECK와 서비스 조회 조건을 함께 사용한다. 설정 기본 시각이나 제품 정책은 API 예시값을 임의 기본값으로 채택하지 않는다.

이 작업은 전체 업무 DB 기준선과 확인된 일정/시험 보완을 구현한다. 91개 API의 DTO·업무 로직·저장 매핑 전체 구현 완료를 의미하지 않는다. 위 후속 항목은 각 기능을 구현하기 전에 roll-forward migration으로 해결한다.
