"use client";
import { QuestionSummary } from "@/components/question-content";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getAiWrongNotes } from "./practice-api";

export function AiWrongSection({ filter }: { filter: "ALL" | "UNRESOLVED" | "RESOLVED" }) {
  const status = filter === "ALL" ? undefined : filter === "UNRESOLVED" ? "OPEN" : "RESOLVED";
  const query = useQuery({ queryKey: ["ai-wrong-notes", status], queryFn: () => getAiWrongNotes(status) });
  return <section className="mt-8">
    <h2 className="text-lg font-black text-slate-950">AI 문제 오답</h2>
    <p className="mt-1 text-sm text-slate-600">AI 학습과 개인 연습 시험에서 틀린 문제를 모았습니다.</p>
    {query.isPending && <p className="mt-4 text-sm text-slate-500">AI 오답을 확인하는 중…</p>}
    {query.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={() => query.refetch()} /></div>}
    {query.data?.length === 0 && <p className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-500">해당 상태의 AI 오답이 없습니다.</p>}
    {query.data && query.data.length > 0 && <div className="mt-4 grid gap-3">{query.data.map(item =>
      <Link key={`${item.jobId}-${item.questionNo}`} href={`/review/ai/${item.jobId}/${item.questionNo}`} className="block rounded-2xl border border-slate-200 bg-white p-4 shadow-sm hover:border-blue-300">
        <div className="flex flex-wrap items-start justify-between gap-2"><h3 className="min-w-0 flex-1 font-extrabold text-slate-950"><QuestionSummary body={item.prompt} /></h3>
          <span className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${item.status === "OPEN" ? "bg-rose-50 text-rose-700" : "bg-emerald-50 text-emerald-700"}`}>{item.status === "OPEN" ? "미해결" : "해결됨"}</span></div>
        <p className="mt-2 text-xs text-slate-500">AI 생성 문제 · 오답 {item.wrongCount}회 · 문제 {item.questionNo}</p>
      </Link>)}</div>}
  </section>;
}
