"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Loader2, NotebookPen, RefreshCw, Shuffle, Target } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { failureMessage, formatLabel, type TaskSummary } from "./contract";
import { startToday, swapTask, taskKeys, todayQuery } from "./queries";

export function TodayScreen() {
  const client = useQueryClient();
  const router = useRouter();
  const today = useQuery(todayQuery);
  const refresh = () => client.invalidateQueries({ queryKey: taskKeys.all });
  const start = useMutation({ mutationFn: startToday, onSuccess: async task => { await refresh(); if (task.status !== "PREPARING" && task.status !== "FAILED") router.push(`/tasks/${task.taskId}`); } });
  const swap = useMutation({ mutationFn: swapTask, onSuccess: refresh });
  if (today.isPending) return <StatePanel state="loading"/>;
  if (today.isError) return <StatePanel state="error" onRefresh={() => void today.refetch()}/>;
  const { task, week, ai, recentCards } = today.data;
  const error = start.error ?? swap.error;

  return <div className="mx-auto grid max-w-4xl gap-5 pb-8">
    <header>
      <p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">오늘 과제</p>
      <h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">하루 30~60분, 과제 하나</h1>
      <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">약한 역량을 골라 드려요. 직접 판단해 쓰고, AI 꼬리질문으로 빈틈을 채워요.</p>
    </header>

    <section className="grid gap-3 sm:grid-cols-2">
      <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <p className="text-xs font-extrabold text-slate-500">이번 주 목표</p>
        <p className="mt-2 text-2xl font-black text-[#0a1837] dark:text-slate-50">{week.completed} / {week.target}회</p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className="h-full rounded-full bg-blue-600" style={{ width: `${Math.min(100, week.completed / week.target * 100)}%` }}/></div>
      </div>
      <Link href="/settings/ai-usage" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-blue-300 dark:border-slate-700 dark:bg-slate-900">
        <p className="text-xs font-extrabold text-slate-500">이번 달 AI 예산</p>
        <p className="mt-2 text-2xl font-black text-[#0a1837] dark:text-slate-50">{ai.enabled ? `${Math.floor(ai.remainingKrw).toLocaleString("ko-KR")}원 남음` : "AI 꺼짐"}</p>
        <p className="mt-1 text-xs text-slate-500">상한 {ai.limitKrw.toLocaleString("ko-KR")}원 · 상한에 닿으면 자가 채점으로 진행해요</p>
      </Link>
    </section>

    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7">
      {task ? <TaskCard task={task} busy={start.isPending || swap.isPending} onSwap={() => swap.mutate(task.taskId)} onRetry={() => start.mutate()}/> : <div>
        <h2 className="flex items-center gap-2 text-xl font-black text-[#0a1837] dark:text-slate-50"><Target size={22} className="text-blue-600"/>{week.completed > 0 ? "한 과제 더 할까요?" : "오늘 과제를 받아 보세요"}</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">우선순위·수준 차이·복습 시점을 보고 오늘 다룰 역량을 골라요.</p>
        <Button className="mt-5" disabled={start.isPending} onClick={() => start.mutate()}>{start.isPending ? <Loader2 className="animate-spin" size={17}/> : <ArrowRight size={17}/>}{week.completed > 0 ? "한 과제 더 받기" : "오늘 과제 받기"}</Button>
      </div>}
      {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600">{error instanceof Error ? error.message : "과제를 준비하지 못했어요."}</p>}
    </section>

    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7">
      <h2 className="flex items-center gap-2 text-lg font-black text-[#0a1837] dark:text-slate-50"><NotebookPen size={20} className="text-blue-600"/>최근 핵심 카드</h2>
      {recentCards.length ? <ul className="mt-4 grid gap-3">{recentCards.map(card => <li key={card.taskId}><Link href={`/tasks/${card.taskId}`} className="block rounded-2xl border border-slate-200 px-4 py-3 hover:border-blue-300 dark:border-slate-700"><span className="text-xs font-extrabold text-blue-600">{card.competencyCode}</span><strong className="mt-1 block text-sm text-slate-900 dark:text-slate-100">{card.title}</strong><span className="mt-1 block text-xs leading-5 text-slate-500">{card.interviewLine}</span></Link></li>)}</ul>
        : <p className="mt-3 text-sm text-slate-500">과제를 마치고 카드를 저장하면 여기에 쌓여요.</p>}
    </section>
  </div>;
}

function TaskCard({ task, busy, onSwap, onRetry }: { task: TaskSummary; busy: boolean; onSwap: () => void; onRetry: () => void }) {
  const canSwap = task.status === "READY" || task.status === "PREPARING";
  return <div>
    <div className="flex flex-wrap items-center gap-2 text-xs font-extrabold">
      <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300">{task.competency.code} · {task.competency.priority}</span>
      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">{formatLabel[task.format]}</span>
      {task.estimatedMinutes && <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">작성 약 {task.estimatedMinutes}분</span>}
    </div>
    <h2 className="mt-3 text-xl font-black text-[#0a1837] dark:text-slate-50">{task.title ?? task.competency.title}</h2>
    <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{task.planReason}</p>
    {task.status === "PREPARING" && <p className="mt-4 flex items-center gap-2 text-sm font-semibold text-slate-600 dark:text-slate-300"><Loader2 className="animate-spin" size={17}/>역량 시트로 과제를 만들고 있어요. 보통 1분 안에 끝나요.</p>}
    {task.status === "FAILED" && <div className="mt-4 rounded-2xl bg-rose-50 p-4 text-sm text-rose-700 dark:bg-rose-950/40 dark:text-rose-200"><p>{failureMessage(task.failureCode)}</p><Button className="mt-3" variant="outline" disabled={busy} onClick={onRetry}><RefreshCw size={16}/>다시 받기</Button></div>}
    <div className="mt-5 flex flex-wrap gap-2">
      {task.status !== "PREPARING" && task.status !== "FAILED" && <Button asChild><Link href={`/tasks/${task.taskId}`}>{task.status === "READY" ? "시작하기" : "이어 하기"}<ArrowRight size={17}/></Link></Button>}
      {canSwap && <Button variant="outline" disabled={busy} onClick={onSwap}><Shuffle size={16}/>다른 역량으로 바꾸기</Button>}
    </div>
  </div>;
}
