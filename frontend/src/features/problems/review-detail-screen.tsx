"use client";
import { QuestionContent } from "@/components/question-content";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, RotateCcw, Save } from "lucide-react";
import Link from "next/link";
import { useRef } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getWrongAnswer, reopenWrong, resolveWrong, saveWrongNote } from "./api";

export function ReviewDetailScreen({wrongAnswerId}:{wrongAnswerId:string}){
  const client=useQueryClient(); const query=useQuery({queryKey:["wrong-answer",wrongAnswerId],queryFn:()=>getWrongAnswer(wrongAnswerId)}); const noteRef=useRef<HTMLTextAreaElement>(null);
  const refresh=async()=>{await Promise.all([client.invalidateQueries({queryKey:["wrong-answer",wrongAnswerId]}),client.invalidateQueries({queryKey:["wrong-answers"]})])};
  const noteMutation=useMutation({mutationFn:()=>{if(!query.data?.etag) throw new Error("최신 revision을 확인할 수 없습니다."); return saveWrongNote(wrongAnswerId,noteRef.current?.value??query.data.note,query.data.etag)},onSuccess:refresh});
  const statusMutation=useMutation({mutationFn:()=>query.data?.status==="RESOLVED"?reopenWrong(wrongAnswerId):resolveWrong(wrongAnswerId),onSuccess:refresh});
  if(query.isPending)return <StatePanel state="loading"/>; if(query.isError)return <ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={()=>query.refetch()}/>;
  const item=query.data; const mutationError=noteMutation.error??statusMutation.error;
  const recoverFromMutationError=()=>{noteMutation.reset();statusMutation.reset();void query.refetch()};
  return <div className="mx-auto max-w-3xl"><header className="flex flex-wrap items-start justify-between gap-3"><div><Link href="/review" className="text-sm font-bold text-blue-700">← 오답노트</Link><h1 className="mt-2 text-2xl font-black text-slate-950">오답 복습</h1></div><span className={`rounded-full px-3 py-2 text-xs font-extrabold ${item.status==="RESOLVED"?"bg-emerald-50 text-emerald-700":"bg-rose-50 text-rose-700"}`}>{item.status==="RESOLVED"?"해결됨":"미해결"}</span></header>
    <article className="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8"><p className="text-xs font-extrabold uppercase tracking-wider text-blue-600">{item.category} · {item.difficulty}</p><div className="mt-4"><h2 className="sr-only">문제 내용</h2><QuestionContent body={item.question} /></div><div className="mt-6 grid gap-3 sm:grid-cols-2"><div className="rounded-2xl bg-rose-50 p-4"><p className="text-xs font-bold text-rose-600">내 마지막 오답</p><p className="mt-2 font-extrabold text-rose-950">{item.myLastAnswer??"미응답"}</p></div><div className="rounded-2xl bg-emerald-50 p-4"><p className="text-xs font-bold text-emerald-600">정답</p><p className="mt-2 font-extrabold text-emerald-950">{item.correctAnswer}</p></div></div><section className="mt-5 rounded-2xl bg-slate-50 p-5"><h3 className="font-extrabold text-slate-900">해설</h3><div className="mt-3"><QuestionContent body={item.explanation} compact /></div></section>
      <label className="mt-6 block text-sm font-extrabold text-slate-800" htmlFor="review-note">나만의 복습 메모</label><textarea key={`${item.wrongAnswerId}-${item.revision}`} ref={noteRef} id="review-note" maxLength={10000} defaultValue={item.note} className="mt-2 min-h-28 w-full resize-y rounded-2xl border border-slate-200 p-4 text-sm outline-none focus:border-blue-500" placeholder="헷갈린 개념과 다음에 확인할 점을 적어두세요."/><div className="mt-3 flex flex-wrap justify-between gap-3"><Link href={`/practice/${item.problemId}`} className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-blue-200 px-4 text-sm font-extrabold text-blue-700"><RotateCcw size={16}/>다시 풀기</Link><div className="flex gap-2"><Button variant="outline" disabled={noteMutation.isPending} onClick={()=>noteMutation.mutate()}><Save size={16}/>메모 저장</Button><Button disabled={statusMutation.isPending} onClick={()=>statusMutation.mutate()}>{item.status==="RESOLVED"?<><RotateCcw size={16}/>다시 복습</>:<><CheckCircle2 size={16}/>학습 확인 완료</>}</Button></div></div>
      {mutationError&&<div className="mt-5"><ApiErrorFeedback viewModel={toApiErrorViewModel(mutationError)} onRefresh={recoverFromMutationError}/></div>}
    </article>
  </div>;
}
