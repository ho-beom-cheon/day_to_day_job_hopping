"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { HelpHint } from "@/components/ui/help-hint";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getAiExamHistory, getSavedAiSets, startAiRun } from "./practice-api";

export function AiExamSection() {
  const router = useRouter();
  const sets = useQuery({ queryKey: ["saved-ai-sets"], queryFn: getSavedAiSets });
  const history = useQuery({ queryKey: ["ai-exam-history"], queryFn: getAiExamHistory });
  const start = useMutation({ mutationFn: (jobId: string) => startAiRun(jobId, "EXAM"),
    onSuccess: run => router.push(`/tests/ai/${run.runId}`) });
  return <section className="mt-8 rounded-3xl border border-indigo-200 bg-indigo-50/50 p-5">
    <div className="flex items-center gap-2"><h2 className="text-lg font-black text-slate-950">저장된 AI 문제로 연습 시험</h2><HelpHint label="AI 연습 시험" summary="학습 중 생성해 저장한 문제를 다시 시험 방식으로 풀어요." items={[
      { term: "시작", description: "저장된 문제 묶음에서 연습 시험을 선택해요." },
      { term: "제출 후", description: "점수와 정답, 해설을 확인할 수 있어요." },
    ]} note="이 결과는 개인 연습 기록이며 공식 시험 점수에는 포함되지 않아요." /></div>
    <p className="mt-1 text-sm text-slate-600">검수 전 AI 문제로 연습합니다. 생성된 정답은 틀릴 수 있으며, 오류로 표시한 문항은 이후 풀이와 개인 점수에서 제외됩니다. 공식 평가 점수에는 반영되지 않습니다.</p>
    {(sets.isPending || history.isPending) && <p className="mt-4 text-sm text-slate-500">연습 시험을 확인하는 중…</p>}
    {sets.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(sets.error)} onRefresh={() => sets.refetch()} /></div>}
    {history.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(history.error)} onRefresh={() => history.refetch()} /></div>}
    {sets.data?.length === 0 && <p className="mt-4 text-sm text-slate-500">저장된 AI 문제가 생기면 여기에서 연습 시험을 시작할 수 있어요.</p>}
    {sets.data && history.data && <div className="mt-4 grid gap-3">{sets.data.map(item => {
      const active = history.data.find(run => run.jobId === item.jobId && run.status === "IN_PROGRESS");
      return <div key={item.jobId} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4">
        <div><h3 className="font-extrabold text-slate-950">{item.title}</h3><p className="mt-1 text-xs text-slate-500">{item.generationSequence}번째 생성 · {item.questionCount}문제 · 제출 완료 후 점수와 해설 공개</p></div>
        {active ? <Link href={`/tests/ai/${active.runId}`} className="inline-flex min-h-10 items-center rounded-xl bg-indigo-600 px-4 text-sm font-bold text-white">시험 이어서 보기</Link>
          : <Button disabled={start.isPending} onClick={() => start.mutate(item.jobId)}>연습 시험 시작</Button>}
      </div>;
    })}</div>}
    {history.data && history.data.length > 0 && <div className="mt-5"><h3 className="text-sm font-extrabold text-slate-900">최근 연습 기록</h3><div className="mt-2 grid gap-2">{history.data.map(run =>
      <Link key={run.runId} href={`/tests/ai/${run.runId}`} className="flex justify-between gap-3 rounded-xl bg-white px-4 py-3 text-sm font-bold text-blue-700">
        <span className="truncate">{run.title}</span><span>{run.status === "COMPLETED" ? `AI 채점 ${run.correctCount}/${run.totalCount}` : "진행 중"}</span>
      </Link>)}</div></div>}
    {start.isError && <div className="mt-3"><ApiErrorFeedback viewModel={toApiErrorViewModel(start.error)} onRefresh={() => start.reset()} /></div>}
  </section>;
}
