"""Extract the archived HTML dictionary without executing its scripts (Python stdlib).

Run from any directory. This refreshes the source fixture, never applied migrations.
Adoption differences belong in migrations and the DB alignment document.
"""
import hashlib
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
source = next((ROOT / 'design-package/supplemental-20260920').glob('*.html'))
raw = source.read_text(encoding='utf-8')


def text(value):
    return html.unescape(re.sub(r'<[^>]+>', '', value)).strip()


tables = []
for name, body in re.findall(r'<details class="entity" id="table-([^"]+)"[^>]*>(.*?)</details>', raw, re.S):
    entry = {'name': name, 'description': text(re.search(r'<div class="entity-body"><p>(.*?)</p>', body, re.S)[1]),
             'columns': [], 'foreignKeys': [], 'checks': [], 'indexes': []}
    for table in re.findall(r'<table>(.*?)</table>', body, re.S):
        headings = [text(v) for v in re.findall(r'<th>(.*?)</th>', table, re.S)]
        for row in re.findall(r'<tr>(.*?)</tr>', table, re.S):
            cells = re.findall(r'<td>(.*?)</td>', row, re.S)
            if not cells:
                continue
            values = [text(c) for c in cells]
            if headings == ['컬럼', '자료형', 'NULL', '기본값', '설명']:
                entry['columns'].append(dict(zip(['name', 'type', 'nullability', 'default', 'description'], values)))
            elif headings == ['컬럼', '참조 대상', '삭제 정책']:
                target = re.search(r'<a[^>]+>(.*?)</a>.*?<code>(.*?)</code>', cells[1], re.S)
                entry['foreignKeys'].append({'columns': values[0], 'targetTable': text(target[1]),
                                             'targetColumns': text(target[2]), 'onDelete': values[2]})
            else:
                raise ValueError(headings)
    keys = re.search(r'<b>기본키</b>\s*<code>(.*?)</code>.*?<b>유니크</b>\s*<code>(.*?)</code>', body, re.S)
    entry['primaryKey'] = text(keys[1])
    entry['uniqueKeys'] = [v.strip().strip('()') for v in text(keys[2]).split(';') if v.strip() not in ('', '—', '추가 유니크 없음')]
    for key in entry['uniqueKeys']:
        assert re.fullmatch(r'[a-z_, ]+', key), (name, key)
    for heading, code in re.findall(r'<h4>([^<]+)</h4><pre><code>(.*?)</code></pre>', body, re.S):
        key = {'CHECK 조건': 'checks', '인덱스 후보': 'indexes'}[heading]
        entry[key] = html.unescape(code).splitlines()
    tables.append(entry)

assert len(tables) == 46
assert sum(len(t['columns']) for t in tables) == 557
assert sum(len(t['foreignKeys']) for t in tables) == 105
fixture = {'source': source.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'tables': tables}
output = ROOT / 'backend/src/test/resources/database/design-v0.1.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: sum(len(t[k]) for t in tables) for k in ['columns', 'foreignKeys', 'uniqueKeys', 'checks', 'indexes']}))
