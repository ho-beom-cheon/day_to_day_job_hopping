"""Build additive, digest-bound assessment evidence; never rewrite published lessons.

V27/V28 already contain reviewed authored lessons. Register their exact bodies.
For the six historical fixture lessons, retain their original scope and combine
only material already supplied to the learner: body, goal, question, explanation.
This does not publish the fixture as a full course or claim human review.
"""
import hashlib
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[2]


def records():
    rows = []
    for release in ('extra-learning-v1', 'extra-learning-v2'):
        for lesson in runpy.run_path(str(ROOT / 'content' / release / 'build.py'))['LESSONS']:
            digest = hashlib.sha256(lesson['body'].encode()).hexdigest()
            rows.append(dict(content_digest=digest, body_sha256=digest, assessment_body=None,
                             review_origin='AI_AUTHORED_AND_CHECKED',
                             review_note=f"{release}/{lesson['slug']}: 기존 발행 원고와 동일한 SHA-256. 사람 검수 아님."))
    seed = (ROOT / 'backend/src/main/resources/db/dev-seed/V8_1__approved_backend_learning_catalog.sql').read_text(encoding='utf-8')

    def array(name):
        values = re.search(r'\b' + name + r' text\[\] := ARRAY\[(.*?)\];', seed, re.S)[1]
        return [v.replace("''", "'") for v in re.findall(r"'((?:[^']|'')*)'", values)]

    for title, goal, body, question, explanation in zip(*(array(n) for n in ('titles', 'goals', 'bodies', 'prompts', 'explanations'))):
        evidence = f"{body.replace(chr(92) + 'n', chr(10))}\n\n학습 목표: {goal}\n\n학습 때 제공된 확인 문제: {question}\n\n제공된 정답 해설: {explanation}"
        assert len(evidence) >= 200, title
        rows.append(dict(content_digest=hashlib.md5(body.encode()).hexdigest() + hashlib.md5(('daily-career:' + body).encode()).hexdigest(),
                         body_sha256=hashlib.sha256(body.encode()).hexdigest(), assessment_body=evidence,
                         review_origin='AI_REVIEWED_EXISTING_MATERIAL',
                         review_note=f"{title}: 기존 원문·학습 목표·확인 문제·정답 해설만 결합. 새 학습 목표/오답 선택지/사용자 답안 제외. 사람 검수 아님."))
    return rows


def main():
    payload = json.dumps(records(), ensure_ascii=False, indent=2)
    (Path(__file__).parent / 'evidence.json').write_text(payload + '\n', encoding='utf-8')
    template = (Path(__file__).parent / 'migration.sql.template').read_text(encoding='utf-8')
    target = ROOT / 'backend/src/main/resources/db/migration/V33__assessment_source_readiness.sql'
    target.write_text(template.replace('{{RECORDS}}', payload.replace("'", "''")), encoding='utf-8')
    print('Built V33: 15 published manuscript reviews + 6 historical evidence bundles')


if __name__ == '__main__':
    main()
