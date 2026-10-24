from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
LESSONS=[]
def lesson(n,title,objective,prose,code,lab,criteria,points,source,questions):
    rows=[]
    for i,line in enumerate(questions.strip().splitlines()):
        parts=[x.strip() for x in line.split('^')]
        if len(parts)!=5: raise ValueError((n,i,parts))
        prompt,correct,wrong1,wrong2,explanation=parts
        choices=[correct,wrong1,wrong2];shift=(n+i)%3;choices=choices[shift:]+choices[:shift]
        rows.append(dict(id=f'd{n:02}-q{i+1:02}',title=f'{title} · 확인 {i+1}',prompt=prompt,choices=choices,answer=choices.index(correct)+1,explanation=explanation,difficulty=1 if i<3 else 2 if i<8 else 3,use='PRACTICE' if i<10 else 'ASSESSMENT'))
    assert len(rows)==12,(n,len(rows))
    java='import java.util.*;\nimport java.util.concurrent.*;\nimport java.util.concurrent.atomic.*;\nimport java.math.*;\nimport java.io.*;\n\npublic class Lesson%02d {\n  public static void main(String[] args) throws Exception {\n%s\n    System.out.println("Lesson%02d OK");\n  }\n}\n'%(n,'\n'.join('    '+l for l in code.strip().splitlines()),n)
    (ROOT/'examples'/f'Lesson{n:02}.java').write_text(java,encoding='utf-8')
    url=source if source.startswith('https://') else 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/'+source
    LESSONS.append(dict(number=n,week=(n-1)//5+1,title=f'{n}일차 · {title}',objective=objective,body=f'# {title}\n\n{prose.strip()}\n\n## 실행 예제\n\nJDK 21에서 `java Lesson{n:02}.java`로 실행하세요. 조건을 만족하면 `Lesson{n:02} OK`가 출력됩니다. 실패하는 입력을 추가하고 어떤 규칙이 깨졌는지 설명해 보세요.\n\n```java\n{java}```',labTask=lab,acceptance=criteria.split('^'),keyPoints=points.split('^'),sources=[dict(title='Java 21 공식 문서 · '+title,url=url)],questions=rows,minutes=65 if n==20 else 90))
for week in range(1,5):
    exec(compile((ROOT/'authoring'/f'week{week:02}.py').read_text(encoding='utf-8'),f'week{week:02}.py','exec'))
assert len(LESSONS)==20
assessments=[]
def exam(id,type,day,title,seconds,questions): assessments.append(dict(id=id,type=type,day=day,title=title,seconds=seconds,questions=questions))
for n in range(1,21):
    exam(f'daily-{n:02}','DAILY',n,f'{n}일차 확인 시험',240,[f'd{n:02}-q11',f'd{n:02}-q12'])
    if n%5==0: exam(f'weekly-{n//5}','WEEKLY',n,f'{n//5}주차 연결 시험',600,[f'd{d:02}-q11' for d in range(n-4,n+1)])
exam('monthly-1','MONTHLY',20,'4주 종합 점검',960,[f'd{d:02}-q12' for d in [2,5,7,10,12,15,17,20]])
exam('final-1','FINAL',20,'Java 4주 최종 시험',1200,[f'd{d:02}-q12' for d in [1,3,4,6,8,9,11,13,16,19]])
weeks=[('값과 동등성',['참조와 값을 구분해 비교한다.','불변 값 객체와 금액의 계약을 테스트한다.']),('컬렉션과 제네릭',['자료구조를 접근 방식에 맞게 선택한다.','타입 경계와 스트림의 부수 효과를 설명한다.']),('오류와 테스트',['예외와 자원 해제 정책을 설계한다.','경계 사례와 실패 재현을 테스트로 남긴다.']),('동시성과 JVM',['공유 상태를 안전하게 갱신한다.','실행 시간과 메모리 관찰의 한계를 설명한다.'])]
bundle=dict(schemaVersion=1,code='JAVA-CORE-4W',version='2026.09.1',title='핵심 역량 학습 과정 · Java 4주',description='값과 동등성, 컬렉션과 제네릭, 오류와 테스트, 동시성과 JVM을 연결하는 20학습일 독립 과정. 본문·실습·확인 문제 200개와 복습형 시험 26개를 제공합니다. JDK 21 실행 환경이 필요합니다.',durationMonths=1,weeks=[dict(number=i+1,title=t,goals=g) for i,(t,g) in enumerate(weeks)],days=LESSONS,assessments=assessments)
(ROOT/'course.json').write_text(json.dumps(bundle,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Built {len(LESSONS)} lessons, {sum(len(d["questions"]) for d in LESSONS)} distinct questions, {len(assessments)} exams')
