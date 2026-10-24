# 선택형 추가 학습 검증

관련 이슈: [#10](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/10)

## 직접 확인할 화면

[대표: 실제 서비스 더 공부하기](https://hocheon.tail4ddad6.ts.net/learning/extra)

- [실제 서비스 학습 화면](https://hocheon.tail4ddad6.ts.net/learning)
- [실제 서비스 학습 통계](https://hocheon.tail4ddad6.ts.net/progress)
- [로그인 없는 QA 더 공부하기](http://localhost:18081/learning/extra)

- [기본 학습과 추가 학습 진입](http://localhost:18081/learning)
- [완료한 추가 학습 세션](http://localhost:18081/workspace/learning/47)
- [추가 학습 문제 풀이](http://localhost:18081/practice/3?contentId=21)
- [학습 통계](http://localhost:18081/progress)

2026-09-26 19:24~19:35 KST 실제 브라우저에서 확인했다. QA는 포트 18081에서 실행 중이며 `과정 검증 사용자`가 자동 적용된다. 별도 QA DB를 사용한다. 현재 공개 카탈로그는 Java 4주 과정이므로 분야 선택에는 Java만 표시된다.

실제 서비스는 19:34 KST 새 이미지로 재기동하고 19:37 KST 기존 로그인 세션으로 위 세 화면을 직접 확인했다. 오늘은 휴식일이지만 **더 공부하기**에 진입하며 알고리즘·SQL·금융·면접·Java·Spring의 공개 자료를 선택할 수 있었다. 실제 사용자 계정에서는 화면·선택 목록을 확인했고, 학습 기록을 만드는 수용 검증은 QA에서 수행했다. 기존에 허용된 Google 계정으로 로그인하면 된다. 계정 허용 및 인증 설정은 추적 제외 `.env`에서 관리한다.

주 서비스는 기존 HTTPS 주소(443) → 로컬 127.0.0.1:8080으로 연결되며 네 컨테이너 모두 healthy다. 런타임 smoke의 프런트엔드→백엔드·DB, Nginx→화면/API 검증을 통과했다. 로컬 새 API의 익명 요청은 401이다. PowerShell HTTP 클라이언트의 HTTPS 확인은 SSL 연결 오류였으나, 실제 브라우저에서는 인증서 경고 없이 기존 HTTPS 주소에 정상 접속했다. 최종 QA 백엔드도 19:36 KST 갱신했으며 기존 추가 기록을 보존했다.

## 자동 검증

- 프런트엔드 `npm.cmd run typecheck`, 변경 화면 대상 ESLint 통과.
- 프런트엔드 Docker runtime 이미지 빌드: 전체 41개 파일, 166개 테스트 통과, Next.js 빌드 성공.
- 백엔드 Docker build 단계: 단위 테스트 78개 통과.
- PostgreSQL `LearningOperationsIT`: 32개 테스트 통과, 실패·오류·건너뛰기 0개.
- 변경된 추적 소스의 `git diff --check` 통과.

통합 테스트는 독립 Testcontainers DB에서 다음을 검증했다.

1. 기본 학습 완료일에도 추가 활동 시작·중단·완료로 완료율이 변하지 않는다.
2. 서로 다른 요청 키의 동시 시작은 동일 진행 세션을 반환한다. 동일 키 재시도와 완료·시간 저장 재시도는 중복 계산하지 않는다.
3. 타인의 추가 세션·콘텐츠 문맥 접근을 거부한다. 과거 정규 답안으로 새 추가 활동을 자동 완료하지 않는다.
4. 11분 중간 저장 후 다음 서울 날짜에 7분 완료 시 날짜별 11/7, 합계 18분이다. 정규 30분과 합쳐 48분이다.
5. 미리 학습을 끝내도 원래 배정일을 유지하고 예정일에 다시 완료할 필요가 없다. 시간은 실제 활동일에만 귀속한다.
6. 휴식일과 배정 없는 날의 상태·필수 학습일 수를 보존한다. 시간을 모두 저장했으면 0분으로 마무리한다.
7. 휴식일의 추가 학습 기록이 일정 정책 갱신·휴식 취소 후에도 보존된다. 선택 학습을 시작했다는 이유만으로 정규 일정 변경을 거절하지 않는다.

실행 명령:

```powershell
docker build --target build -t daily-career-extra-learning-build ./backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}/content:/content:ro" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal daily-career-extra-learning-build sh mvnw -B -ntp -Ppostgres-it '-Dit.test=LearningOperationsIT' verify
docker build --target runtime -t daily-career-extra-learning-frontend ./frontend
```

초기 검증에서 API 인증 matcher 누락과 멱등 작업 코드 형식 오류를 수정했다. 일정 회귀 테스트에서는 현재 일정 revision을 사용하도록 fixture를 수정했다. 위 결과는 수정 후 최종 실행이다.

## 실제 브라우저 검증

- 네 방식 모두 주제를 선택하고 실제 세션에 진입했다.
- DEEPEN 15분 세션 47: 5분 저장 → 목록 이동 → 재접속·새로고침 → 3문제 연속 제출 → 콘텐츠 완료 → 추가 10분 저장·세션 완료. 기록은 15분이다.
- 기본 학습은 계속 1/1·100%·90분이다. 통계 누적은 1시간 45분으로 추가 15분만 증가했다.
- AHEAD 세션 3: 다음 날(9월 27일) 2일차를 9월 26일에 시작하고 3분 저장했다. 다음 진도 이어하기 목록에서 복원했다.
- REVIEW 세션 48: 이전 주제와 오답 우선 표시 후 새 풀이 문맥으로 시작했다.
- EXPLORE 세션 49: 공개된 10일차 주제를 60분 목표·기존 확인 문제 10개로 시작했다.
- 데스크톱과 390×844 모바일 뷰포트에서 화면을 확인했다. 모바일 문서 폭은 375px(스크롤바 제외)이며 scrollWidth=clientWidth였다. 선택 화면과 세션 화면의 가로 넘침이 없고 콘솔 오류·경고도 없었다. 임시 뷰포트 설정은 해제했다.

이 브라우저 검증에서는 추가 세션의 외부 AI 생성은 요청하지 않았다. 기존 자료를 이용한 흐름과 기존 AI 버튼 연결을 확인했으며 새 AI 응답의 품질·과금은 검증 범위에 포함하지 않는다.

## 실행과 재시작

QA는 Docker Desktop(Linux containers), 위 이미지, 기존 QA DB, 추적 제외 `.env.course-qa.local`의 설정이 필요하다. 비밀번호는 해당 로컬 파일에서 관리한다.

```powershell
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml -f compose.extra-learning-qa.yaml up -d --no-build --wait
docker compose --env-file .env.course-qa.local -f compose.course-qa.yaml -f compose.extra-learning-qa.yaml exec -T nginx nginx -s reload
```

기존 주 서비스는 추적 제외 `.env`의 공개 모드 설정과 기존 HTTPS 프록시를 사용한다. 재시작 명령은 아래와 같다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ide.ps1 -Action Up -Mode Public
```
