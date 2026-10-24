"use client";
import { QuestionContent } from "@/components/question-content";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CheckCircle2, Circle, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { IllustrationIcon } from "@/components/ui/illustration-icon";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getProblem, submitAttempt } from "./api";
import { completeLearningContent, learningContentQuery, todayLearningKeys } from "@/features/today-learning/queries";

const difficulty:Record<string,string>={BASIC:"기초",INTERMEDIATE:"중급",ADVANCED:"고급",EASY:"하",MEDIUM:"중",HARD:"상"};
export function PracticeScreen({problemId,contentId}:{problemId:string;contentId?:string}) {
  return <PracticeQuestion key={`${problemId}:${contentId ?? ""}`} problemId={problemId} contentId={contentId}/>;
}

function PracticeQuestion({problemId,contentId}:{problemId:string;contentId?:string}) {
  const client=useQueryClient();
  const problem=useQuery({queryKey:["problems",problemId,contentId],queryFn:()=>getProblem(problemId,contentId)});
  const content=useQuery({...learningContentQuery(problem.data?.contentId??"0"),enabled:!!problem.data?.contentId});
  const problemStatuses=useQueries({queries:(content.data?.problems??[]).map(entry=>({queryKey:["problems",entry.problemId,content.data?.contentId],queryFn:()=>getProblem(entry.problemId,content.data?.contentId)}))});
  const [answer,setAnswer]=useState("");
  const attempt=useMutation({mutationFn:()=>submitAttempt(problemId,answer,problem.data?.contentId??null),onSuccess:()=>{void client.invalidateQueries({queryKey:["problems",problemId]});}});
  const complete=useMutation({mutationFn:()=>completeLearningContent(content.data!.contentId),onSuccess:async()=>{if(!content.data)return;await Promise.all([client.invalidateQueries({queryKey:todayLearningKeys.content(content.data.contentId)}),client.invalidateQueries({queryKey:todayLearningKeys.contents(content.data.sessionId)}),client.invalidateQueries({queryKey:todayLearningKeys.session(content.data.sessionId)}),client.invalidateQueries({queryKey:todayLearningKeys.dashboard()})]);}});
  if(problem.isPending) return <StatePanel state="loading"/>;
  if(problem.isError) return <ApiErrorFeedback viewModel={toApiErrorViewModel(problem.error)} onRefresh={()=>problem.refetch()}/>;
  const item=problem.data; const choices=item.type==="TRUE_FALSE"?[{optionNo:1,text:"참",answer:"TRUE"},{optionNo:2,text:"거짓",answer:"FALSE"}]:item.options.map(o=>({...o,answer:String(o.optionNo)}));
  const entries=content.data?.problems??[];
  const currentIndex=entries.findIndex(entry=>entry.problemId===problemId);
  const orderedEntries=currentIndex<0?[]:[...entries.slice(currentIndex+1),...entries.slice(0,currentIndex)];
  const nextProblem=orderedEntries.find(entry=>problemStatuses[entries.findIndex(candidate=>candidate.problemId===entry.problemId)]?.data?.answered===false);
  const statusesReady=problemStatuses.every(query=>query.isSuccess);
  const allAttempted=statusesReady&&entries.length>0&&entries.every((entry,index)=>entry.problemId===problemId?attempt.isSuccess||problemStatuses[index].data?.answered:problemStatuses[index].data?.answered);
  const sessionHref=content.data?`/workspace/learning/${content.data.sessionId}`:"/practice";
  return <div className="mx-auto max-w-3xl">
    <header className="flex flex-wrap items-start justify-between gap-3"><div className="flex items-center gap-4"><IllustrationIcon name="practice" size="lg"/><div><p className="text-sm font-extrabold text-blue-600">문제 풀이</p><h1 className="mt-1 text-xl font-black text-slate-950 sm:text-2xl">개념을 직접 확인해 보세요</h1></div></div><div className="flex flex-wrap gap-2"><Link className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700" href={content.data ? `/workspace/learning/${content.data.sessionId}` : "/practice"}>학습으로 돌아가기</Link><Link className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700" href="/review">오답노트</Link></div></header>
    {content.data&&currentIndex>=0&&<p className="mt-5 text-sm font-bold text-slate-600">확인 문제 {currentIndex+1} / {entries.length}</p>}
    {!attempt.isSuccess&&item.previousAttemptCount>0&&<div className="mt-5 rounded-2xl bg-amber-50 px-4 py-3 text-sm font-bold text-amber-800"><RefreshCw className="mr-2 inline" size={16}/>재풀이 {item.previousAttemptCount+1}회차예요. 첫 풀이 정답률과는 별도로 기록됩니다.</div>}
    <article className="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xl font-black text-blue-600">Q.</span><span className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-extrabold text-blue-700">난이도: {difficulty[item.difficulty]??item.difficulty}</span></div>
      <div className="mt-5"><h2 className="sr-only">문제 내용</h2><QuestionContent body={item.question} /></div>
      <fieldset className="mt-7 grid min-w-0 gap-3" disabled={attempt.isPending||attempt.isSuccess}><legend className="sr-only">답 선택</legend>{choices.map(choice=><label key={choice.answer} className={`flex min-h-16 cursor-pointer items-start gap-4 rounded-2xl border px-4 py-4 text-sm font-normal focus-within:ring-2 focus-within:ring-blue-500 leading-6 transition sm:text-base ${answer===choice.answer?"border-blue-500 bg-blue-50 text-blue-950 dark:text-blue-100":"border-slate-200 hover:border-blue-300"}`}><input className="sr-only" type="radio" name="answer" value={choice.answer} checked={answer===choice.answer} onChange={e=>setAnswer(e.target.value)}/>{answer===choice.answer?<CheckCircle2 className="shrink-0 text-blue-600"/>:<Circle className="shrink-0 text-slate-300"/>}<div className="flex min-w-0 flex-1 gap-2">{item.type==="MULTIPLE_CHOICE"&&<span className="shrink-0 font-semibold">{choice.optionNo}.</span>}<div className="min-w-0 flex-1"><QuestionContent body={choice.text} compact /></div></div></label>)}</fieldset>
      {!attempt.data&&<div className="mt-6 flex items-center justify-between gap-4 rounded-2xl bg-slate-50 p-4"><p className="min-w-0 text-sm text-slate-500">제출하면 정답과 해설을 확인할 수 있어요.</p><Button className="shrink-0 px-4 sm:px-5" disabled={!answer||attempt.isPending} onClick={()=>attempt.mutate()}>{attempt.isPending?"채점 중":<span className="text-center leading-5">답안<span className="block sm:inline"> 제출</span></span>}</Button></div>}
      {attempt.isError&&<div className="mt-6"><ApiErrorFeedback viewModel={toApiErrorViewModel(attempt.error)} onRefresh={()=>attempt.reset()}/></div>}
      {attempt.data&&<section aria-live="polite" className={`mt-6 rounded-2xl border p-5 ${attempt.data.correct?"border-emerald-200 bg-emerald-50":"border-rose-200 bg-rose-50"}`}><h3 className={`flex items-center gap-2 font-extrabold ${attempt.data.correct?"text-emerald-800":"text-rose-800"}`}>{attempt.data.correct?<CheckCircle2 size={20}/>:<AlertCircle size={20}/>} {attempt.data.correct?"정답이에요":"오답이에요"}</h3><p className="mt-3 text-sm font-bold text-slate-800">정답: {attempt.data.correctAnswer}</p><div className="mt-3"><QuestionContent body={attempt.data.explanation} compact /></div><div className="mt-5 flex flex-wrap items-center gap-3">{nextProblem&&<Link href={`/practice/${nextProblem.problemId}${content.data ? `?contentId=${content.data.contentId}` : ""}`} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-5 text-sm font-extrabold text-white hover:bg-blue-700">다음 문제 풀기 →</Link>}{allAttempted&&content.data?.status!=="COMPLETED"&&!complete.isSuccess&&<Button disabled={complete.isPending} onClick={()=>complete.mutate()}>{complete.isPending?"완료 저장 중…":"확인 문제 완료하기"}</Button>}{complete.isSuccess&&<p className="text-sm font-extrabold text-emerald-800">콘텐츠 완료가 저장됐어요.</p>}{!nextProblem&&<Link href={sessionHref} className="inline-flex min-h-11 items-center text-sm font-extrabold text-blue-700">{content.data?.status==="COMPLETED"||complete.isSuccess?"학습 세션 마무리하기 →":"학습으로 돌아가기 →"}</Link>}{attempt.data.wrongAnswerId&&<Link href={`/review/${attempt.data.wrongAnswerId}`} className="inline-flex min-h-11 items-center text-sm font-extrabold text-blue-700">오답노트에서 복습하기 →</Link>}</div>{complete.isError&&<div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(complete.error)} onRefresh={()=>complete.reset()}/></div>}</section>}
    </article>
  </div>;
}
