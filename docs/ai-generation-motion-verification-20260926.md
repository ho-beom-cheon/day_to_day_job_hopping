# AI 문제 생성 대기 애니메이션

이슈 #10 추가 학습 화면의 사용자 후속 요청: 생성 중 상태에 움직임 추가.

[대표: 애니메이션 미리보기](http://localhost:5175/workspace/learning/47)

[실제 사용 중인 학습 세션](https://hocheon.tail4ddad6.ts.net/workspace/learning/11)

## 변경

- `frontend/src/features/ai/ai-practice-panel.tsx`: 생성 요청 직후 및 READY/PROCESSING에서 회전 아이콘, 순차 점 움직임, 흐르는 표시를 노출한다. 실제 진행률을 알 수 없으므로 퍼센트나 가상의 생성 단계는 표시하지 않는다.
- `frontend/src/features/ai/ai-practice-panel.module.css`: 기존 파란색·다크 모드 색상을 상속하는 국소 애니메이션이다. `prefers-reduced-motion`에서는 정적인 안내를 유지한다.
- 완료·실패 시 기존 문제 이동·재시도 UI를 표시한다. API, 생성 속도, 문제 수, 기존 데이터는 변경하지 않았다.

## 검증

- `npm.cmd run typecheck` 및 변경 TSX 대상 ESLint 통과.
- `docker compose -f compose.yaml -f compose.public.yaml build frontend`: 전체 프런트엔드 테스트 166개, 타입·린트 및 Next.js 빌드 통과.
- 실제 빌드한 앱에 로컬 QA 응답을 연결해 생성 중·완료·실패 상태를 재현했다. 외부 AI 생성 요청은 하지 않았다.
- 데스크톱과 390×844 모바일에서 확인. 모바일 문서 폭 375px(스크롤바 제외), scrollWidth=clientWidth, 줄바꿈과 아이콘 배치 정상.
- 브라우저 계산 스타일에서 회전·점·흐름 애니메이션 이름과 transform을 확인했다. 움직임 줄이기 에뮬레이션에서는 모두 animationName=none이었다. 에뮬레이션과 임시 뷰포트는 해제했다.
- SUCCESS/FAILED로 전환하면 status 영역이 사라지는 것을 확인했다. 최종 앱 검증 중 콘솔 오류는 없었다.
- 2026-09-26 주 서비스와 QA 프런트엔드 반영 완료. 실제 HTTPS 세션에서는 이미 생성된 3문제의 완료 UI를 확인했다.

## 접속과 실행

현재 주 서비스(HTTPS 443 → 로컬 8080), QA(18081), 미리보기(5175)가 실행 중이다. 실제 서비스는 기존에 허용된 Google 계정으로 로그인한다. 미리보기는 로컬 QA 자동 계정을 사용하며 상단에 실제 AI 요청이 없는 상태 재현임을 표시한다. 학습 본문의 **AI 확인 문제** 영역에서 움직임을 확인할 수 있다.

미리보기는 Docker QA 서비스가 18081에서 실행 중이고 Node.js가 준비된 환경에서 다음 명령으로 재실행한다.

```powershell
node output/ai-motion-proxy.mjs
```

주 서비스는 기존 `.env`·Docker Desktop·HTTPS 프록시가 준비된 상태에서 다음 명령으로 재시작한다. 키와 비밀번호는 추적 제외 `.env` 및 `.env.course-qa.local`에만 보관한다.

```powershell
.\scripts\ide.ps1 -Action Up -Mode Public
```
