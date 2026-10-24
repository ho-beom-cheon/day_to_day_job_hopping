# 문제 본문·코드·예시 표시 개선

관련 이슈: [#8 화면·접근성 회귀](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/8)

## 변경 범위

- AI 최초 풀이, 저장된 문제 재풀이·개인 시험, AI 오답 상세, 일반 문제·오답 상세, 시험 응시·결과에 `QuestionContent` 적용.
- 긴 문제를 제목 대신 본문으로 표시. 보통 굵기, 문단 여백, 한글 어절 단위 줄바꿈, 작은 화면에서도 읽을 수 있는 줄 간격.
- 코드 블록은 언어 표제와 테두리, 고정폭 글꼴, 들여쓰기를 보존. 긴 코드는 블록 안에서 가로 스크롤하며 키보드로도 접근 가능.
- AI 생성 지침과 JSON schema 설명에 문단·코드·보기·입출력 예시 표기 규칙 추가. 새 결과의 닫히지 않은 코드 fence는 품질 검사에서 거절.
- `prompt`, `choices[]`, `explanation` 문자열 API와 기존 저장 데이터 유지. 기존에 구조 없이 저장된 코드를 추측해서 다시 작성하지 않는다.
- 정답·해설 공개 시점, 채점, 재풀이 및 다음 문제 이동 정책 유지. 원시 HTML·이미지는 렌더하지 않으며 선택지 안에 외부 링크를 만들지 않는다.

## 생성·작성 규칙

지문과 최종 질문은 빈 줄로 구분한다. 짧은 식별자는 인라인 backtick을 사용한다. 코드 블록에는 `java`, `sql` 등 언어를 지정한다. 입출력은 각각 `input`, `output`, 일반 리터럴 예시는 `text`를 사용한다. 문장형 보기·조건 목록은 `>` 인용 블록으로 구분한다. JSON 안의 줄바꿈은 올바르게 escape한다.

````markdown
정산 결과에 오차가 발생했습니다. 다음 코드를 확인하세요.

```java
BigDecimal amount = new BigDecimal(0.1);
```

```output
0.10000000000000000555...
```

> ㄱ. 금액의 십진 정밀도를 보존해야 합니다.
>
> ㄴ. 반올림 시점은 업무 규칙을 따릅니다.

어떤 수정이 가장 적절한가요?
````

## 검증

- Frontend: TypeScript, ESLint, Vitest **40개 파일 / 161개 테스트**, Next.js production build 통과. `docker compose -f compose.yaml -f compose.public.yaml build frontend` 로그: `output/question-readability-build.log`.
- Backend: Docker build의 `sh mvnw -B -ntp verify` 통과. `AiPracticeQuestionsTest` 9개 통과. Windows Maven wrapper는 실행에 실패하여 컨테이너 빌드에서 검증했다. PostgreSQL 통합 테스트 프로필은 이번에 재실행하지 않았다.
- 원본 코드·들여쓰기·줄바꿈 보존, 입력/출력/보기 구분, 일반 텍스트 호환, HTML/이미지/활성 링크 차단, 닫히지 않은 fence 거절 확인.
- 코드가 포함된 선택지 제출 후에만 해설 노출, 기존 다음 문제 이동, 일반 문제 제출·완료 회귀 확인.
- 실서비스: 저장된 AI 문제 10번의 1·2번 문제, 기존 제출 해설, AI 오답, 일반 오답·문제, 시험 결과 해설 펼침 확인. 실제 풀이 기록을 추가하거나 수정하지 않았다.
- 실서비스 AI 문제: 데스크톱 2560px 및 모바일 390px에서 가로 넘침 없음. 390px viewport에서 document scrollWidth 375px. 브라우저 오류 로그 없음.
- 코드·예시 fixture: 실제 `QuestionContent`를 서버 렌더한 HTML로 1440px / 390px / 320px, 라이트·다크 확인. 320px에서 document scrollWidth 305px, 긴 코드만 `overflow-x: auto`로 내부 스크롤. 결과 HTML: `output/question-readability-preview.html`.
- fixture 검증은 실제 AI 호출 성공 증거가 아니다. 새 생성 지침의 실제 공급자 재생성 결과는 미검증이다. 기존에 형식 구분 없이 저장된 코드는 자동 재작성하지 않는다.
- 신규 응시·AI 재풀이 기록은 만들지 않았으므로 진행 중 시험·AI 재풀이 화면의 실제 제출은 이번 브라우저 검증에서 제외했다.

## 접속 정보

대표: [현재 AI 문제](https://hocheon.tail4ddad6.ts.net/practice/ai/10)

2026-09-26 18:48 KST 기준 공개 서비스 실행 중. 위 대표 주소와 아래 확인 완료 주소를 로그인된 브라우저로 직접 열었다. HTTPS 443 → 로컬 Nginx 127.0.0.1:8080, backend/frontend/postgres/nginx healthy 및 runtime smoke 통과. 실행 중 backend 이미지와 새로 빌드한 이미지 ID도 일치한다.

| 화면 | 직접 링크 | 검증 상태 |
|---|---|---|
| AI 최초 풀이 | [저장된 AI 문제 10](https://hocheon.tail4ddad6.ts.net/practice/ai/10) | 지문·기존 해설·모바일 확인 |
| 일반 문제 | [문제 4](https://hocheon.tail4ddad6.ts.net/practice/4) | 조회 확인 |
| 오답 목록 | [오답노트](https://hocheon.tail4ddad6.ts.net/review) | 일반·AI 목록 확인 |
| AI 오답 상세 | [AI 문제 10의 1번 오답](https://hocheon.tail4ddad6.ts.net/review/ai/10/1) | 지문·보기·해설 확인 |
| 일반 오답 상세 | [오답 3](https://hocheon.tail4ddad6.ts.net/review/3) | 지문·해설 확인 |
| 시험 결과 | [응시 3 결과](https://hocheon.tail4ddad6.ts.net/tests/attempts/3/result) | 문제별 해설 펼침 확인 |
| 일반 시험 응시 | [시험 현황](https://hocheon.tail4ddad6.ts.net/tests) | 진행 중 응시 없음. 응시 가능한 시험 시작 후 발급되는 `/tests/attempts/{attemptId}`에서 확인 |
| AI 재학습·오답 재풀이 | [저장된 AI 문제 목록](https://hocheon.tail4ddad6.ts.net/practice) | 기존 실행 기록이 없어 상세 직접 주소 미발급. `다시 학습`으로 `/practice/ai/runs/{runId}` 생성 |
| AI 개인 시험 | [AI 연습 시험 목록](https://hocheon.tail4ddad6.ts.net/tests) | 기존 실행 기록이 없어 상세 직접 주소 미발급. `연습 시험 시작`으로 `/tests/ai/{runId}` 생성 |

로그인: 기존 허용 Google 계정. 계정 허용 설정은 추적 제외 로컬 `.env`에서 관리한다. 다른 계정은 위 사용자 소유 문제·오답·응시에 접근할 수 없다. 신규 생성 형식은 학습 화면에서 AI 문제를 새로 만들 때 적용된다.

재시작 명령: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public`. Docker Desktop, 로컬 `.env`, 기존 Tailscale HTTPS proxy가 필요하다. 로컬 HTTP는 secure-cookie 공개 모드이므로 로그인 화면의 대체 경로로 사용하지 않는다.

임시 3101 fixture 서버는 검증 후 중지했고 임시 소스 디렉터리는 제거했다. 코드·예시의 정적 검증 결과는 `output/question-readability-preview.html`을 브라우저로 열어 확인할 수 있다. 본 서비스는 계속 실행 중이다.

## 후속 수정: 드래그 선택 영역 대비

대표 확인: [AI 문제 13](https://hocheon.tail4ddad6.ts.net/practice/ai/13)

2026-09-26 19:59 KST 확인. 공통 `::selection`에 배경만 `#dbeafe`로 지정되어 다크 모드의 밝은 본문색 `#eef3ff`와 겹쳤다. `globals.css`의 테마별 선택 색상을 라이트 `#17233c` / `#dbeafe`, 다크 `#ffffff` / `#1d4ed8`로 지정하고 선택된 텍스트의 그림자를 제거했다. 색상 대비는 각각 12.82:1, 6.70:1이다.

- 실서비스에서 본문을 마우스로 드래그하여 다크 모드 선택 글자가 읽히는 것을 확인했다. 라이트 모드에서도 실제 계산 색상을 확인하고 원래 다크 모드로 복원했다.
- 390×844 화면에서 document scrollWidth 375px, 가로 넘침 및 브라우저 오류 없음. 검증 후 화면 크기 재정의를 해제했다.
- 프론트엔드 TypeScript·ESLint·41개 파일/166개 테스트·production build·runtime smoke 통과. 로그: `output/text-selection-build.log`.
- 프론트엔드 재빌드·재시작 및 Nginx reload 완료. 서비스 실행 중이며 접속 포트·Google 로그인·시작 명령·선행 조건은 위 접속 정보와 같다. 기존 페이지에서는 새로고침 후 확인한다.
