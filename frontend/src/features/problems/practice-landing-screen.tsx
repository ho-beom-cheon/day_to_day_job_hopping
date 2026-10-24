"use client";

import { useQueries, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { StatePanel } from "@/components/ui/state-panel";
import { AiSavedSets } from "@/features/ai/ai-saved-sets";
import { dashboardQuery, learningContentQuery, sessionContentsQuery } from "@/features/today-learning/queries";
import { getProblem } from "./api";

export function PracticeLandingScreen() {
  const dashboard = useQuery(dashboardQuery);
  const sessions = dashboard.data?.today?.sessions ?? [];
  const sessionContents = useQueries({ queries: sessions.map(session => sessionContentsQuery(session.sessionId)) });
  const summaries = sessionContents.flatMap(query => query.data ?? []);
  const contents = useQueries({ queries: summaries.map(summary => learningContentQuery(summary.contentId)) });
  const entries = contents.flatMap(query => query.data?.problems.map(problem => ({ ...problem, contentId: query.data.contentId, sessionId: query.data.sessionId, contentTitle: query.data.title })) ?? []);
  const problems = useQueries({ queries: entries.map(entry => ({ queryKey: ["problems", entry.problemId, entry.contentId], queryFn: () => getProblem(entry.problemId, entry.contentId) })) });

  const pending = dashboard.isPending || sessionContents.some(query => query.isPending) || contents.some(query => query.isPending) || problems.some(query => query.isPending);
  const failed = dashboard.isError || sessionContents.some(query => query.isError) || contents.some(query => query.isError) || problems.some(query => query.isError);

  return <div className="mx-auto max-w-3xl">
    <header><p className="text-sm font-extrabold text-blue-600">문제 풀이</p><h1 className="mt-1 text-2xl font-black text-slate-950">오늘의 확인 문제</h1><p className="mt-2 text-sm text-slate-600">학습 콘텐츠에 연결된 문제를 하나씩 풀고 완료 상태를 확인하세요.</p></header>
    <section className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-5"><h2 className="font-extrabold text-slate-950">다른 분야의 문제도 풀고 싶나요?</h2><p className="mt-2 text-sm leading-6 text-slate-600">정규 과정의 앞 일차를 끝낼 필요 없이 분야와 주제를 고를 수 있어요. 기존 확인 문제를 바로 풀고, 학습 본문에서 새 AI 문제도 요청할 수 있습니다.</p><Link href="/learning/extra" className="mt-3 inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-4 text-sm font-bold text-white">분야와 주제 고르기 →</Link></section>
    <AiSavedSets />
    {pending ? <div className="mt-6"><StatePanel state="loading" /></div> : failed ? <div className="mt-6"><StatePanel state="error" onRefresh={() => { void dashboard.refetch(); void Promise.all(sessionContents.map(query => query.refetch())); void Promise.all(contents.map(query => query.refetch())); void Promise.all(problems.map(query => query.refetch())); }} /></div> : entries.length === 0 ? <div className="mt-6"><StatePanel state="empty" /><Link href="/learning" className="mt-4 inline-flex min-h-11 items-center text-sm font-bold text-blue-700">학습 목록으로 돌아가기 →</Link></div> : <>
      <p className="mt-6 text-sm font-bold text-slate-600">제출 {problems.filter(query => query.data?.answered).length} / {entries.length}문제</p>
      <ol className="mt-3 grid gap-3">{entries.map((entry, index) => <li key={`${entry.sessionId}-${entry.problemId}`} className="rounded-2xl border border-slate-200 bg-white p-4"><p className="text-xs font-bold text-slate-500">{entry.contentTitle}</p><Link href={`/practice/${entry.problemId}?contentId=${entry.contentId}`} className="mt-2 flex min-h-11 items-center justify-between gap-3 text-sm font-extrabold text-blue-700"><span>{index + 1}. {entry.title}</span><span className="shrink-0 text-xs">{problems[index]?.data?.answered ? "다시 풀기 →" : "문제 풀기 →"}</span></Link><Link href={`/workspace/learning/${entry.sessionId}`} className="mt-2 inline-flex text-xs font-bold text-slate-600">학습으로 돌아가기</Link></li>)}</ol>
    </>}
  </div>;
}
