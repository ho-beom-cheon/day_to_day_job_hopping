"use client";
import { QuestionSummary } from "@/components/question-content";
import { useQuery } from "@tanstack/react-query";
import { BookOpenCheck, ChevronRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { AiWrongSection } from "@/features/ai/ai-wrong-section";
import { IllustrationIcon } from "@/components/ui/illustration-icon";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getWrongAnswers } from "./api";

type Filter="ALL"|"UNRESOLVED"|"RESOLVED";
export function ReviewListScreen(){
  const [filter,setFilter]=useState<Filter>("ALL");
  const list=useQuery({queryKey:["wrong-answers",filter],queryFn:()=>getWrongAnswers(filter==="ALL"?undefined:filter)});
  return <div><header className="flex items-center gap-4"><IllustrationIcon name="review" size="lg"/><div><p className="text-sm font-extrabold text-blue-600">복습</p><h1 className="mt-1 text-2xl font-black text-slate-950 sm:text-3xl">오답노트</h1><p className="mt-2 text-sm text-slate-600">틀린 이유를 다시 확인하고 해결 상태를 관리해요.</p></div></header>
    <div className="mt-6 grid grid-cols-3 rounded-2xl bg-slate-100 p-1 dark:bg-[#111827]" role="group" aria-label="오답 상태 필터">{([['ALL','전체'],['UNRESOLVED','미해결'],['RESOLVED','해결 완료']] as const).map(([value,label])=><button key={value} onClick={()=>setFilter(value)} aria-pressed={filter===value} className={`min-h-11 rounded-xl text-sm font-extrabold ${filter===value?"bg-blue-600 text-white shadow-sm":"text-slate-500"}`}>{label}</button>)}</div>
    <section className="mt-5"><h2 className="mb-3 text-lg font-black text-slate-950">기본 문제 오답</h2>{list.isPending?<StatePanel state="loading"/>:list.isError?<ApiErrorFeedback viewModel={toApiErrorViewModel(list.error)} onRefresh={()=>list.refetch()}/>:list.data.content.length===0?<p className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-500">해당 상태의 기본 문제 오답이 없습니다.</p>:<div className="grid gap-3">{list.data.content.map(item=><Link key={item.wrongAnswerId} href={`/review/${item.wrongAnswerId}`} className="group flex min-w-0 items-center gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-blue-300 sm:p-5"><span className={`grid size-12 shrink-0 place-items-center rounded-2xl ${item.status==="RESOLVED"?"bg-emerald-50 text-emerald-600":"bg-blue-50 text-blue-600"}`}><BookOpenCheck/></span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h3 className="min-w-0 flex-1 text-slate-950"><QuestionSummary body={item.question} /></h3><span className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${item.status==="RESOLVED"?"bg-emerald-50 text-emerald-700":"bg-rose-50 text-rose-700"}`}>{item.status==="RESOLVED"?"해결됨":"미해결"}</span></div><p className="mt-1 text-sm text-slate-500">{item.category} · {new Intl.DateTimeFormat("ko-KR",{dateStyle:"medium"}).format(new Date(item.lastWrongAt))} · 오답 {item.wrongCount}회</p></div><ChevronRight className="shrink-0 text-slate-400 group-hover:text-blue-600"/></Link>)}</div>}</section>
    <AiWrongSection filter={filter} />
  </div>;
}
