# OpenAI API 연결

앱의 일간 학습 피드백은 OpenAI Responses API를 직접 호출할 수 있다. 현재 범위는 `/assistant`의 **오늘 피드백 만들기**이며, 학습 제목·진행 상태를 입력으로 보내고 한국어 요약을 저장한다.

프로젝트 루트의 Git 무시 대상 `.env`에 다음 값을 저장한다. 키를 채팅, 이슈, 로그 또는 커밋에 적지 않는다.

```dotenv
AI_PROVIDER_TYPE=openai
OPENAI_API_KEY=<OpenAI Platform 프로젝트 API 키>
OPENAI_MODEL=gpt-6-luna
```

`docker compose up -d --build --wait backend` 후 필요하면 `docker compose restart nginx`를 실행한다. [외부 연동 설정](http://localhost:8080/settings/integrations)에서 **AI · OpenAI / API 키 설정됨**을 확인하고 [AI 도우미](http://localhost:8080/assistant)에서 피드백을 만든다. `API 키 설정됨`은 키의 존재만 뜻한다. 작업이 `SUCCESS`이고 한국어 결과와 사용 토큰이 보일 때 실제 호출 성공이다.

OpenAI Platform의 [API 키](https://platform.openai.com/api-keys)와 [결제 설정](https://platform.openai.com/settings/organization/billing/overview)은 ChatGPT 구독과 별도로 관리한다. 잔액 소진 시 앱의 작업은 실패하며 충전 또는 사용 한도 조정 후 새 요청으로 다시 시도한다. `OPENAI_API_KEY`가 비어 있으면 앱은 피드백 요청을 막는다. `OPENAI_MODEL`은 OpenAI Platform에서 사용 가능한 구조화 출력 지원 모델로 변경할 수 있다.

2026-09-24 09:45 KST 로컬 검증: 잔액 준비 후 `gpt-6-luna` 일간 피드백 작업이 `SUCCESS`로 완료되었고 한국어 결과와 입력 183·출력 147토큰이 기록되었다. 앞선 잔액 부족 실패 작업 4건은 일일 요청 횟수에서 제외되었고, 성공한 작업 1건만 차감되어 19/20으로 표시된다.
