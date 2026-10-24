# 개인 AI 학습자료 생성 검증 — 2026-09-27

## 접속

- 대표 실사용 화면: [더 공부하기](https://hocheon.tail4ddad6.ts.net/learning/extra)
- 로컬 실사용 화면: [localhost:8080/learning/extra](http://localhost:8080/learning/extra)
- QA 화면: [localhost:18081/learning/extra](http://localhost:18081/learning/extra)
- QA 생성 원문: [학습 세션 54](http://localhost:18081/workspace/learning/54)
- QA 생성 문제: [AI 문제 7개](http://localhost:18081/practice/ai/8)

2026-09-27 20:29 KST 기준 실사용/QA 실행 중. 실사용 HTTPS 페이지의 Google 로그인 화면, 로컬 페이지 HTTP 200, 비로그인 API HTTP 401을 확인했다. 실사용 로그인 후의 유료 생성은 반복하지 않았다. 실제 생성·저장·학습·문제 풀이 검증은 격리된 QA에서 수행했다.

실사용은 기존 허용 Google 계정과 진행 중인 학습 과정이 필요하다. Docker Desktop과 기존 로컬 `.env`가 있어야 하며 키/로그인 허용 설정은 이 파일에서 관리한다. 비밀 값은 보고서에 포함하지 않는다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public
```

QA는 `.env.course-qa.local`이 필요하며 검증 사용자로 자동 접속한다.

```powershell
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml -f compose.study-program-qa.yaml up -d --no-build --wait
```

실제 OpenAI 검증을 위한 QA 임시 키 주입은 종료 후 제거했고 `OpenAIKeyAbsent`를 확인했다. QA에는 생성 완료 자료와 풀이 기록이 남으며, 추가 유료 생성 검증은 기존 안전한 키 주입 절차가 필요하다.

## 사용자 흐름

1. 기존 검색은 저장된 자료를 검색한다.
2. `AI로 새 주제 만들기`에서 검색어·분야·학습 단계·30/60/90/120분을 지정하고 명시적으로 생성한다.
3. 기존 자료를 선택한 뒤 `이 주제 더 깊게 만들기`를 누르면 원문을 기반으로 심화 자료를 요청한다. 원본 분야는 고정되고 원하는 세부 주제·단계·시간은 조절할 수 있다.
4. 작업은 서버에 저장되어 새로고침 후 확인할 수 있다. 동일 사용자/과정/주제/단계/시간/기반 자료/모델/프롬프트 버전으로 진행 중이거나 성공한 작업은 재사용한다.
5. 완성한 자료는 `내 AI 학습자료`와 기존 주제 검색에 나타난다. `이 자료로 학습 시작`으로 기존 추가 학습 세션을 만들고 기본 문제 또는 AI 확인 문제를 푼다.

생성 자료는 사용자 소유다. 공유 카탈로그 133개, 기존 원문, 정규 일정은 수정하지 않는다. 생성에 보내는 정보는 요청한 주제·분야·단계·시간과 선택한 기반 원문/출처다. 개인 답안·정답률·과정 식별자는 공급자 입력에서 제외한다.

## 원문과 문항 검사

- `LEARNING_CONTENT`, 프롬프트 `personal-learning-v2`, 기존 OpenAI 모델 설정 재사용.
- 공식 도메인 웹 조회를 요청하고 검색 결과/인용/완료된 페이지 조회에 나타난 URL과 출처 목록을 대조한다. 모델이 텍스트에 URL을 적었다는 이유만으로 인정하지 않는다.
- HTTPS와 허용 공식 호스트, 분량, 필수 설명/사례/수정·영향·검증/실습/해설, 코드·로그 블록, 기본 문항 2개의 구조를 확인한다.
- 핵심 판단 근거는 본문에 포함하고 문항이 실제 항목 번호를 참조한다. 없는 항목 번호는 거부한다. 서버가 같은 원문을 해설 근거에 연결하므로 모델의 재서술을 원문 인용으로 저장하지 않는다.
- 정답 위치·보기·중복 질문·근거 참조를 검사한다. 자동 검사는 사실의 진위, 모든 코드의 실행 결과, 정답의 의미적 유일성을 보증하지 않는다. 화면과 본문에 AI 생성/자동 검사/오류 가능성을 명시한다.
- 실패 자료는 발행하지 않으며 일반 조회 API에 실패 원문/답안을 노출하지 않는다. 토큰 사용량은 기록하며 결과 불명 상태는 자동 재요청하지 않는다.

API 형식 확인 자료: [OpenAI 웹 검색](https://developers.openai.com/api/docs/guides/tools-web-search), [구조화 출력](https://developers.openai.com/api/docs/guides/structured-outputs).

## 검증 결과

- 백엔드 `docker build --target build -t daily-career-study-policy-check ./backend`: **140 tests, 0 failures/errors**, Maven verify 및 빌드 통과.
- 프런트 `docker build --target runtime --build-context course_examples=./content/java-four-week-v1/examples -t daily-career-study-frontend ./frontend`: typecheck, ESLint, **46 files / 190 tests**, Next production build 통과.
- 브라우저에서 검색 결과 0개인 새 주제 입력, 기존 주제 심화 시 분야 고정, 4개 시간 옵션, 새로고침 후 작업 복구, 완성 자료 검색 확인.
- 390px 모바일 viewport에서 문서 폭 375px, 가로 넘침 없음. 생성 입력에 자동 포커스 및 화면 이동 확인. 풀이 화면 콘솔 error/warn 없음.
- 알 수 없는 `answers` 입력은 HTTP 400, 접근할 수 없는 기반 ID는 HTTP 404로 거부. 단위 테스트에서 권한 확인 전 기반 본문을 읽지 않는 경계도 확인.
- 실제 OpenAI 새 원문 **job 6 → version 134**: SQL/실무/30분, 본문 5,729자, 공식 출처 4개, 기본 문항 2개. 소유자 QA 사용자 1, 공유 카탈로그 등록 0건.
- 실제 심화 **job 9 → version 135**: 기반 version 134, SQL/심화/30분, 본문 5,788자. 기존 자료를 변경하지 않고 새 버전의 독립 자료로 저장.
- 새 원문으로 **세션 54 / 배정 콘텐츠 28** 시작. 기본 문제 384에 답안 제출 후 정답·해설·근거 표시 및 제출 완료 상태 확인.
- 해당 원문으로 **AI job 8의 7문항** 생성 성공. 첫 답안 저장 후 `제출 1 / 7문제`, 정답 및 본문 근거 확인.
- 진행 중 동일 조건 재요청은 job 5, 성공 후 동일 조건 재요청은 job 6을 재사용했다. 새 유료 작업을 만들지 않았다.
- 검증 중 job 2/3/4/5/7은 출처 또는 초기 근거 계약 검사에서 거부되었다. 완성 자료로 우회 발행하지 않았다. 직인용 재서술 문제는 본문 항목 참조 계약으로 개선했고, 검색 목록만 읽던 출처 수집은 완료된 페이지 조회까지 처리하도록 보완했다. 현재도 출처를 확인할 수 없는 출력은 실패할 수 있다.
- 실제 유료 검증 범위는 SQL 새 주제/심화 30분 사례다. 모든 분야·시간 조합을 실제 유료 호출로 검증했다는 의미는 아니다.

QA 호출별 기록(이전 검증 job 1 제외):

| Job | 종류 | 결과 | 입력 토큰 | 출력 토큰 |
| --- | --- | --- | ---: | ---: |
| 2 | 원문 | 검사 거부 | 22,590 | 4,538 |
| 3 | 원문 | 검사 거부 | 25,964 | 4,169 |
| 4 | 심화 | 검사 거부 | 20,553 | 5,971 |
| 5 | 원문 | 검사 거부 | 30,750 | 4,462 |
| 6 | 원문 | 성공 | 26,554 | 4,267 |
| 7 | 심화 | 검사 거부 | 22,509 | 5,880 |
| 8 | 확인 문제 | 성공 | 4,558 | 2,889 |
| 9 | 심화 | 성공 | 23,023 | 4,446 |

웹 검색 비용을 포함한 최종 청구액은 이 토큰 표만으로 산정하지 않는다. 기존 사용자 비용 확인 방식을 유지한다.

## 실사용 적용과 데이터 보존

사용자의 기존 실사용 배포 승인 및 이번 기능 추가 요청에 따라 이미지를 빌드하고 backend/frontend를 교체했다. V31 마이그레이션 성공, 전체 서비스 healthy, Nginx reload, `frontend node scripts/smoke.mjs` 통과를 확인했다.

백업: `backups/daily-career-public-20260927T202328-5bbdecc6.dump`

SHA-256: `0AE2F5516B17BE49A0E4946DBCAB82612AA557A9A0DBBC3A80BF19B1FA54B2F4`

배포 전후 `md5(string_agg(to_jsonb(t)::text,'' order by id))` 값 동일:

| 테이블 | 행 수 | MD5 |
| --- | ---: | --- |
| learning_day_item | 83 | 2fc0960803f6c19edc298c34ef4598c4 |
| problem_attempt | 17 | 8937bc7765e1d6ea1a3ae507b823c567 |
| test_attempt | 3 | 87020a37df0a561ac40c574e6d10ca52 |

실사용 개인 생성 자료는 0건으로 시작한다. QA에서 만든 2개 자료/테스트 답안은 실사용에 복사하지 않았다. 이번 작업은 추가 학습 원문 생성 기능이며, 130일 정규 원문이나 일간/주간/월간/최종 평가 전체 통합 완료를 뜻하지 않는다.
