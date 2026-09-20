# 작업 3 완료 — PostgreSQL 업무 스키마·Flyway

2026-09-20 / [이슈 #2](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/2)

학습 과정·일정·콘텐츠·문제·시험·답안·채점·복습·AI·알림·외부 동기화의 DB 기반을 만들고, 실제 개발 PostgreSQL에 적용했다. 업무 API와 화면은 후속 단계다. 사용자의 '한 작업씩' 진행 방식에 따라 이번에는 3번만 완료했다.

## 구현과 설계 보완

- `V1__spring_session.sql`은 수정하지 않았다. `V2__business_schema.sql`은 원본 46테이블의 557컬럼, PK 46개, FK 105개, UNIQUE 제약 85개, CHECK 169개, 후보 인덱스 111개를 구현한다. 후보 중 11개는 UNIQUE 인덱스다.
- `V3__schedule_preview_and_final_test.sql`은 OpenAPI에 있는 최종시험 `FINAL`과 일정 미리보기 저장을 보완한다. 미리보기는 사용자·개인 과정 복합 FK, 일정 revision, 요청/계산 결과 스냅샷, 10분 만료와 소비 시각을 가진다.
- 업무 스키마는 `daily_career`, Session/Flyway 인프라는 `public`이다. DB 역할과 schema의 이름이 같아도 재시작 시 이력이 이동하지 않도록 `spring.flyway.default-schema=public`으로 고정했다.
- 원본 컬럼 설명 557개와 보완 컬럼 설명 10개를 모두 PostgreSQL COMMENT로 반영했다. 원본 HTML/OpenAPI 파일은 변경하지 않았다.
- [DB 채택 기준](contracts/database-stage3-alignment.md)에 원본과 공개 API의 차이, 채택 이유, 이후 서비스 책임을 기록했다. 558이라는 후속 요약 수치에 맞추기 위한 임의 컬럼 추가는 없다.

## 실제 DB 집계

| 항목 | 결과 |
|---|---:|
| PostgreSQL | 17.11, 기존 digest 유지 |
| 업무 테이블 / 컬럼 | 47 / 567 |
| PK / FK / UNIQUE 제약 / CHECK | 47 / 107 / 85 / 175 |
| 인덱스 (PK·UNIQUE 포함) | 246 |
| 컬럼 COMMENT | 567 / 567 |
| public 인프라 테이블 | 3 (Session 2 + Flyway 1) |
| 업무 + 인프라 테이블 합계 | 50 |
| Flyway | V1, V2, V3 모두 성공 |

[실제 catalog 기록](audit/database-stage3-catalog-20260920.json). 운영 데이터/비밀번호/토큰은 포함하지 않는다. 개발 DB의 cluster ID `7687576957880160290`과 기존 V1 체크섬 `1873624569`가 적용 전후 유지됐다. 기존 세션 행은 적용 전 0개였으며, 데이터가 있는 업그레이드 보존은 별도 Testcontainers의 세션/속성 fixture로 검증했다. 개발 volume 삭제나 재초기화는 하지 않았다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| Docker build의 `sh mvnw -B -ntp verify` | PASS, 일반 테스트 11개, JAR 생성 |
| `sh mvnw -B -ntp -Ppostgres-it verify` | PASS, 일반 11개 + 실제 PostgreSQL IT 8개. 실패/오류/skip 0 |
| 원본 전체 catalog 대조 | PASS, 46테이블의 모든 컬럼 타입/identity/default/NULL, PK/FK/UNIQUE/CHECK, 인덱스 정의/조건/유일성 및 COMMENT |
| 보완 구조 | FINAL 저장 허용, 무효 시험 유형 거부, preview 소유권·revision·JSON 형태·10분 TTL·만료/1회 조건부 소비 검증 |
| 설치·업그레이드 | 빈 DB Spring Boot 기동, V1→V3, 기존 세션 속성 보존, 새 연결로 Flyway validate/재실행 0건 |
| DB 무결성 | 존재하지 않는 FK·타 사용자 복합 참조·중복 활성 과정/OIDC 식별·음수 revision·잘못된 상태·필수값 누락 거부 |
| 제출·채점 | 실패 run과 성공 재채점 run 이력 및 답안/제출 보존, 성공 run만 공식 지정, 공식 run 중복 거부, 만점 초과 점수 거부 |
| 멱등성/outbox | 사용자별 같은 키 중복 거부·다른 사용자 허용, 업무 변경과 outbox 동시 commit/rollback |
| 실제 개발 Compose | config PASS, Backend 재빌드/적용/재시작 PASS, 4서비스 running/healthy |
| 연결 smoke | Frontend→Backend(DB UP), Nginx 연결, 외부 management 차단 PASS |
| 원본/V1 보존 | 두 원본 SHA-256 유지, 독립 추출 목록 일치, V1 Git diff 없음 |

검증은 저장 구조와 SQL 트랜잭션 수준이다. 제출 후 수정 금지, 이전 일정 보존, preview 확인 전체 API, 외부 작업자 전달과 공개 DTO 매핑을 구현한 것으로 간주하지 않는다. 원격 CI는 실행하지 않았다.

## 재현 명령

호스트 Java 없이 프로젝트 루트에서 실행한다.

```powershell
python backend/tools/extract_database_design.py
docker build --target build -t daily-career-backend-check ./backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-backend-check sh mvnw -B -ntp -Ppostgres-it verify
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180 backend
docker compose exec -T frontend node scripts/smoke.mjs
Get-Content backend/tools/database_catalog.sql -Raw | docker compose exec -T postgres sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
```

Python 추출은 선택적인 원본 사전 갱신 도구이며 런타임 필수 의존성이 아니다. 저장된 테스트 사전은 원본 HTML SHA-256과 함께 관리한다. 적용된 migration은 재생성/수정하지 않고 다음 버전을 추가한다. `database_catalog.sql`은 읽기 전용 요약이며 전체 비교는 `BusinessSchemaIT`가 담당한다.

## 다음 작업과 남은 범위

다음은 **4번 Spring Boot 공통 기반**: OpenAPI 응답/오류, validation, ID·trace·동시성·멱등성 계약 연결이다. 아직 시작하지 않았다.

설정의 기본 휴식요일·테마·시각, 알림 집합 revision/주간 요일/시험 전 시간, 사용자 프로필 저장은 해당 기능 단계의 추가 migration 항목으로 남겼다. 숙련도 산식과 실제 콘텐츠 seed도 미확정 상태를 유지한다. 이 목록은 전체 API 저장 계약 완성으로 잘못 해석하지 않도록 [채택 기준](contracts/database-stage3-alignment.md)과 Gap Log에 연결했다.

커밋/푸시/PR은 수행하지 않았다. 기존 미커밋 변경은 보존했으며 이슈 #2는 후속 Git 반영을 위해 열어 두었다.
