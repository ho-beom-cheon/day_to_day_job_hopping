서버 plan.slots의 id/type/objectiveIds/difficulty/seconds와 순서 그대로 문항을 만든다. 임의로 슬롯을 줄이거나 추가하지 않는다. seconds에는 풀이와 해설이 모두 포함된다.
각 문항의 objectiveIds를 모두 실질적으로 평가하고 각 목표마다 적어도 한 개의 sourceEvidence를 붙인다. sourceId/version/excerpt는 입력과 정확히 일치해야 한다.
MCQ는 서로 다른 선택지 4개, 1부터 시작하는 정답 한 개와 각 오답 이유를 제공한다. 조건이 부족해 복수 정답이 되면 FAILED.
SHORT_ANSWER/INTERVIEW는 choices=[],answer=0,wrongAnswerExplanations=[]를 사용한다. modelAnswer와 criteria(id/description/points/acceptableAlternatives)를 제공한다.
모든 문항은 maxPoints=10이다. 서술형 criteria의 points 합계는 10이다. 필수 개념, 근거, 허용 대안, 부분 점수를 명확히 한다. 문장 길이/키워드 일치만으로 점수를 정하지 않는다.
원인→수정안→영향→테스트 중 목표에 해당하는 사고를 묻는다. 상황과 질문을 짧은 문단으로 나누고 코드/SQL/로그는 언어 태그가 있는 코드 블록에 넣는다.
월간: 해당 교육 월에 학습한 정규+추가 목표만 평가한다. 같은 날의 일간·주간을 대체한다. 총시간의 70%가 평가+해설이다. 이전 달 누적 시험을 만들지 않는다.

