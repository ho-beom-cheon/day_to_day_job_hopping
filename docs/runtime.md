# 실행 및 검증

## 현재 구현 범위

프로젝트 골격과 공통 기반이다. 로그인/과정/학습/시험 API는 아직 없다.
시작 화면의 학습 버튼은 준비 중으로 비활성화되어 있다. 실제 API 계약을
추정한 mock 서버나 가짜 진행률은 제공하지 않는다.

## Frontend

Node.js 22.13 이상(현재 검증 22.22.0)에서:

```powershell
cd frontend
npm ci
npm run typecheck
npm run lint
npm test
npm run build
npm run dev
```

브라우저: http://localhost:3000
실제 API 연동은 같은 origin의 Nginx `/api/` 프록시를 사용한다.
Next dev 단독 실행에는 API 프록시를 임의 추가하지 않았다.

## Backend

Java 21에서:

```powershell
cd backend
.\mvnw.cmd -B -ntp verify
# Docker가 동작하는 환경에서 반드시 별도 실행:
.\mvnw.cmd -B -ntp -Ppostgres-it verify
```

Linux에서는 `sh mvnw -B -ntp verify`와 `sh mvnw -B -ntp -Ppostgres-it verify`.
일반 verify는 8개 단위/MVC 테스트와 실행 JAR 빌드, postgres-it은 실제
PostgreSQL 17의 clean Flyway migration, 애플리케이션 기동, JDBC 세션 저장/조회/삭제를 검사한다.
Docker가 없으면 통합 프로필은 실패한다. `skip`을 성공처럼 처리하지 않는다.

## Docker Compose / 단일 VM

1. `.env.example`을 `.env`로 복사하고 DATABASE_PASSWORD를 로컬에서 설정한다. 자세한 새 PC 절차는 README에 증분 추가했다.
2. 로컬 HTTP 개발 템플릿은 SESSION_COOKIE_SECURE=false이며 운영 HTTPS에서는 true로 변경한다.
3. `docker compose config --quiet`로 검사하고 `docker compose up --build -d` 실행.
4. `docker compose ps`, `docker compose logs backend`로 상태와 Flyway 적용 확인.
5. http://localhost:8080 접속. 종료는 `docker compose down`. DB volume 삭제 옵션은 사용하지 않는다.

PostgreSQL/Backend/Frontend는 host port를 열지 않고 Nginx만 기본 loopback에 노출한다. HTTP_BIND_ADDRESS/HTTP_PORT로 조정한다.
VM 운영 배포는 HTTPS 종단과 도메인 설정을 추가하고 secure cookie를 유지해야 한다.
현재 Nginx 설정은 로컬 HTTP 기본 구성이다. 아직 TLS/OIDC를 포함한 운영 배포 검증은 아니다.
OIDC redirect/login/logout 및 CSRF 토큰 경로는 OpenAPI 원본 확인 후 Nginx와 Spring에 함께 반영한다.

## 미완료 게이트

ErrorEnvelope/공개 DTO와 OIDC/로그아웃/CSRF 경로: GAP-001.
업무 DB migration: GAP-002. Docker 환경: GAP-005.
현재 로컬 결과와 수정 이력은 implementation-progress.md 참고.

## 추가: 회사/집 재현 기준과 Phase 1 실행 게이트

DB는 Docker 전용이다. 위 호스트 Maven/npm 명령은 선택적인 코드 검증용이며 새 PC에서는 README의 Compose 절차가 기본이다. 호스트에서 Backend를 직접 띄울 경우 별도 Docker DB 접속 구성이 필요하며 로컬 PostgreSQL 설치를 전제로 하지 않는다.

고정 이미지 목록/플랫폼은 `infra/images.lock.json`에 기록했다. Java build/runtime은 동일 Temurin 21 patch, Maven은 Wrapper 3.9.9, Node는 22.22.0이며 Compose의 DB/Proxy 이미지도 digest로 잠갔다. digest 변경은 두 PC에 반영할 Git 변경으로 관리한다.

### Health / readiness

- PostgreSQL: pg_isready 통과.
- Backend: 내부 9090 포트 `/actuator/health`가 DB indicator를 포함해 200/UP. 외부 port publish 없음, 상세/컴포넌트 노출 없음.
- Frontend: 컨테이너 내부 HTTP 3000 시작 화면 200.
- Nginx: Backend/Frontend healthy 후 시작, 내부 HTTP probe 통과.
- `docker compose exec -T frontend node scripts/smoke.mjs`: Frontend runtime에서 Backend health 호출, Nginx → Frontend, Nginx → 보호된 API prefix(401 + traceId), 외부 management 404 검증.

이 health는 인프라 전용이며 canonical `/api/v1`에 새 endpoint를 추가하지 않는다. 브라우저→공개 Health API 검증은 OpenAPI 원본 확인이 필요하다.

### Seed / 독립 Volume

`APP_PROFILES=dev,dev-seed`일 때만 `classpath:db/dev-seed`를 추가한다. 현재 확정 업무 데이터가 없어 SQL seed는 비워 둔다. 승인된 공통 fixtures가 생기면 이 경로에 멱등적인 migration을 추가하고 두 PC에서 같은 파일을 실행한다. 적용한 seed profile은 해당 개발 volume 수명 동안 유지한다. 중간에 profile을 제거하면 Flyway 이력 누락 오류가 생길 수 있으므로 seed 없는 개발 DB가 필요하면 폐기 가능한 개발 volume을 명시적으로 초기화한다. 운영에서 dev-seed를 활성화하지 않는다.

Named volume은 Docker host마다 독립적이다. 같은 PC에서 clone을 여러 개 운영하면 COMPOSE_PROJECT_NAME과 DB_VOLUME_NAME을 둘 다 다르게 지정한다. 운영 환경파일은 APP_PROFILES=prod, 고유 project/volume, 운영 password/domain, HTTPS용 cookie 값을 사용한다. 실제 파일은 Git 밖에 둔다. PostgreSQL 초기 계정 변수는 빈 volume 최초 생성에만 적용된다.

### 추가 인수 항목

실제 container 4개 정상 실행, Flyway 성공, Backend→DB, Frontend runtime→Backend health, 재시작 뒤 DB 보존을 모두 확인해야 Phase 1 환경 게이트를 통과한다. YAML 검증/단위 테스트만 통과한 경우 실제 실행 PASS로 표기하지 않는다.
