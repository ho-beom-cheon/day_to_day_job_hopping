-- Approved deterministic development catalog. Never load this location in production.
-- Original project content for the complete local curriculum/problem/exam flow.
DO $$
DECLARE
    codes text[] := ARRAY['JAVA','SPRING','SQL','ALGORITHM','FINANCE','INTERVIEW'];
    names text[] := ARRAY['Java','Spring Boot','SQL','알고리즘','금융 도메인','기술 면접'];
    titles text[] := ARRAY['Java 핵심 다지기','Spring Boot 웹 계층','SQL과 데이터 모델링','알고리즘 문제 해결','금융 서비스 기초','면접과 최종 점검'];
    goals text[] := ARRAY['값 비교와 불변 객체의 기본 원칙을 설명한다.','HTTP 요청의 Controller와 Service 처리 흐름을 설명한다.','JOIN의 기준 열과 결과 행 수의 관계를 설명한다.','문제를 입력, 상태, 전이로 나누어 풀이한다.','금액 처리와 멱등성의 필요성을 설명한다.','기술 선택의 근거와 트레이드오프를 구조적으로 설명한다.'];
    prompts text[] := ARRAY['Java에서 두 문자열의 내용이 같은지 비교할 때 가장 적절한 방법은?','Spring MVC에서 HTTP 요청을 받아 입력을 검증하고 응답 DTO를 반환하는 계층은?','INNER JOIN에 대한 설명으로 옳은 것은?','너비 우선 탐색에서 다음에 방문할 정점을 관리하는 대표 자료구조는?','같은 결제 요청이 재전송되어도 한 번만 처리되게 하는 성질은?','기술 면접에서 설계 선택을 설명하는 가장 좋은 방식은?'];
    choices jsonb[] := ARRAY[
      '[{"id":"A","text":"== 연산자로 참조를 비교한다"},{"id":"B","text":"equals 메서드로 내용을 비교한다"},{"id":"C","text":"hashCode 값만 비교한다"}]'::jsonb,
      '[{"id":"A","text":"Controller"},{"id":"B","text":"Repository"},{"id":"C","text":"Migration"}]'::jsonb,
      '[{"id":"A","text":"양쪽에서 조건이 일치하는 행만 반환한다"},{"id":"B","text":"왼쪽의 모든 행을 항상 반환한다"},{"id":"C","text":"두 테이블의 모든 조합을 반환한다"}]'::jsonb,
      '[{"id":"A","text":"Stack"},{"id":"B","text":"Queue"},{"id":"C","text":"Heap"}]'::jsonb,
      '[{"id":"A","text":"멱등성"},{"id":"B","text":"정규화"},{"id":"C","text":"다형성"}]'::jsonb,
      '[{"id":"A","text":"결론만 말한다"},{"id":"B","text":"문제, 선택, 대안, 트레이드오프를 설명한다"},{"id":"C","text":"라이브러리 이름만 나열한다"}]'::jsonb
    ];
    answers text[] := ARRAY['B','A','A','B','A','B'];
    explanations text[] := ARRAY['`equals`는 문자열의 내용을 비교하고 `==`는 참조 동일성을 비교합니다.','Controller는 HTTP 경계에서 요청을 받고 검증 결과와 응답 형식을 조정합니다.','INNER JOIN은 조인 조건을 만족하는 양쪽 행만 결과에 포함합니다.','BFS는 먼저 발견한 정점을 먼저 꺼내기 위해 Queue를 사용합니다.','멱등키와 처리 결과 보존으로 같은 논리 요청의 중복 반영을 막습니다.','맥락과 대안, 선택 이유, 손해를 함께 말해야 판단 과정을 검증할 수 있습니다.'];
    bodies text[] := ARRAY[
      '# Java 값 비교\n\n문자열과 값 객체는 참조가 아니라 의미 있는 값으로 비교합니다. `equals`와 `hashCode` 계약을 함께 지키고, 변경 가능한 필드를 키로 사용하지 않습니다.',
      '# Spring Boot 요청 흐름\n\nController는 HTTP 경계를, Service는 업무 규칙과 트랜잭션을, Repository는 저장소 접근을 담당합니다. 입력 DTO와 응답 DTO를 분리합니다.',
      '# JOIN과 데이터 모델\n\nJOIN 전에 기준 엔터티와 관계의 카디널리티를 확인합니다. 필요한 열만 선택하고 실행 계획으로 인덱스 사용과 예상 행 수를 점검합니다.',
      '# 문제 해결 절차\n\n입력 범위와 목표를 먼저 적고 상태와 전이를 정의합니다. 작은 예제로 불변식을 확인한 뒤 시간과 공간 복잡도를 계산합니다.',
      '# 안전한 금액 처리\n\n금액은 부동소수점 대신 명시적인 소수 정밀도를 사용합니다. 결제 명령은 멱등키, 원자적 저장, 감사 이력으로 중복과 부분 실패를 다룹니다.',
      '# 기술 면접 답변 구조\n\n상황과 제약, 선택한 방법, 대안, 검증 결과를 순서대로 설명합니다. 모르는 부분은 추측하지 않고 확인 방법과 위험을 구분합니다.'
    ];
    curriculum_id bigint; version_id bigint; month_id bigint; week_id bigint; day_id bigint; item_id bigint;
    current_subject_id bigint; content_id bigint; content_version_id bigint; problem_id bigint;
    problem_version_id bigint; problem_version_ids bigint[] := ARRAY[]::bigint[];
    test_id bigint; test_version_id bigint;
    test_types text[] := ARRAY['DAILY','WEEKLY','MONTHLY','FINAL'];
    test_titles text[] := ARRAY['1일차 확인 시험','기초 주간 시험','중간 월간 시험','6개월 최종 시험'];
    test_days integer[] := ARRAY[1,2,3,6]; test_counts integer[] := ARRAY[1,2,3,6];
    i integer; j integer;
BEGIN
    INSERT INTO daily_career.curriculum(code,title) VALUES ('BACKEND-FOUNDATIONS-6M','백엔드 개발자 6개월 기초 과정') RETURNING id INTO curriculum_id;
    INSERT INTO daily_career.curriculum_version(curriculum_id,version_no,duration_months,status,description)
      VALUES (curriculum_id,1,6,'DRAFT','Java, Spring Boot, SQL, 알고리즘, 금융 도메인, 기술 면접을 한 흐름으로 연습하는 개발용 승인 과정.') RETURNING id INTO version_id;
    INSERT INTO daily_career.curriculum_release VALUES (version_id,'2026.09-dev.1','백엔드 개발자 6개월 기초 과정');

    FOR i IN 1..6 LOOP
      INSERT INTO daily_career.subject(code,name) VALUES (codes[i],names[i]) RETURNING id INTO current_subject_id;
      INSERT INTO daily_career.problem(subject_id,title) VALUES (current_subject_id,titles[i] || ' 확인 문제') RETURNING id INTO problem_id;
      INSERT INTO daily_career.problem_version(problem_id,version_no,status,question_type,difficulty,prompt_markdown,choices,answer_key,rubric,explanation_markdown,grading_rule_version,published_at)
        VALUES (problem_id,1,'PUBLISHED','SINGLE_CHOICE',CASE WHEN i<3 THEN 1 WHEN i<5 THEN 2 ELSE 3 END,prompts[i],choices[i],jsonb_build_object('correct',answers[i]),'{}',explanations[i],'rule-choice-v1',clock_timestamp()) RETURNING id INTO problem_version_id;
      problem_version_ids := array_append(problem_version_ids,problem_version_id);
      INSERT INTO daily_career.learning_content(subject_id,title) VALUES (current_subject_id,titles[i]) RETURNING id INTO content_id;
      INSERT INTO daily_career.learning_content_version(learning_content_id,version_no,status,body_markdown,source_references,content_digest)
        VALUES (content_id,1,'DRAFT',bodies[i],'[{"title":"데일리 이직 승인 개발 콘텐츠","url":"https://localhost/approved-dev-content"}]',md5(bodies[i])||md5('daily-career:'||bodies[i])) RETURNING id INTO content_version_id;
      INSERT INTO daily_career.learning_content_detail VALUES (content_version_id,titles[i],goals[i],jsonb_build_array(goals[i]),jsonb_build_array('선택 근거와 트레이드오프를 설명한다.'));
      INSERT INTO daily_career.learning_content_problem VALUES (content_version_id,problem_version_id,1);
      UPDATE daily_career.learning_content_version
         SET status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
       WHERE id=content_version_id;

      INSERT INTO daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title) VALUES (version_id,NULL,'MONTH',i,titles[i]) RETURNING id INTO month_id;
      INSERT INTO daily_career.curriculum_unit_detail(curriculum_unit_id,description) VALUES (month_id,titles[i]||' 월차');
      INSERT INTO daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title) VALUES (version_id,month_id,'WEEK',i,i||'주차 · '||titles[i]) RETURNING id INTO week_id;
      INSERT INTO daily_career.curriculum_unit_detail(curriculum_unit_id,description,learning_goals) VALUES (week_id,goals[i],jsonb_build_array(goals[i]));
      INSERT INTO daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title) VALUES (version_id,week_id,'DAY',1,i||'일차 · '||titles[i]) RETURNING id INTO day_id;
      INSERT INTO daily_career.curriculum_unit_detail(curriculum_unit_id,description,month_no,week_no,day_no) VALUES (day_id,goals[i],i,i,i);
      INSERT INTO daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title,subject_id,activity_type,estimated_minutes,completion_weight,content_version_id)
        VALUES (version_id,day_id,'ITEM',1,titles[i]||' 학습',current_subject_id,'LEARN',45,1,content_version_id) RETURNING id INTO item_id;
      INSERT INTO daily_career.curriculum_unit_detail(curriculum_unit_id,description) VALUES (item_id,goals[i]);
      INSERT INTO daily_career.curriculum_item_content VALUES (item_id,1,content_version_id,true,'PRACTICE','ALL_PROBLEMS_ATTEMPTED');
    END LOOP;

    FOR i IN 1..4 LOOP
      INSERT INTO daily_career.test(title,test_type) VALUES (test_titles[i],test_types[i]) RETURNING id INTO test_id;
      INSERT INTO daily_career.test_version(test_id,version_no,status,time_limit_seconds,attempt_limit,pass_percent,rule_version,published_at)
        VALUES (test_id,1,'PUBLISHED',600+i*300,3,60,'rule-choice-v1',clock_timestamp()) RETURNING id INTO test_version_id;
      FOR j IN 1..test_counts[i] LOOP
        INSERT INTO daily_career.test_item(test_version_id,problem_id,problem_version_id,item_no,max_score)
          SELECT test_version_id,pv.problem_id,pv.id,j,10 FROM daily_career.problem_version pv WHERE pv.id=problem_version_ids[j];
      END LOOP;
      SELECT u.id INTO day_id FROM daily_career.curriculum_unit u JOIN daily_career.curriculum_unit_detail d ON d.curriculum_unit_id=u.id
        WHERE u.curriculum_version_id=version_id AND u.unit_type='DAY' AND d.day_no=test_days[i];
      SELECT p.subject_id INTO current_subject_id FROM daily_career.problem p JOIN daily_career.problem_version pv ON pv.problem_id=p.id WHERE pv.id=problem_version_ids[test_days[i]];
      INSERT INTO daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title,subject_id,activity_type,estimated_minutes,completion_weight,test_version_id)
        VALUES (version_id,day_id,'ITEM',2,test_titles[i],current_subject_id,'TEST',20+i*10,1,test_version_id) RETURNING id INTO item_id;
      INSERT INTO daily_career.curriculum_unit_detail(curriculum_unit_id,description) VALUES (item_id,test_titles[i]||' 응시');
    END LOOP;
    UPDATE daily_career.curriculum_version
       SET status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
     WHERE id=version_id;
END $$;
