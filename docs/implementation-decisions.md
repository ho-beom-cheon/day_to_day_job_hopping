# 구현 결정

- 빈 저장소이므로 backend/frontend/infra 분리. Maven Wrapper + npm lockfile 사용. Java 21을 고정한다.
- Spring Boot 3.5 계열은 Java 21을 지원하고 Jackson 2 기반의 명시적 ID serializer 구성이 가능하다. 실제 사용 버전은 pom.xml에 고정한다. [공식 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- Next.js App Router, TypeScript strict, Tailwind, TanStack Query, shadcn 계열 cva/Slot 기반 공통 컴포넌트 사용. build와 lint를 별도 실행한다. [공식 설치 문서](https://nextjs.org/docs/app/getting-started/installation)
- OpenAPI가 없는 동안 route/controller와 추정 ErrorEnvelope를 만들지 않는다. Spring 기본 로그인/로그아웃 UI도 공개 계약으로 노출하지 않는다.
- 기술 인프라 선택은 업무 계약 결정과 구분한다. Session JDBC 공식 스키마는 업무 46개 테이블과 별도다.
- 루트 화면은 실제 학습 데이터가 없는 시작 화면이다. 설계 시안의 예시 점수/진척도를 사용자 데이터로 표시하지 않는다.
- npm registry에서 Next 16.3.5 / React 19.3.0 확인 후 lockfile 고정. Vitest 3 계열 설치 시 발견한 advisory 2건은 5.0.1로 올려 해소했다. ESLint 10.11.0은 Next의 React plugin에서 `getFilename` 런타임 오류가 발생하여 작동 검증한 9.39.5로 고정했다. ESLint 9 지원 종료 경고는 남으며 React plugin의 ESLint 10 호환성 확보 후 올린다.

## 개발환경 운영 결정 (추가)

- 로컬 DB 설치 없이 Compose가 기본 실행 경로다. 기존 호스트 Maven/npm 명령은 선택적인 개발/테스트 도구이며 새 PC의 필수 설치 항목이 아니다.
- 이미지 digest는 multi-platform manifest 단위로 고정한다. 버전 업데이트는 두 PC의 공통 Git 변경으로 수행한다. Docker Desktop/Engine은 OS별 설치물이며 동일 Linux container image/runtime을 사용한다.
- readiness는 PostgreSQL → Backend → Frontend → Nginx 순서로 확인한다. [Compose 공식 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/)
- Health는 별도 내부 management port의 Spring Actuator health만 사용하고 Nginx에서 외부에 노출하지 않는다. DB indicator를 포함한 UP을 요구한다. `/api/v1` 계약은 그대로 둔다. [Spring 공식 Actuator 문서](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- 개발 seed는 `dev-seed` Spring profile에만 추가 Flyway location을 등록한다. 현재 업무 schema/콘텐츠가 없어 seed 데이터 자체는 추가하지 않는다.
