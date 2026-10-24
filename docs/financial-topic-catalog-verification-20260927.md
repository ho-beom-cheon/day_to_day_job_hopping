# 금융 백엔드 실제 학습 원문 보강 검증

## 바로 확인

[대표: 실사용 133개 주제 선택](https://hocheon.tail4ddad6.ts.net/learning/extra) · [실사용 26주 주제 목록](https://hocheon.tail4ddad6.ts.net/learning/program)

[QA 26주 실제 주제 목록](http://localhost:18081/learning/program) · [QA 133개 주제 선택](http://localhost:18081/learning/extra) · [QA 새 AI 학습 본문](http://localhost:18081/workspace/learning/51) · [QA 실제 생성한 AI 문제 7개](http://localhost:18081/practice/ai/1)

2026-09-27 19:41 KST 기준 QA 실행 중. 위 네 페이지를 브라우저로 직접 확인했다. 포트 18081은 로컬 전용이며 기존 QA 자동 로그인 계정 `과정 검증 사용자`를 사용한다. 실사용 계정 기록과 분리되어 있다. 생성 검증 후 QA의 임시 OpenAI 연결은 제거했다. 저장된 문제 조회·풀이는 가능하지만 QA에서 추가 외부 AI 생성은 활성화하지 않았다.

프로젝트 루트에서 QA 재시작:

```powershell
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml -f compose.study-program-qa.yaml up -d --no-build --wait
```

선행 조건은 Docker Desktop, 이번에 빌드한 QA 이미지, 기존 QA DB와 추적 제외 설정 `.env.course-qa.local`이다. 비밀값은 기록하지 않는다.

[실사용 로컬 추가 학습](http://localhost:8080/learning/extra)은 2026-09-27 19:44 KST 기준 배포 완료, HTTP 200이다. 처음에는 자동 승인 검토가 실사용 서버 재시작/DB 마이그레이션을 별도 승인 부재로 거절했다. 이후 사용자가 **“실사용 앱에도 배포해”**라고 명시적으로 승인하여 정상 배포했다. 우회 실행은 하지 않았다.

실사용은 127.0.0.1:8080과 기존 Tailscale Funnel HTTPS 443을 사용한다. 공개 두 페이지는 실제 브라우저에서 정상 접속하여 Google 로그인 필요 화면까지 확인했다. 인증된 실사용 화면의 133개 표시는 이 브라우저에서 확인하지 못했으며, 동일 배포본 QA 화면과 실사용 DB의 카탈로그 조건으로 교차 확인했다. PowerShell HTTPS 요청은 로컬 인증서 체인 오류가 있었지만 브라우저에서는 인증서 경고 없이 열렸다. 인증서 검증을 우회하지 않았다.

실사용 로그인은 `.env`의 `GOOGLE_ALLOWED_EMAILS`에 등록된 기존 Google 계정을 사용한다. 호스트 PC, Docker Desktop, Tailscale/Funnel이 켜져 있어야 다른 기기에서도 접속된다. 로그인 후 `더 공부하기 → 다른 분야 공부하기 → 분야/주제 선택 → 추가 학습 시작 → AI 확인 문제` 흐름으로 확인한다. 별도 사용자의 세션이나 답안을 만들어 검증하지 않았다.

실사용 재시작 명령(프로젝트 루트, Docker Desktop과 기존 `.env` 필요):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public
```

## 실제 추가한 자료

`content/financial-backend-v1/catalog/`의 7개 원고 파일에 분야마다 14개씩, 98개 신규 원문을 작성했다. 기존 35개를 유지하여 QA에서 다음 수를 확인했다.

| 분야 | 기존 | 신규 | 선택 가능 |
| --- | ---: | ---: | ---: |
| Java | 20 | 14 | 34 |
| Spring | 3 | 14 | 17 |
| SQL | 3 | 14 | 17 |
| 알고리즘 | 3 | 14 | 17 |
| 금융·여신 | 3 | 14 | 17 |
| 면접 | 3 | 14 | 17 |
| AI 개발 활용 | 0 | 14 | 14 |
| 합계 | 35 | 98 | 133 |

- 제목만 추가한 항목이 아니다. 각 원문에 상황·개념, 구체적인 판단 자료, 수정과 영향, 실습과 해설, 설명 연습, 공식 참고 링크를 넣었다. 완성 본문은 906~1,347자이며 주제별 기본 확인 문제 1개를 함께 등록했다.
- Java 동시성/JVM/비동기, Spring 트랜잭션/잠금/Outbox/배치, SQL 실행 계획/격리/대사, 알고리즘의 경계·반례, 여신 흐름/상환/한도/마감 등을 포함한다.
- GA 채널 유지보수·코어 API 연계, KDB 산업은행 조회 개선, 아리수본부에서 Cursor/Codex를 사용한 개발 경험을 반영했다. 여신 설계 가정과 실제 경력을 구분한다. 미확인 성과 수치나 직접 수행하지 않은 코어 업무는 경력으로 만들지 않았다.
- AI에는 모델/앱/에이전트, 컨텍스트, 스킬, MCP, RAG, 구조화 출력, 평가셋, 개발 검증, 인젝션, 비용, 서술형 채점 기준을 포함한다.
- 98개를 기초→실무→심화로 정렬하고 26주에 주당 3~4개, 최소 3개 분야가 섞이도록 배치했다. 이는 **선택 학습 권장 경로**다. 기존 정규 130일 과정을 자동 교체한 것이 아니다.
- 작성·검토 주체는 AI이며 형식·수치·사례 자동 검증을 추가했다. 사람 검수 또는 모든 환경의 코드 실행 확인을 뜻하지 않는다.

## 연결과 데이터 보존

- V29는 새 원문·기본 문제·단계/권장 주차 메타데이터를 추가한다. 기존 발행 버전·학습 배정·답안·시험 기록을 수정하지 않는다.
- V30은 기존 15분 기록을 유지하면서 90/120분을 허용한다. 선택 UI는 30/60/90/120분을 제공한다.
- 분야/제목·목표 검색/학습 단계 필터, 목표 미리 보기, 주차 목록→주제 사전 선택 연결을 추가했다.
- QA에서 새 MCP 주제 30분 세션 51을 생성하고 원문 표시와 AI 문제 생성을 확인했다. 90분/120분 세션 생성 API도 각각 세션 52/53으로 성공하여 DB 제약까지 확인했다.
- QA의 기존 50개 학습 항목, 14개 기본 답안, 2개 시험 응시의 행 전체 해시는 마이그레이션 전후 및 검증 후 동일했다. 새로 만든 QA 세션은 별도 추가되었다.

| 기존 QA 데이터 | 행 수 | 변경 전후 MD5 |
| --- | ---: | --- |
| learning_day_item (기존 id ≤ 50) | 50 | a1b25cada68543c20862b72a166dccaf |
| problem_attempt | 14 | 65427dcf98995c60ac11ef05ca8a583f |
| test_attempt | 2 | ba984ccb4e9559088d9d4bae1b82b170 |

실사용 DB의 사전 백업은 `backups/daily-career-public-20260927T192752-a81db389.dump`이며 아카이브 읽기와 복사본 해시를 확인했다. 백업은 Git 추적 제외이며 내용은 문서/채팅에 노출하지 않았다. SHA-256: `1801BAE2DAC15FFEAD856756AD0648E2BC03D9C26FAFD81BC0C7B3B202910625`.

승인 후 실사용 배포에서도 기존 기록의 내용이 동일했다:

| 실사용 데이터 | 행 수 | 변경 전후 MD5 |
| --- | ---: | --- |
| learning_day_item | 83 | 0a691f8f5b1b7fd502eefe764f13dc00 |
| problem_attempt | 17 | 40ff30f905bdc781db6b461be11b274a |
| test_attempt | 3 | a5019b15eb2e52673349e0519e2ca36a |

실사용 API와 같은 공개 자료 조건으로 133개(위 표와 같은 분야별 수), 신규 경로 98개/26주를 확인했다. `public.flyway_schema_history`에서 V29/V30 성공을 확인했다.

## 검증 결과

- `python content/financial-backend-v1/build_catalog.py --check`: 98개 원고와 V29 일치, 주제/본문 중복·선지 정렬 검사 통과.
- `python content/financial-backend-v1/verify_examples.py`: 금액·이자·상환·한도·JOIN 합계·이진 탐색·누적합·BFS·DP·재시도 및 반례 검산 통과.
- 63개 공식 출처 키 중 62개 URL HTTP 200. MIT 알고리즘 강의 링크는 이 환경의 Python 인증서 체인 검사에서 실패하여 웹 도구로 공식 페이지를 열어 확인했다. TLS 검증을 해제하지 않았다.
- 백엔드 Docker JDK 21 `sh mvnw -B -ntp verify`: 131개 테스트 통과. 모든 신규 본문이 30/60/90/120분, 난이도 3~5의 AI 원문 길이 조건을 충족한다. PostgreSQL Testcontainers 전체 통합 프로필은 실행하지 않았다.
- 프런트: 타입 검사·ESLint·45파일 185테스트·Next production build 통과. 추가 학습 관련 8테스트에 90분 요청, AI 분야 검색/단계 필터/초기화를 포함했다.
- QA 실제 PostgreSQL에 V29/V30 적용 후 서버 정상 기동, 133개 카탈로그 및 98개/26주 경로 API 확인.
- 실사용 `docker compose -f compose.yaml -f compose.public.yaml up -d --no-build --wait backend frontend nginx` 성공, Nginx upstream reload 완료. 네 컨테이너 모두 healthy. `docker compose -f compose.yaml -f compose.public.yaml exec -T frontend node scripts/smoke.mjs`로 프런트→백엔드 건강/DB UP, Nginx→프런트/API, 관리 경로 비공개 검사 통과.
- 실제 브라우저에서 주차 링크의 주제 사전 선택, 원문과 실습 표시, 7개 문제 저장, 답안 제출 후 정답/해설/원문 근거 표시 확인.
- 추가 학습과 주차 목록을 390px viewport에서 확인: CSS clientWidth/scrollWidth 모두 375px, 수평 넘침 없음. console error/warn 0개. 검사 후 viewport 원복.
- 화면 증거: `output/financial-catalog-qa-20260927.png`.

### 실제 OpenAI 호출

기존 OpenAI 모델 설정을 사용해 QA에서 새 MCP 원문 1건으로 문제 7개를 생성했다. 작업 1은 `SUCCEEDED`, 유효 문항 7개, 거절 0개, 모델 `gpt-6-luna`, 입력 2,122 / 출력 2,693토큰이었다. 키 값은 출력하거나 파일에 저장하지 않았다. 검증 후 QA를 원래 환경 설정으로 재생성하고 키 부재를 확인했다.

7개 문항을 읽어 원문 범위와 선택지 판단을 검토했다. 문서의 지시/데이터 구분, 최신성, 권한과 대상 범위, 연결 프로토콜과 업무 기능의 구분을 다룬다. 일부 상위 난이도 문항도 비교적 명확한 선택지이며 6·7번의 검토 범위가 겹친다. 따라서 **외부 생성·저장·응답 흐름 성공**을 전체 분야의 출제 품질이나 난이도 5의 충분한 깊이가 검증되었다는 뜻으로 쓰지 않는다. 문항 1에서 답 2를 제출해 정답·해설·원문 발췌를 확인했고, 미제출 문항의 정답/해설/발췌는 응답에서 숨겨진다.

## 아직 별개로 남은 범위

이번 보강은 사용자가 지적한 분야별 학습 원문 부족을 해결하고 선택 학습/AI 연습에 연결한 작업이다. 정규 130일 자동 배정, 단답·면접 답변 시험 UI, 주간·월간·최종 통합 평가/재응시 저장 연결은 이전 설계 문서에 남아 있는 별도 구현이다. 98개의 짧은 사례 원문을 6개월 전체 강의의 모든 깊이를 완성한 것으로 표현하지 않는다. 이번 변경은 명시적 승인 후 실사용 배포까지 완료했다.

기존 staged/unstaged/untracked 작업은 유지했다. 이번 요청에서 커밋·푸시·PR·원격 이슈 생성은 하지 않았다.
