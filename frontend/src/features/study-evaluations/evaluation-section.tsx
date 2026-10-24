"use client";
import { useState } from "react";
import { useMutation,useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { createEvaluation,evaluationAction,listEvaluations,type Kind } from "./api";
export const kindLabels:Record<Kind,string>={DAILY:"일간",WEEKLY:"주간",MONTHLY:"월간",FINAL:"최종"};
export function EvaluationSection(){
  const router=useRouter();const query=useQuery({queryKey:["study-evaluations"],queryFn:listEvaluations});
  const [kind,setKind]=useState<Kind>("DAILY");const [selected,setSelected]=useState("");
  const create=useMutation({mutationFn:createEvaluation,onSuccess:e=>router.push(`/tests/study/${e.id}`)});
  const scopes=query.data?.scopes.filter(s=>s.kind===kind).toReversed()??[];
  const scope=scopes.find(s=>s.key===selected)??scopes[0];
  return <section aria-label="학습량에 따른 평가" className="mt-6 min-w-0 rounded-3xl border border-blue-200 bg-white p-5 shadow-sm sm:p-7">
    <p className="text-sm font-bold text-blue-700">완료한 학습으로 만드는 평가</p><h2 className="mt-1 text-2xl font-black text-slate-950">배운 만큼, 빠짐없이 확인하기</h2>
    <p className="mt-3 text-sm leading-6 text-slate-600">정규 학습과 추가 학습에서 완료한 목표를 함께 평가해요. 같은 목표는 한 번만 세고, 시간 안에 끝나지 않는 분량은 여러 회차로 나눠요.</p>
    <div className="mt-5 flex flex-wrap gap-2">{(Object.keys(kindLabels) as Kind[]).map(k=><Button key={k} variant={kind===k?"default":"outline"} onClick={()=>{setKind(k);setSelected("");}}>{kindLabels[k]} 평가</Button>)}</div>
    {query.isPending?<p className="mt-5 text-sm">완료한 학습을 확인하고 있어요.</p>:query.isError?<ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={()=>void query.refetch()}/>:!scope?<p className="mt-5 text-sm text-slate-600">본문 학습을 완료하면 평가 범위가 여기에 쌓여요.</p>:<>
      <label className="mt-5 block text-sm font-bold text-slate-700">평가 기간<select aria-label="평가 기간" value={scope.key} onChange={e=>setSelected(e.target.value)} className="mt-2 block min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-slate-900">{scopes.map(s=><option key={s.key} value={s.key}>{s.title}</option>)}</select></label>
      <div className="mt-4 rounded-2xl bg-blue-50 p-4"><strong className="text-lg text-slate-950">학습 목표 {scope.objectiveCount}개</strong><p className="mt-2 text-sm leading-6 text-slate-700">완료 본문 {scope.sourceCount}개 · 추가 학습 본문 {scope.extraSourceCount}개 포함<br/>새로 평가할 목표 {scope.newObjectiveCount}개 · {scope.newObjectiveCount}문항 · {scope.partCount}회차</p></div>
      <p className="mt-3 text-xs leading-6 text-slate-500">설정한 {query.data?.sessionMinutes??60}분 기준, 회차별 평가 한도는 {kind==="DAILY"?"30%":kind==="WEEKLY"?"50%":kind==="MONTHLY"?"70%":"전체 시간"}예요. 답안 작성과 해설 학습 시간을 합산해요. 문항은 객관식·단답·면접 답변으로 구성해요.</p>
      {scope.replacement?<p className="mt-4 text-sm font-bold text-blue-800">이 기간은 {kindLabels[scope.replacement as Kind]} 평가로 대신해요. <button className="underline" onClick={()=>{setKind(scope.replacement as Kind);setSelected("");}}>{kindLabels[scope.replacement as Kind]} 평가 보기</button></p>:!scope.available?<p className="mt-4 text-sm text-slate-600">해당 기간의 마지막 학습일 또는 정규 학습 완료 후 열려요.</p>:!scope.sourceReady?<div role="status" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-slate-800">
        <p className="font-bold">평가 자료 연결에 문제가 있어 준비할 수 없어요.</p>
        <p className="mt-2">학습 완료 기록은 보존돼 있어요. 아래 자료의 수정이 필요하며, 추가로 문제를 풀거나 다시 학습할 필요는 없어요.</p>
        {!!scope.sourceIssues?.length&&<ul className="mt-2 list-disc space-y-1 pl-5">{scope.sourceIssues.map(issue=><li key={issue.sourceId}>{issue.title} · {issue.reasons.join(" · ")}</li>)}</ul>}
        <Button variant="outline" className="mt-3" disabled={query.isFetching} onClick={()=>void query.refetch()}>상태 다시 확인</Button>
      </div>:<Button className="mt-4" disabled={scope.newObjectiveCount===0||create.isPending} onClick={()=>create.mutate(evaluationAction(scope.kind,scope.key))}>{create.isPending?"평가 준비 중…":scope.newObjectiveCount===0?"새로 추가된 목표 없음":"이 범위로 평가 준비"}</Button>}
    </>}
    {create.isError&&<ApiErrorFeedback viewModel={toApiErrorViewModel(create.error)} onRefresh={()=>create.variables&&create.mutate(create.variables)}/>}
    {(query.data?.evaluations.length??0)>0&&<div className="mt-6 border-t border-slate-200 pt-5"><h3 className="font-extrabold text-slate-950">내 평가 · 이어 풀기와 결과</h3><ul className="mt-3 grid gap-2">{query.data?.evaluations.map(e=><li key={e.id}><Link href={`/tests/study/${e.id}`} className="block rounded-xl border border-slate-200 p-4 hover:bg-slate-50"><span className="font-bold text-slate-900">{e.title}</span><span className="mt-1 block text-sm text-slate-600">{e.objectiveCount}문항 · {e.submittedPartCount}/{e.partCount}회차 제출 →</span></Link></li>)}</ul></div>}
  </section>;
}
