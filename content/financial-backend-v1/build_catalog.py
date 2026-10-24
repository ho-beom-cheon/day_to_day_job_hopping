"""Build the reviewed-in-code, additive catalog migration; no provider calls or DB writes.

Source files are original Korean teaching cases, not copied documentation.
The review origin is explicitly AI author/reviewer plus executable checks, not human review.
"""
from pathlib import Path
from collections import Counter
import hashlib
import json
import re
import sys

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[1]
SUBJECTS = {"java": ("JAVA", "Java"), "spring": ("SPRING", "Spring"),
            "sql": ("SQL", "SQL"), "algorithm": ("ALGORITHM", "알고리즘"),
            "finance": ("FINANCE", "금융"), "interview": ("INTERVIEW", "면접"), "ai": ("AI", "AI 개발 활용")}
LEVELS = {"기초": 3, "실무": 4, "심화": 5}


def lessons():
    sources = json.loads((BASE / "catalog/sources.json").read_text(encoding="utf-8"))
    groups = {}
    for filename, (subject, name) in SUBJECTS.items():
        group = []
        for block in (BASE / f"catalog/{filename}.md").read_text(encoding="utf-8").split("@@ ")[1:]:
            header, text = block.split("\n", 1)
            slug, title, level, source = header.split("|")
            body, question = text.split("\n?? ", 1)
            lines = question.strip().splitlines()
            prompt = lines[0]
            correct = [s[2:] for s in lines if s.startswith("+ ")]
            wrong = [s[2:] for s in lines if s.startswith("- ")]
            explanation = [s[3:] for s in lines if s.startswith("!! ")]
            assert len(correct) == 1 and len(wrong) == 3 and len(explanation) == 1, slug
            assert len(set(correct + wrong)) == 4, slug
            assert len(body) >= 490 and body.count("## ") >= 4 and "```" in body, (slug, len(body))
            assert body.count("```") % 2 == 0, slug
            assert source in sources and level in LEVELS, slug
            source_title, url = sources[source]
            objective = f"{title} 사례에서 원인, 대안의 영향과 검증 근거를 설명한다."
            body = f"# {title}\n\n학습 단계: {level}\n\n" + body.strip()
            body += f"\n\n## 설명 연습\n\n{prompt} 선택 이유를 본문의 자료로 설명하고, 조건이 달라지면 다시 확인할 경계를 하나 적어 보세요. 코드 작성 대신 표·실행 흐름·짧은 면접 답변으로 정리할 수 있습니다.\n\n## 참고 자료와 범위\n\n[{source_title}]({url})\n\n본문의 수치·거래·로그는 학습용으로 구성한 사례입니다. 참고 자료는 개념 확인용이며 사례가 실제 기관의 업무 규정이나 본인의 수행 이력이라는 뜻은 아닙니다. 제품별 동작은 명시된 버전과 실제 환경에서 확인하세요."
            key_points = [correct[0], explanation[0], objective]
            group.append(dict(slug="fb-"+slug, title=title, level=level, subject=subject, subjectName=name,
                              objective=objective, body=body, keyPoints=key_points,
                              sources=[dict(title=source_title, url=url)],
                              difficulty=LEVELS[level], questions=[dict(prompt=prompt, choices=correct+wrong,
                                                                      answer=1, explanation=explanation[0])]))
        assert len(group) == 14, (filename, len(group))
        # Foundation first within a subject, stable author order for the next steps.
        groups[filename] = sorted(group, key=lambda x: x["difficulty"])
    result = [groups[subject][index] for index in range(14) for subject in SUBJECTS]
    for index, lesson in enumerate(result):
        lesson["order"] = index + 1
        lesson["week"] = index * 26 // 98 + 1
        q = lesson["questions"][0]
        shift = index % 4
        q["choices"] = q["choices"][-shift:] + q["choices"][:-shift] if shift else q["choices"]
        q["answer"] = shift + 1
        lesson["digest"] = hashlib.sha256(lesson["body"].encode()).hexdigest()
    assert len({x["slug"] for x in result}) == 98
    assert len({x["title"] for x in result}) == 98
    assert len({x["digest"] for x in result}) == 98
    return result


def migration(data):
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("'", "''")
    return """-- Generated from content/financial-backend-v1/catalog/*.md.
-- AI-authored and AI-reviewed educational cases with executable checks; no human review claim.
-- Additive only: existing assignments, answers and published versions are not modified.
CREATE TABLE daily_career.study_topic_detail (
  content_version_id bigint PRIMARY KEY REFERENCES daily_career.learning_content_version(id) ON DELETE RESTRICT,
  stage varchar(8) NOT NULL CHECK (stage IN ('기초','실무','심화')),
  recommended_week smallint NOT NULL CHECK (recommended_week BETWEEN 1 AND 26),
  learning_order smallint NOT NULL UNIQUE CHECK (learning_order > 0),
  review_origin varchar(40) NOT NULL DEFAULT 'AI_AUTHORED_AND_CHECKED'
);
COMMENT ON TABLE daily_career.study_topic_detail IS '금융 백엔드 확장 원문 단계와 권장 순서. 사람 검수 또는 정규 과정 재배정을 뜻하지 않는다.';
DO $financial_catalog$
DECLARE
  lesson jsonb; question jsonb; subject_key bigint; content_key bigint;
  version_key bigint; problem_key bigint; problem_version_key bigint;
  next_order integer;
BEGIN
  SELECT coalesce(max(display_order),0) INTO next_order FROM daily_career.extra_learning_catalog;
  FOR lesson IN SELECT value FROM jsonb_array_elements('""" + payload + """'::jsonb) LOOP
    INSERT INTO daily_career.subject(code,name) VALUES(lesson->>'subject',lesson->>'subjectName') ON CONFLICT(code) DO NOTHING;
    SELECT id INTO STRICT subject_key FROM daily_career.subject WHERE code=lesson->>'subject';
    INSERT INTO daily_career.learning_content(subject_id,title) VALUES(subject_key,lesson->>'title') RETURNING id INTO content_key;
    INSERT INTO daily_career.learning_content_version(learning_content_id,version_no,status,body_markdown,source_references,content_digest)
      VALUES(content_key,1,'DRAFT',lesson->>'body',lesson->'sources',lesson->>'digest') RETURNING id INTO version_key;
    INSERT INTO daily_career.learning_content_detail(content_version_id,title,learning_objective,key_points,interview_points)
      VALUES(version_key,lesson->>'title',lesson->>'objective',lesson->'keyPoints',jsonb_build_array(lesson->>'objective'));
    question := lesson->'questions'->0;
    INSERT INTO daily_career.problem(subject_id,title) VALUES(subject_key,(lesson->>'title') || ' · 핵심 판단') RETURNING id INTO problem_key;
    INSERT INTO daily_career.problem_version(problem_id,version_no,status,question_type,difficulty,prompt_markdown,choices,answer_key,rubric,explanation_markdown,grading_rule_version,published_at)
      SELECT problem_key,1,'PUBLISHED','SINGLE_CHOICE',(lesson->>'difficulty')::smallint,
        (lesson->>'body') || E'\\n\\n---\\n\\n' || (question->>'prompt'),
        (SELECT jsonb_agg(jsonb_build_object('id',ordinality::text,'text',value) ORDER BY ordinality)
          FROM jsonb_array_elements_text(question->'choices') WITH ORDINALITY),
        jsonb_build_object('correct',question->>'answer'),'{}'::jsonb,question->>'explanation','rule-choice-v1',clock_timestamp()
      RETURNING id INTO problem_version_key;
    INSERT INTO daily_career.learning_content_problem(content_version_id,problem_version_id,sequence) VALUES(version_key,problem_version_key,1);
    UPDATE daily_career.learning_content_version SET status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 WHERE id=version_key;
    next_order := next_order + 1;
    INSERT INTO daily_career.extra_learning_catalog(slug,content_version_id,display_order) VALUES(lesson->>'slug',version_key,next_order);
    INSERT INTO daily_career.study_topic_detail(content_version_id,stage,recommended_week,learning_order)
      VALUES(version_key,lesson->>'level',(lesson->>'week')::smallint,(lesson->>'order')::smallint);
  END LOOP;
END
$financial_catalog$;
"""


def main():
    data = lessons()
    target = ROOT / "backend/src/main/resources/db/migration/V29__financial_backend_topic_catalog.sql"
    expected = migration(data)
    if "--check" in sys.argv:
        assert target.read_text(encoding="utf-8") == expected, "Regenerate catalog migration"
    else:
        target.write_text(expected, encoding="utf-8")
        (BASE / "catalog-manifest.json").write_text(json.dumps([
            {k: v for k, v in x.items() if k not in ("body", "questions", "keyPoints")}
            for x in data], ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(dict(lessons=len(data), subjects=dict(Counter(x["subject"] for x in data)),
                         minBodyLength=min(len(x["body"]) for x in data),
                         maxBodyLength=max(len(x["body"]) for x in data)), ensure_ascii=False))


if __name__ == "__main__":
    main()
