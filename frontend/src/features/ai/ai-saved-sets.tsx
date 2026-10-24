"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getSavedAiSets, startAiRun } from "./practice-api";

export function AiSavedSets() {
  const router = useRouter();
  const query = useQuery({ queryKey: ["saved-ai-sets"], queryFn: getSavedAiSets });
  const start = useMutation({ mutationFn: (jobId: string) => startAiRun(jobId, "STUDY"),
    onSuccess: run => router.push(`/practice/ai/runs/${run.runId}`) });

  return <section className="mt-7 rounded-3xl border border-blue-200 bg-blue-50/50 p-5">
    <h2 className="text-lg font-black text-slate-950">저장된 AI 문제</h2>
    <p className="mt-1 text-sm text-slate-600">검수 전 AI 문제입니다. 생성된 정답은 틀릴 수 있어요. 오류로 표시한 문항은 이후 다시 학습에서 제외됩니다.</p>
    {query.isPending && <p className="mt-3 text-sm text-slate-500">저장된 문제를 확인하는 중…</p>}
    {query.isError && <div className="mt-3"><ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={() => query.refetch()} /></div>}
    {query.data?.length === 0 && <p className="mt-3 text-sm text-slate-500">아직 생성한 AI 문제가 없습니다.</p>}
    {query.data && query.data.length > 0 && <div className="mt-4 grid gap-3">{query.data.map(item =>
      <div key={item.jobId} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4">
        <div><h3 className="font-extrabold text-slate-950">{item.title}</h3><p className="mt-1 text-xs text-slate-500">{item.generationSequence}번째 생성 · {item.questionCount}문제 · 개인 학습</p></div>
        <div className="flex flex-wrap gap-2"><Link href={`/practice/ai/${item.jobId}`} className="inline-flex min-h-10 items-center rounded-xl border border-blue-200 px-3 text-sm font-bold text-blue-700">기존 풀이 보기</Link>
          <Button disabled={start.isPending} onClick={() => start.mutate(item.jobId)}>다시 학습</Button></div>
      </div>)}</div>}
    {start.isError && <div className="mt-3"><ApiErrorFeedback viewModel={toApiErrorViewModel(start.error)} onRefresh={() => start.reset()} /></div>}
  </section>;
}
