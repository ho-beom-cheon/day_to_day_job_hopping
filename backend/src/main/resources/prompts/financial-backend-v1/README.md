# 금융권 백엔드 학습 프롬프트 v1

실행 원본은 이 디렉터리다. `StudyPrompts`가 공통 system + 역할 파일을 합치고 `OpenAiCompletion`의 Responses API structured outputs에 스키마를 전달한다. 역할별 파일을 다른 문서에 복사해 따로 유지하지 않는다.

| 역할 | 입력 | 출력 |
| --- | --- | --- |
| learning | 선택 목표, 검수·발행 원문, 시간 | learning.schema.json |
| daily / weekly / monthly / final | 서버 확정 plan, 목표, 검수·발행 원문 | assessment.schema.json |
| extra | 문제만 또는 혼합 평가 plan | assessment.schema.json; 설명/실습만은 learning 사용 |
| retake | 원래 전체 범위/난이도의 새 plan, 새 variationSeed | assessment.schema.json |
| grading | 현재 제출 답안, 고정 문항/루브릭/근거 | grading.schema.json |
| validation / gap_audit | 검수 후보 또는 목표와 원문 현황 | audit.schema.json |
| source_authoring | 목표, 조사한 공식 자료·버전·출처 | learning.schema.json; 결과는 발행 전 초안 |

## 실행 경계

1. DB 통계로 다음 목표와 난이도를 서버에서 결정한다. 생성에 raw history를 전달하지 않는다.
2. `StudyPolicy.scope`로 정규+추가 목표를 합치고 `plan`으로 시간을 계산한다. 학습 전에는 deferredObjectives를 다음 세션으로 이월할 수 있다. 이미 학습한 전체 범위의 평가는 `requireComplete`를 통과해야 한다.
3. `StudyRequest.assessment`는 typed 객체만 받으며 미검수/미발행/목표 근거 누락을 유료 호출 전에 거절한다. Source.reviewed/published 값은 클라이언트 JSON으로 받으면 안 된다. DB 검수/발행 기록을 확인한 어댑터가 구성해야 한다.
4. 역할에 따라 `STUDY_DAILY`, `STUDY_WEEKLY` 등의 purpose와 입력을 OpenAI 어댑터에 전달한다. 구조화 출력도 의미상 정답을 보장하지 않는다.
5. `StudyAssessment.validate`는 슬롯·유형·근거 버전·정확 발췌·커버리지·루브릭·중복을 검사한다. 이 검사는 출제 정답의 의미 검수를 대체하지 않는다.
6. `publicQuestions`만 풀이 화면에 보낸다. 원본에는 정답이 있으므로 일반 ai_job GET 응답으로 보내면 안 된다.
7. 답안을 불변 응시 스냅샷으로 먼저 저장한다. MCQ는 서버 채점. 서술형은 `StudyRequest.grading`으로 현재 답안만 전달한 후 `StudyAssessment.grade`로 기준별 점수·HOLD를 검증한다. 한 문항이라도 HOLD면 종합 점수는 null이다.
8. 재응시는 새 set/run/answer 행으로 보존하고 이전 질문 본문은 서버 중복 검사에만 사용한다. 생성/채점 실패가 제출이나 첫 응시를 지우면 안 된다.

## 현재 연결 상태

- 기존 PRACTICE_PROBLEMS 경로: 공통 경력/사실/근거 가드레일과 개인정보 허용 목록이 실제 worker/OpenAI 경로에 연결되었다. 기존 객관식 저장/풀이 계약은 유지한다.
- 새로운 역할/스키마/정책/검증기: 빌드 가능한 코드와 테스트가 있다. 새 STUDY_* 역할은 일반 AiController에서 요청할 수 없으며 신규 평가 큐·응시 저장·화면 어댑터가 연결되기 전에는 사용자 평가에 자동 적용하지 않는다.
- curriculum.json: 26주/130일 목표 목차다. 학습 본문 130개가 아니며 기존 4주 CatalogManuscript 스키마에 가져올 수 없다.
- `/learning/program`: 목차와 시간 정책의 미리 보기다. 실제 완료 목표가 아닌 목차 기준 예상이라는 안내를 유지한다.

## 공식 API 근거

- [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs): Responses `text.format` strict schema, 불완전 응답/거절 별도 처리.
- [Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices): 명확한 루브릭, 사람이 확인한 기준 답안과 채점 일치도 검증. 실제 학습자 답변 채점의 품질 검증은 별도로 필요하다.

설계 결정과 남은 통합 사항은 `docs/financial-backend-program-decisions.md` 및 검증 보고서에서 추적한다.
