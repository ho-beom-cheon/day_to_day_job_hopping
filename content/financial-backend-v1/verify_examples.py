"""Executable checks for the numerical/algorithmic worked examples in the manuscript."""
from bisect import bisect_left
from collections import deque
from decimal import Decimal, ROUND_HALF_UP
from build_catalog import lessons

data = lessons()
assert len(data) == 98
assert min(len(x["body"]) for x in data) >= 900  # 20-question eligibility upper bound
assert {x["week"] for x in data} == set(range(1, 27))
for week in range(1, 27):
    assert len({x["subject"] for x in data if x["week"] == week}) >= 3
for x in data:
    q = x["questions"][0]
    assert q["choices"][q["answer"]-1] == x["keyPoints"][0]

d = Decimal
assert d("0.005").quantize(d("0.01"), rounding=ROUND_HALF_UP)*2 == d("0.02")
assert (d("0.005")*2).quantize(d("0.01"), rounding=ROUND_HALF_UP) == d("0.01")
assert d(1000000)*d("0.0365")*10/365 == 1000
assert [(300-i*100)*d("0.1")+100 for i in range(3)] == [130,120,110]
assert 70-20 == 50 and 100-60-30 == 10
assert sum([100,200]*3) == 900 and sum([100,200]) == 300
assert bisect_left([1,3,3,3,8],3) == 1 and bisect_left([1,3,3,3,8],9) == 5
values=[10,20,-5,7]
prefix=[0]
for v in values: prefix.append(prefix[-1]+v)
assert prefix == [0,10,30,25,32] and prefix[3]-prefix[1] == 15
dp=[0]+[100]*6
for n in range(1,7): dp[n]=min(dp[n-c]+1 for c in [1,3,4] if c<=n)
assert dp[6] == 2
values=[1,-1,5]
assert min(j-i for i in range(len(values)) for j in range(i+1,len(values)+1) if sum(values[i:j])>=5) == 1
graph={"A":["B","C"],"B":["D"],"C":["D"],"D":["A"]}
queue=deque(["A"]); visited={"A"}
while queue:
    for node in graph[queue.popleft()]:
        if node not in visited: visited.add(node); queue.append(node)
assert visited == {"A","B","C","D"}
assert 3**3 == 27
print("PASS: 98 source/answer contracts, 26 mixed weeks, numerical and algorithm examples")
