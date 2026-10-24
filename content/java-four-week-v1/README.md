# Java 핵심 역량 4주

[이슈 #9](https://github.com/ho-beom-cheon/day_to_day_job_hopping/issues/9)의 별도 1개월 과정이다. 기존 6개월 개발 자료나 26주 편집 초안을 대체하지 않는다.

- JDK 21, 4주, 20학습일, 필수 학습 세션 20개.
- 하루 자체 본문·실행 예제·실습 과제·완료 기준·확인 문제 10개·공식 출처.
- 확인 문제 200개와 별도 평가 은행 40개. 시험 26개에서 평가 은행을 재사용한다.
- 이 원고는 Codex가 작성·기술 점검했다. 독립적인 사람의 교육 검수나 학습자 소요 시간 실측을 통과했다고 주장하지 않는다.
- 운영 발행 여부와 실행 검증 결과는 `review.md`에 기록한다. 저장소에 파일이 있다는 것만으로 발행된 상태는 아니다.

## 편집·검증

`authoring/week01.py`부터 `week04.py`까지가 편집 원본이다. 학습 순서별로 본문과 12개 고유 문항을 직접 정의한다. `build.py`는 보기를 순환 배치하고 원고 JSON·실행 예제를 생성한다. 원격 AI 호출이나 자동 문항 생성은 하지 않는다.

```powershell
python content/java-four-week-v1/authoring/build.py
```

`examples/Lesson01.java`부터 `Lesson20.java`는 각 본문의 코드 블록과 동일하다. JDK 21에서 `java content/java-four-week-v1/examples/Lesson01.java`로 실행할 수 있다. 모든 예제는 실패 시 AssertionError를 던진다. 단순 출력만으로 통과하지 않는다.

`course.json`은 검수 전용 로컬 파일이며 정답도 포함한다. 프런트엔드 public 디렉터리에 복사하지 않는다. 화면은 기존 서버 API의 답안 제출 후 공개 정책을 사용한다.

## 적재와 발행

공개 서버가 실행 중이고 추적 제외 `.env`가 준비되어 있어야 한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/catalog.ps1 -Operation Import
```

로그의 `templateId`(문자열)와 `digest`를 검토한다. 동일 코드·버전·원고는 재사용하고, 같은 버전의 변경 원고는 거부한다. 변경은 원고 버전을 올린 뒤 새 버전으로 적재한다.

```powershell
# 아래 값은 실제 import 결과로 대체한다.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/catalog.ps1 -Operation Validate -TemplateId <ID> -ExpectedDigest <SHA256>
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/catalog.ps1 -Operation Publish -TemplateId <ID> -ExpectedDigest <SHA256> -ReviewNote '<검증 명령, 결과, 검수 기록 경로>'
```

적재는 과정·콘텐츠·문제·시험을 모두 DRAFT로 만든다. 발행 시 원고 digest와 DB 스냅샷, 참조·문항·기간·분모 검증을 다시 수행한다. DB 내용이 바뀌었으면 발행을 거부한다. 검수 기록과 하위 버전·과정 발행은 한 트랜잭션이다. 기존 사용자에게 새 과정을 자동 배정하지 않는다.

신규 사용자는 [과정 선택](https://hocheon.tail4ddad6.ts.net/onboarding), 기존 사용자는 [로드맵](https://hocheon.tail4ddad6.ts.net/roadmap)에서 현재 배정을 확인한다. 허용 Google 계정으로 로그인해야 한다. 발행 확인 전에는 이 주소를 신규 과정 제공의 증거로 간주하지 않는다.
