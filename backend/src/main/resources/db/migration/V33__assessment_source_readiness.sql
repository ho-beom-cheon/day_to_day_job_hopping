-- Additive evidence only. Original sources, assignments, attempts and goals stay immutable.
CREATE TABLE daily_career.study_source_review (
    content_digest varchar(64) PRIMARY KEY CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    body_sha256 varchar(64) NOT NULL CHECK (body_sha256 ~ '^[0-9a-f]{64}$'),
    assessment_body text CHECK (assessment_body IS NULL OR length(assessment_body)>=200),
    review_origin varchar(60) NOT NULL,
    review_note text NOT NULL,
    reviewed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE daily_career.study_source_review IS '원본 digest와 실제 본문 SHA-256에 묶인 평가 근거. 기존 교재/진도 변경 또는 사람 검수 주장이 아님.';
INSERT INTO daily_career.study_source_review(content_digest,body_sha256,assessment_body,review_origin,review_note)
SELECT content_digest,body_sha256,assessment_body,review_origin,review_note
FROM jsonb_to_recordset('[
  {
    "content_digest": "c581a89877c6fe0071f90843e73a182d7fa1eb7e2198f5e33f9a5059f7419104",
    "body_sha256": "c581a89877c6fe0071f90843e73a182d7fa1eb7e2198f5e33f9a5059f7419104",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v1/spring-change-boundary: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "fbf0004252886f7d1cb1ae9c9e667fc85a8c1e098da6b575bc2389dfadb0f67f",
    "body_sha256": "fbf0004252886f7d1cb1ae9c9e667fc85a8c1e098da6b575bc2389dfadb0f67f",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v1/sql-join-report: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "2708707e1cd8f7213c3dfd26d24e7f0e194972268093acc7a747bcd49da017fe",
    "body_sha256": "2708707e1cd8f7213c3dfd26d24e7f0e194972268093acc7a747bcd49da017fe",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v1/algorithm-batch-dedup: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "8899a716eba85e765849c4129e37eb8199456302af1f61dc2170108c5295e0d8",
    "body_sha256": "8899a716eba85e765849c4129e37eb8199456302af1f61dc2170108c5295e0d8",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v1/finance-amount-retry: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "4eada59ed7bcc2208bf9de606809530698e61a260ee2121ad1b799623a2478b3",
    "body_sha256": "4eada59ed7bcc2208bf9de606809530698e61a260ee2121ad1b799623a2478b3",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v1/interview-change-story: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "895e0b84a46593f5a609803789d3d2f066c999780b0e678ad87c1d5b012b2988",
    "body_sha256": "895e0b84a46593f5a609803789d3d2f066c999780b0e678ad87c1d5b012b2988",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/spring-rollback-rules: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "91751803e4bb10c7bc22075b3e4028e5789cf0ade59cc9c9d0eea517f52e5b5e",
    "body_sha256": "91751803e4bb10c7bc22075b3e4028e5789cf0ade59cc9c9d0eea517f52e5b5e",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/spring-input-contract: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "b30f2fe64f6c5946b842ac22a8a0d6150477e5bc1b5743f5ca572d94a6b8cfca",
    "body_sha256": "b30f2fe64f6c5946b842ac22a8a0d6150477e5bc1b5743f5ca572d94a6b8cfca",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/sql-latest-row: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "654da90a7879d6b6614c4a9bd9fa34149174283e0a3c648566c230d9a0673466",
    "body_sha256": "654da90a7879d6b6614c4a9bd9fa34149174283e0a3c648566c230d9a0673466",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/sql-null-unique: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "f01e673aa0727b9b05d18067da842ebdc9f0266a19596b0130db12e2c5f8c222",
    "body_sha256": "f01e673aa0727b9b05d18067da842ebdc9f0266a19596b0130db12e2c5f8c222",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/algorithm-bfs-workflow: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "9ef9b1c599f34a417b97b515bbf60a2082ea8f95628a169ae57eaedc6a822cef",
    "body_sha256": "9ef9b1c599f34a417b97b515bbf60a2082ea8f95628a169ae57eaedc6a822cef",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/algorithm-priority-deadline: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "327a53115c3bf1d4ddb1745c129ca1e4efa188d39e5b2ef1450fd2b3f2e44d66",
    "body_sha256": "327a53115c3bf1d4ddb1745c129ca1e4efa188d39e5b2ef1450fd2b3f2e44d66",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/finance-ledger-reconcile: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "9e3429ca3262d630cb07e8961993bb365ac709114c9472ca4f74b1a450605d48",
    "body_sha256": "9e3429ca3262d630cb07e8961993bb365ac709114c9472ca4f74b1a450605d48",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/finance-cutoff-retry: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "a8c5337a646034c2370b67aaa112a6f404cb25c0794d781313ea8a5d16b3fb61",
    "body_sha256": "a8c5337a646034c2370b67aaa112a6f404cb25c0794d781313ea8a5d16b3fb61",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/interview-incident-evidence: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "8da0c0e01242b4b757104c31a39ff2739964434e79151c0dcd69f78460972b53",
    "body_sha256": "8da0c0e01242b4b757104c31a39ff2739964434e79151c0dcd69f78460972b53",
    "assessment_body": null,
    "review_origin": "AI_AUTHORED_AND_CHECKED",
    "review_note": "extra-learning-v2/interview-test-boundaries: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."
  },
  {
    "content_digest": "4e3072eeb4608f04911b4a7951a814c75a9dae6b2dadf6e9d6d81feb08569e06",
    "body_sha256": "41e4b8a00f983d1793e9aefc1d08df652d47f3354111a58fcb403e896484dad3",
    "assessment_body": "# Java 값 비교\n\n문자열과 값 객체는 참조가 아니라 의미 있는 값으로 비교합니다. `equals`와 `hashCode` 계약을 함께 지키고, 변경 가능한 필드를 키로 사용하지 않습니다.\n\n학습 목표: 값 비교와 불변 객체의 기본 원칙을 설명한다.\n\n학습 때 제공된 확인 문제: Java에서 두 문자열의 내용이 같은지 비교할 때 가장 적절한 방법은?\n\n제공된 정답 해설: `equals`는 문자열의 내용을 비교하고 `==`는 참조 동일성을 비교합니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "Java 핵심 다지기: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  },
  {
    "content_digest": "c6d526f661460665471216081ed9c71923b9b35caf3180a3057708b62bee93d1",
    "body_sha256": "56f5e2879846480dd7e5ca2f3380b507e420754cd82d90dfda92c12fc4dc5246",
    "assessment_body": "# Spring Boot 요청 흐름\n\nController는 HTTP 경계를, Service는 업무 규칙과 트랜잭션을, Repository는 저장소 접근을 담당합니다. 입력 DTO와 응답 DTO를 분리합니다.\n\n학습 목표: HTTP 요청의 Controller와 Service 처리 흐름을 설명한다.\n\n학습 때 제공된 확인 문제: Spring MVC에서 HTTP 요청을 받아 입력을 검증하고 응답 DTO를 반환하는 계층은?\n\n제공된 정답 해설: Controller는 HTTP 경계에서 요청을 받고 검증 결과와 응답 형식을 조정합니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "Spring Boot 웹 계층: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  },
  {
    "content_digest": "0b491897718a411fc8876988e72f51dd1dfed905b3ead4855ccb2bcc9f933f0d",
    "body_sha256": "614c0af8e15de8fab5da85a936e802240cfc771f15cd356878cdfffe68b22185",
    "assessment_body": "# JOIN과 데이터 모델\n\nJOIN 전에 기준 엔터티와 관계의 카디널리티를 확인합니다. 필요한 열만 선택하고 실행 계획으로 인덱스 사용과 예상 행 수를 점검합니다.\n\n학습 목표: JOIN의 기준 열과 결과 행 수의 관계를 설명한다.\n\n학습 때 제공된 확인 문제: INNER JOIN에 대한 설명으로 옳은 것은?\n\n제공된 정답 해설: INNER JOIN은 조인 조건을 만족하는 양쪽 행만 결과에 포함합니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "SQL과 데이터 모델링: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  },
  {
    "content_digest": "c6f1d2e22b735ebfbc576cfbe5ceeba7f74a3b32a54aabf0f9242fdef2ac1d51",
    "body_sha256": "2988507ac75634f27b24cc4dd4c0c75790ef0babb13e48477f2cc1c6e48baa06",
    "assessment_body": "# 문제 해결 절차\n\n입력 범위와 목표를 먼저 적고 상태와 전이를 정의합니다. 작은 예제로 불변식을 확인한 뒤 시간과 공간 복잡도를 계산합니다.\n\n학습 목표: 문제를 입력, 상태, 전이로 나누어 풀이한다.\n\n학습 때 제공된 확인 문제: 너비 우선 탐색에서 다음에 방문할 정점을 관리하는 대표 자료구조는?\n\n제공된 정답 해설: BFS는 먼저 발견한 정점을 먼저 꺼내기 위해 Queue를 사용합니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "알고리즘 문제 해결: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  },
  {
    "content_digest": "a105397d734323ede196f3752a43b48fe89225b6a9ec2b60c37a5d492f76dff7",
    "body_sha256": "c3c3bfc5823bf00a401197cee69afbe2ab1e1daa2e4301473a4b38f81a23d9de",
    "assessment_body": "# 안전한 금액 처리\n\n금액은 부동소수점 대신 명시적인 소수 정밀도를 사용합니다. 결제 명령은 멱등키, 원자적 저장, 감사 이력으로 중복과 부분 실패를 다룹니다.\n\n학습 목표: 금액 처리와 멱등성의 필요성을 설명한다.\n\n학습 때 제공된 확인 문제: 같은 결제 요청이 재전송되어도 한 번만 처리되게 하는 성질은?\n\n제공된 정답 해설: 멱등키와 처리 결과 보존으로 같은 논리 요청의 중복 반영을 막습니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "금융 서비스 기초: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  },
  {
    "content_digest": "c2d6dee8c54c3d74e1ad63a29c1fc574edf0828695bd332b0441b146ea84e206",
    "body_sha256": "cc82c755b1848779cd651c984b6f326fbc1aab30cd10f23db76f253352948b06",
    "assessment_body": "# 기술 면접 답변 구조\n\n상황과 제약, 선택한 방법, 대안, 검증 결과를 순서대로 설명합니다. 모르는 부분은 추측하지 않고 확인 방법과 위험을 구분합니다.\n\n학습 목표: 기술 선택의 근거와 트레이드오프를 구조적으로 설명한다.\n\n학습 때 제공된 확인 문제: 기술 면접에서 설계 선택을 설명하는 가장 좋은 방식은?\n\n제공된 정답 해설: 맥락과 대안, 선택 이유, 손해를 함께 말해야 판단 과정을 검증할 수 있습니다.",
    "review_origin": "AI_REVIEWED_EXISTING_MATERIAL",
    "review_note": "면접과 최종 점검: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."
  }
]'::jsonb)
AS r(content_digest text,body_sha256 text,assessment_body text,review_origin text,review_note text);

-- One policy for visible optional topics and completed-learning assessments.
CREATE VIEW daily_career.study_source_readiness AS
WITH evidence AS (
    SELECT v.id content_version_id,c.owner_user_id,
      coalesce(r.assessment_body,v.body_markdown) body_markdown,
      v.version_no::text || ':' || v.content_digest || ':' ||
        encode(sha256(convert_to(coalesce(r.assessment_body,v.body_markdown),'UTF8')),'hex') source_version,
      v.status IN ('PUBLISHED','RETIRED') published,
      (r.content_digest IS NOT NULL
        OR EXISTS(SELECT 1 FROM daily_career.study_topic_detail t WHERE t.content_version_id=v.id)
        OR EXISTS(SELECT 1 FROM daily_career.ai_learning_topic t WHERE t.content_version_id=v.id AND t.user_id=c.owner_user_id)
        OR EXISTS(SELECT 1 FROM daily_career.curriculum_item_content ci
          JOIN daily_career.curriculum_unit cu ON cu.id=ci.curriculum_unit_id
          JOIN daily_career.catalog_import imp ON imp.curriculum_version_id=cu.curriculum_version_id
          WHERE ci.content_version_id=v.id AND imp.reviewed_at IS NOT NULL)) reviewed
    FROM daily_career.learning_content_version v
    JOIN daily_career.learning_content c ON c.id=v.learning_content_id
    LEFT JOIN daily_career.study_source_review r ON r.content_digest=v.content_digest
      AND r.body_sha256=encode(sha256(convert_to(v.body_markdown,'UTF8')),'hex')
)
SELECT *,reviewed AND published AND length(body_markdown)>=200 ready FROM evidence;
