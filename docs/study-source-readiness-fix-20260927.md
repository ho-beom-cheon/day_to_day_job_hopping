# 완료 학습의 평가 차단 복구 — 2026-09-27

관련 범위: 이슈 #10 / #8 후속. 기존 발행 원문·진도·답안을 보존하며 실사용 일간 평가가 열리지 않는 원인을 수정했다.

## 원인과 변경

- 실사용 2026-09-27 일간 범위는 원문 5개, 목표 11개였다. SQL 94자/Spring 117자의 옛 원문 두 개와 기존 추가 학습의 검수 연결이 누락된 알고리즘 두 개가 전체 준비를 차단했다.
- V33은 `study_source_review`와 `study_source_readiness`를 추가한다. V27/V28의 기존 검토 원고 15개는 정확한 digest로 검수 경로를 연결한다. 옛 원문 6개는 그때 이미 제공된 본문·목표·확인 문제·정답 해설만 묶은 평가 근거를 사용한다. 새 학습 내용/오답 선택지/개인 답안은 넣지 않는다. 사람 검수라고 표시하지 않는다.
- 원본 digest뿐 아니라 실제 본문의 SHA-256도 일치해야 복구 근거가 연결된다. 원본 본문과 버전·학습 목표 ID는 바꾸지 않는다. 평가 snapshot의 근거 버전에는 결합 본문 SHA-256도 포함한다. 원고 생성 근거는 `content/study-source-repair-v1/`에 있다.
- 완료 원문을 평가할 때와 새 추가 학습 주제를 제공할 때 동일한 준비 뷰를 사용한다. 기간별 목표 수와 주간·월간 개방 시점은 그대로다. 검수되지 않은 자료를 일괄 통과시키거나 평가 범위를 줄이지 않는다.
- 예외가 남으면 해당 제목과 원인, 완료 기록 보존, 재학습 불필요를 표시한다. 막연히 자료 보강을 기다리라는 문구는 제거했다. 목록의 준비 상태는 실제 새 평가가 사용할 목표의 원문을 기준으로 계산한다.

## 검증

- 백엔드 `mvn verify`: 144개 통과.
- PostgreSQL `StudyEvaluationIT`, `LearningOperationsIT#extraLearning*`: 10개 통과. 실제 구성과 같은 5원문/11목표/3회차, 일간·주간·월간·최종 생성 준비, 원문/완료 기록 불변, digest 위조 시 차단, 미검수 자료의 목록 제외, 추가 학습 시간/진도 회귀를 확인했다.
- 프런트 타입 검사·린트·프로덕션 빌드 및 48파일/196개 테스트 통과. 제목별 차단 사유와 복구 후 재조회도 검증했다.
- QA `http://localhost:18081/tests`에서 2026-09-25 합성 범위 5원문/11목표/3회차의 준비 버튼을 직접 실행했다. 결과는 `http://localhost:18081/tests/study/3`이며 5/5/1문항 준비 화면, 새 콘솔 오류·경고 없음. QA 합성 기록은 실사용에 복사하지 않았다.
- 실사용 V33 성공, 5개 자료 모두 `ready=true`. SQL 평가 근거 227자, Spring 296자이며 원본 94/117자는 그대로다.
- 실사용 원문139/배정본문45/학습항목83/기본답안28/기존시험3/완료기록11/신규평가0의 전체 행 해시가 배포 전후 동일하다. 근거는 `output/study-source-preservation-before.txt`, `output/study-source-preservation-after.txt`다.
- 백업: `backups/daily-career-public-20260927T222749-6efed2a6.dump`, SHA-256 `1FE207BAA4245BCD216577081069F072549374EC07B90D51BDCF3646A43DD147`.

## 실행과 확인 범위

- 대표 화면: https://hocheon.tail4ddad6.ts.net/tests — 기존 Google 허용 계정으로 로그인 후 일간 평가 준비. 추가 학습 목록: https://hocheon.tail4ddad6.ts.net/learning/extra .
- 로컬: http://localhost:8080/tests . 2026-09-27 22:30 KST HTTP 200, backend/frontend/nginx/postgres healthy, 프런트 런타임→백엔드/DB 및 nginx smoke 통과.
- 재시작: Docker Desktop 실행 후 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ide.ps1 -Action Up -Mode Public`.
- 이번 새 유료 OpenAI 호출은 자동 승인 검토가 특정 payload/목적지 승인 부족으로 차단했다. 기존 연결 설정은 변경하지 않았고 QA 임시 키도 없다(`OpenAIKeyAbsent`). 새 외부 생성 성공은 검증 범위에 포함하지 않는다.
- 실사용 Google 계정 선택도 자동 승인 검토가 계정별 로그인 승인 부족으로 차단했다. 실사용은 로그인 화면/DB/런타임을, 인증된 평가 준비 화면은 QA를 직접 확인했다. 사용자가 다시 로그인하면 실사용 화면을 확인할 수 있다. 인증 우회나 계정 대행 생성은 하지 않았다.

![QA 평가 준비 버튼](../output/study-source-ready-qa.png)
