"""Read-only public documentation link audit; stores no page contents or credentials."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen
import json

base=Path(__file__).resolve().parent
sources=json.loads((base/'catalog/sources.json').read_text(encoding='utf-8'))
def check(item):
    key,(title,url)=item
    try:
        with urlopen(Request(url,headers={'User-Agent':'Mozilla/5.0 DocumentationLinkCheck'}),timeout=20) as response:
            return {'key':key,'url':url,'resolvedUrl':response.url,'status':response.status}
    except Exception as error:
        return {'key':key,'url':url,'error':str(error)}
with ThreadPoolExecutor(max_workers=8) as pool:
    results=list(pool.map(check,sources.items()))
(base/'source-link-audit.json').write_text(json.dumps({'checkedDate':'2026-09-27','note':'Link availability only; does not certify every teaching claim.','results':results},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'links':len(results),'ok':sum(x.get('status')==200 for x in results),'failed':[x for x in results if 'error' in x]},ensure_ascii=False))
