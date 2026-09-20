# 교육 템플릿 운영

작업 6은 검토된 데이터의 발행·배정·학습 운영 기능을 제공한다. 사용자의 결정에 따라 운영 콘텐츠와 개발용 가짜 과정을 자동 적재하지 않는다. 최초에는 `GET /api/v1/curriculum-templates`가 빈 배열이다. 테스트 전용 6모듈 콘텐츠는 `backend/src/test/`에만 있다.

## 검토와 적재

운영자는 출처와 이용 권한을 확인한 콘텐츠를 검토된 SQL 또는 별도 적재 도구로 DRAFT에 넣는다. 아래 관계가 모두 준비되어야 한다. 공개 업로드/발행 API는 없다.

- `curriculum` → `curriculum_version` → `curriculum_release`: 6개월, 공개 제목·설명·버전명.
- `curriculum_unit`: MONTH → WEEK → DAY → ITEM. 월차 1..6, 주차 1..60은 연속 번호이며, `curriculum_unit_detail`의 DAY 일차는 1부터 연속한다. DAY의 month_no/week_no는 교육 모듈을 나타낸다. 주차는 월차 경계를 포함할 수 있다.
- `curriculum_unit_detail`: 모든 단위의 설명, WEEK의 1개 이상 학습 목표. 각 월차/주차/학습일에 필수 세션이 있어야 한다.
- `learning_content_version`과 `learning_content_detail`: 본문·출처·고정 제목·목표·핵심 정리. 검토 완료된 콘텐츠 버전만 PUBLISHED로 전환한다. raw HTML 및 위험한 URL scheme은 허용하지 않는다.
- `curriculum_item_content`: 세션별 순서·필수 여부·종류·완료 조건. 여러 콘텐츠를 편성할 수 있다. READ_ACK는 읽음 확인, ALL_PROBLEMS_ATTEMPTED는 `learning_content_problem`으로 연결한 문제에 사용자 본인의 제출 이력이 필요하다.
- 확인 문제와 시험의 불변 버전도 PUBLISHED여야 한다. 초기 공개 문제 요약은 내부 SINGLE_CHOICE를 MULTIPLE_CHOICE로 매핑한다. 다른 내부 문제 유형은 준비 완료로 간주하지 않는다. 시험은 버전과 문항 구성을 고정해 배정하며 응시 API는 작업 9 범위다.

템플릿 발행 후 구성/콘텐츠 수정·삭제는 DB에서 차단한다. 수정은 새 버전으로 발행한다. RETIRED는 새 배정을 막지만 이미 배정한 콘텐츠 이력은 남는다. 제목·설명·분모를 예시값이나 AI 응답으로 채우지 않는다.

## 검증·발행 명령

아래 `$templateVersionId`에는 검토한 DRAFT의 실제 ID를 넣는다. `.env`나 credential을 명령에 직접 쓰지 않는다. 기존 서비스와 별도로 포트를 공개하지 않는 일회성 컨테이너를 실행하며 완료 후 종료한다. `catalog-ops` 프로필을 상시 Backend의 APP_PROFILES에 넣지 않는다.

```powershell
$templateVersionId = '검토한 버전 ID'
docker compose run --rm --no-deps backend --spring.profiles.active=catalog-ops --server.port=0 --management.server.port=0 --catalog.operation=validate --catalog.template-id=$templateVersionId
docker compose run --rm --no-deps backend --spring.profiles.active=catalog-ops --server.port=0 --management.server.port=0 --catalog.operation=publish --catalog.template-id=$templateVersionId
```

`validate`는 전체 배정 자료의 형식·모듈·콘텐츠·문제·시험 참조를 검사한다. `publish`는 템플릿 버전을 잠근 상태에서 같은 검증 후 PUBLISHED/발행시각을 저장한다. 실패하면 발행을 취소한다. 목록 조회나 과정 배정 중에 콘텐츠를 새로 생성하지 않는다.

현재 운영 콘텐츠는 비어 있으므로 이 명령으로 발행한 운영 템플릿은 없다. 실제 학습 콘텐츠 검토·적재는 별도 작업이다.
