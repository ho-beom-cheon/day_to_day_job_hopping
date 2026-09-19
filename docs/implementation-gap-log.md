# 구현 Gap Log

| ID | 근거 / 누락 | 영향 | 처리 |
|---|---|---|---|
| GAP-001 | API 요약과 ORIGINAL_FILE_STATUS는 File Library의 openapi.yaml을 참조하지만 ZIP/저장소에는 없음 | Phase 1 ErrorEnvelope 정확한 shape, Phase 2 인증/CSRF/로그아웃 경로, Phase 3–9 모든 공개 API/DTO/enum/required/nullable/status | 공개 엔드포인트와 schema를 추정하지 않는다. transport는 payload unknown 및 decoder 경계만 준비. 실제 v1.2.1 원본 필요 |
| GAP-002 | DB v0.2는 46테이블/558컬럼/105FK 규모·원칙만 제공 | 업무 JPA entity, repository, Flyway, outbox/idempotency 영속 구현 | 업무 테이블 생성 보류. Spring 공식 Session JDBC 인프라 스키마만 별도로 적용 가능. DB v0.2 원본 필요 |
| GAP-003 | 정적 검증 보고서: mastery 가중치/결합 산식 미확정 | Phase 7 계산 | 임의 산식/0% 대체 금지. 공개 null 표현도 원본 nullable 확인 후 적용 |
| GAP-004 | 이미지 매니페스트/시작 README는 바이너리 미포함 주장, 실제 15 PNG 존재 | 문서 충돌 | 해결: PACKAGE_STATUS와 실제 PNG를 우선. 원본 문서는 수정하지 않음 |
| GAP-005 | 현재 Docker CLI/실행 환경 없음, PostgreSQL CLI 없음 | clean PostgreSQL/Flyway/Testcontainers 및 Compose 실행 | `docker compose config` 명령 미발견. `mvnw -Ppostgres-it verify`에서 Docker 환경 미발견으로 IT 1개 ERROR. 단위 테스트/일반 build PASS와 구분. Docker 지원 환경에서 재검증 필요 |
| GAP-006 | 기능 요약은 6개월 과정만 설명하며 실제 콘텐츠/배정 규칙/문항 원본 없음 | Phase 3 이후 실제 학습 데이터 | 데모 데이터를 실데이터처럼 만들지 않는다 |
| GAP-007 | 패키지 CHECKSUMS.sha256이 자기 자신을 체크섬 대상으로 포함 | 패키지 무결성 감사 | 자기 참조 1건 불일치, 나머지 41건 일치. 원본 ZIP/문서 보존. manifest 자체 해시로 전체 실패를 판단하지 않음 |

사용자에게 문서에 이미 있는 내용을 재질문하지 않는다. 위 누락은 패키지의 과거 완료 선언과 별개로 실제 파일 부재를 근거로 기록했다.

## 개발환경 운영 기준 반영 후 추가 기록

- GAP-005 추가 확인: WSL 실행 환경도 설치되어 있지 않다. 시스템 설정 변경/설치/재부팅은 수행하지 않았다. 공식 Docker Compose 5.5.1 독립 CLI를 `.tools/`에 받아 배포 SHA256 검증 후 개발/운영 변수 조합의 `config` 검증은 통과했다. `up -d --build`는 `docker_engine` named pipe 부재로 실패했다. PostgreSQL을 호스트에 설치하는 우회 방식은 사용하지 않는다.
- Health 확인 요구는 외부 비공개 management 9090 `/actuator/health`와 Frontend 컨테이너 실행 스크립트로 구현했다. Nginx는 management 경로를 차단한다. 새 공개 업무 API를 만들지 않았으며 브라우저용 공개 Health 계약은 GAP-001의 원본 확인 대상이다.
- GAP-002/GAP-006은 그대로 유지한다. `dev-seed` profile과 migration 디렉터리만 준비했으며 미확정 업무 테이블이나 seed 콘텐츠는 생성하지 않았다.
