# 금융권 백엔드 26주 과정

상태: 7개 분야 98개 신규 원문과 98개 핵심 확인 문제를 추가했다. 기존 35개 자료와 합쳐 선택 학습 카탈로그는 133개다. 정규 130일 자동 배정/서술형 통합 평가 완료와는 구분한다.

- 실제 원고: `catalog/{java,spring,sql,algorithm,finance,interview,ai}.md`
- 분야마다 14개. 기초·실무·심화, 구체적인 상황, 판단 자료, 수정/영향, 실습 해설, 공식 참고 자료를 포함한다.
- `python content/financial-backend-v1/build_catalog.py --check`: 원고와 V29 마이그레이션 일치 검사.
- `python content/financial-backend-v1/verify_examples.py`: 금액·이자·상환·누적합·이진 탐색·DP·BFS 등 예시 검산 및 답안 정렬 검사.
- `catalog-manifest.json`: 안정적인 주제 식별자, 목표, 난이도, 권장 순서/주차, 출처, 본문 digest.
- `source-link-audit.json`: 공개 링크 확인 결과. Python에서 MIT 인증서 체인을 확인하지 못한 링크는 웹 도구로 원문을 열어 확인했다. 인증서 검증을 해제하지 않았다.
- 작성/검토 주체는 AI이며 형식·예시 자동 검증을 추가했다. 사람 검수 완료나 모든 실무 환경에서의 실행 검증을 주장하지 않는다.
- 기존 학습 자료/진도/답안은 유지한다. `V29`는 신규 자료만 추가하고 `V30`은 기존 15분 기록을 유지하며 90/120분 선택을 추가한다.

- [확정 설계](../../docs/financial-backend-program-decisions.md)
- [실행 프롬프트와 입력/출력 계약](../../backend/src/main/resources/prompts/financial-backend-v1/README.md)
- [26주/130일 목표 목차](../../backend/src/main/resources/prompts/financial-backend-v1/curriculum.json)
- [실제 원문·AI 생성 검증](../../docs/financial-topic-catalog-verification-20260927.md)
- [이전 설계 단계·미완료 요구 추적](../../docs/financial-backend-program-verification-20260927.md)

평가 목표 목차 원본은 backend의 classpath JSON 하나로 유지한다. 이 디렉터리를 기존 4주 `CatalogManuscript` 스키마로 import하지 않는다. 발행 후 수정은 기존 본문을 덮어쓰지 않고 새 버전으로 만든다. 권장 26주 경로는 선택 학습 순서이며 기존 사용자의 정규 일정을 재배정하지 않는다.
