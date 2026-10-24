# Java 4주 과정 기술 검수 기록

- 관련 이슈: [#9](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/9).
- 검수일: 2026-09-26 KST. 작성·기술 점검: Codex. 독립적인 사람의 교육 검수 및 학습 시간 실측은 미실시.
- 코드/버전: `JAVA-CORE-4W` / `2026.09.1`.
- 정규화 원고 SHA-256: `467d3dd1793beeb2426e420531877b7b74844d9135fdd25ab5ce2304dc5dcf80`.
- 20일·20세션, 확인 문제 200개, 평가 은행 40개, 일간 20·주간 4·월간 1·최종 1 평가.
- 범위와 해석 제한은 [평가 정책](assessment-policy.md)에 기록했다. 기존 6개월 자료와 사용자 기록을 대체하지 않는다.

## 근거

- Java 21에서 `examples/Lesson01.java`~`Lesson20.java` 실행: 20개 PASS. 각 예제는 핵심 결과를 assertion으로 검사한다. 로컬 로그 `output/course-examples-20260926.log`.
- 각 원고의 Oracle Java 21 공식 문서 링크를 열어 참조 계약을 확인했다. 컬렉션, equals/hashCode, record, BigDecimal, 예외, Clock, 동시성 및 JVM 명세 범위다.
- `sh mvnw -B -ntp -Ppostgres-it -Dcatalog.test-file=/course/course.json verify`: 단위 74개·PostgreSQL 통합 68개 PASS. Docker의 읽기 전용 `/course` mount를 사용한다. 최종 로그 `output/course-postgres-it-final-20260926.log`, 완료 17:51 KST.
- 통합 테스트에서 신규 과정 생성 → 20일 배정 → 확인 문제 200개 제출 → 오답 재풀이·해결 → 20세션 완료 → 26시험 제출·실제 채점 worker 100점 → 완료율 100%·학습 시간 1,800분·연속 20일 조회를 검증했다. 날짜는 테스트 전용 Clock을 진행시켰으며 실제 4주간 학습한 결과가 아니다.
- 같은 원고 재적재, 새 버전, 잘못된 정답/참조/일수, 중간 적재 실패 rollback, DB 본문 변조, 미검수 발행 차단 및 기존 6개월 호환 검증 PASS.
- 프런트엔드 typecheck·lint·Vitest 156개·Next production build PASS. raw HTML/위험 링크 렌더링 방지와 시험 목록의 후속 페이지 실패도 검증한다.
- 별도 DB의 브라우저 QA: 신규 온보딩, 첫날 10문항, 오답 메모·정답 재풀이, 콘텐츠 완료·90분 세션 완료, 시험 답안 저장·제출·자동 채점 100점·재접속 확인. 홈에서 1/20, 5%, 90분, 연속 1일 확인.

## 발행

- QA DB: templateId `1`, PUBLISHED. [QA 로드맵](http://localhost:18081/roadmap).
- 기존 공개 서버: templateId `2`, PUBLISHED, 2026-09-26 17:56:28 KST. V19 migration 및 운영 프런트엔드 반영 완료. 기존 templateId `1`의 6개월 과정과 공존한다.
- 기존 학습 관련 7개 테이블의 배포 전후 행 수·행 내용 digest가 모두 일치했다. 기존 계정으로 공개 홈에 다시 로그인해 2/6세션과 75분 기록 조회를 확인했다.
- 390×844 라이트·다크 및 1280×900 라이트 학습 화면 확인. 페이지 가로 넘침 없음(375/390, 1265/1280). 긴 코드는 299px 블록 내부에서만 스크롤되며 키보드 방향키와 포커스 표시를 확인했다. 전체 스크린리더 감사 완료를 뜻하지 않는다.
- 새 과정 자동 배정이나 기존 과정 초기화를 수행하지 않는다. 기존 사용자는 현재 배정을 유지한다.
