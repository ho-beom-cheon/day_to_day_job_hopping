"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { categoryLabels, getExtraHistory, modeLabels } from "./api";

export const extraLearningHistoryQuery = {
  queryKey: ["extra-learning"] as const,
  queryFn: getExtraHistory,
};

export function ActiveExtraSessions({ enabled = true }: { enabled?: boolean }) {
  const history = useQuery({ ...extraLearningHistoryQuery, enabled });
  const active = (history.data ?? []).filter(entry =>
    entry.status === "IN_PROGRESS" && entry.mode !== "AHEAD" && entry.courseStatus !== "CANCELED",
  );
  if (!enabled || history.isPending || (!history.isError && active.length === 0)) return null;

  return <section className="rounded-[24px] border border-blue-200 bg-blue-50/70 p-5 dark:border-slate-700 dark:bg-slate-900 sm:p-6" aria-labelledby="active-extra-title">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div><p className="text-xs font-extrabold text-blue-700 dark:text-blue-300">선택해서 시작한 학습</p><h2 id="active-extra-title" className="mt-1 text-lg font-black text-slate-950">진행 중인 추가 학습</h2></div>
      <Link href="/learning/extra" className="inline-flex min-h-10 items-center gap-1 text-sm font-bold text-blue-700 dark:text-blue-300">전체 보기<ArrowRight size={16}/></Link>
    </div>
    {history.isError ? <p className="mt-4 text-sm text-slate-600">추가 학습 목록을 불러오지 못했어요. 전체 보기에서 확인해 주세요.</p> :
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">{active.map(entry => <li key={entry.sessionId} className="min-w-0"><Link href={`/workspace/learning/${entry.sessionId}`} className="flex min-h-24 items-center justify-between gap-3 rounded-2xl border border-blue-100 bg-white p-4 hover:border-blue-400 dark:border-slate-700 dark:bg-slate-800"><span className="min-w-0"><span className="block text-xs font-bold text-blue-700 dark:text-blue-300">{modeLabels[entry.mode]} · {categoryLabels[entry.category] ?? entry.category}</span><strong className="mt-1 block text-sm font-black text-slate-950">{entry.title}</strong><span className="mt-1 block text-xs text-slate-500">예상 {entry.targetMinutes}분</span></span><span className="shrink-0 text-xs font-extrabold text-blue-700 dark:text-blue-300">이어하기 →</span></Link></li>)}</ul>}
  </section>;
}
