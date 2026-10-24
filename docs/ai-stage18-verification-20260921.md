# 작업 18 AI 기능·피드백 UI 검증

- AI-001~008 경로, 논리 작업 생성·조회, 사용량 조회, HTTP provider adapter와 worker를 연결했다.
- 본인 콘텐츠·과정·오답·평가만 입력으로 허용하고 동시 작업 2개와 일일 접수 20개를 제한했다.
- `/assistant`에서 공급자 준비 상태, 사용량, 작업 처리/실패/완료와 일간 피드백을 구분한다.
- 공급자 설정이 빈 현재 환경은 `enabled=false`로 표시하며 가짜 AI 결과를 만들지 않는다.

| 검증 | 결과 |
|---|---|
| Backend Docker build / Maven verify | PASS, 40개 |
| Frontend 단위 테스트 | PASS, 21파일 76개 |
| TypeScript / ESLint / Next build | PASS |
| Compose config / 4서비스 | PASS / healthy |
| Browser desktop 2560px | PASS, 가로 넘침 없음, console error/warn 0 |
| Browser narrow 592px | PASS, 가로 넘침 없음, console error/warn 0 |
| 실제 공급자 호출 | 미검증. 로컬 provider 설정 없음 |
| 성공 피드백 실데이터 | 미검증. 승인된 과정 템플릿 없음 |

기존 smoke script는 현재 개발 인증 우회가 미인증 API를 401 대신 200으로 응답하게 하므로 보안 경계 assertion에서 실패했다. 인증 우회를 끈 환경에서 재실행해야 한다.
