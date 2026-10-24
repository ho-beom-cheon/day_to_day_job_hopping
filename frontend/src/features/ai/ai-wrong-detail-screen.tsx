"use client";
import { QuestionContent } from "@/components/question-content";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getAiWrongDetail, startAiRun, updateAiWrong } from "./practice-api";

export function AiWrongDetailScreen({ jobId, questionNo }: { jobId: string; questionNo: number }) {
  const router = useRouter();
  const client = useQueryClient();
  const key = ["ai-wrong-detail", jobId, questionNo] as const;
  const query = useQuery({ queryKey: key, queryFn: () => getAiWrongDetail(jobId, questionNo) });
  const noteRef = useRef<HTMLTextAreaElement>(null);
  const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: key }), client.invalidateQueries({ queryKey: ["ai-wrong-notes"] })]); };
  const save = useMutation({ mutationFn: () => updateAiWrong(jobId, questionNo, { note: noteRef.current?.value ?? "" }), onSuccess: refresh });
  const status = useMutation({ mutationFn: () => updateAiWrong(jobId, questionNo, { status: query.data?.status === "OPEN" ? "RESOLVED" : "OPEN" }), onSuccess: refresh });
  const review = useMutation({ mutationFn: () => startAiRun(jobId, "REVIEW"), onSuccess: run => router.push(`/practice/ai/runs/${run.runId}`) });
  if (query.isPending) return <StatePanel state="loading" />;
  if (query.isError) return <ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={() => query.refetch()} />;
  const item = query.data;
  const error = save.error ?? status.error ?? review.error;
  return <div className="mx-auto max-w-3xl pb-8">
    <header><Link href="/review" className="text-sm font-bold text-blue-700">← 오답노트</Link><h1 className="mt-2 text-2xl font-black text-slate-950">AI 문제 오답 복습</h1>
      <p className="mt-2 text-sm text-slate-600">AI 생성 문제 · 검수 전 연습 기록 · 오답 {item.wrongCount}회</p></header>
    <article className="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <h2 className="sr-only">문제 내용</h2><QuestionContent body={item.prompt} />
      <ol className="mt-5 grid gap-2">{item.choices.map((choice, index) => <li key={index} className={`rounded-xl border p-3 text-sm ${index + 1 === item.correctAnswer ? "border-emerald-300 bg-emerald-50 text-emerald-900" : "border-slate-200 text-slate-700"}`}><div className="flex min-w-0 items-start gap-2"><span className="shrink-0 font-semibold">{index + 1}.</span><div className="min-w-0 flex-1"><QuestionContent body={choice} compact /></div></div></li>)}</ol>
      <section className="mt-5 rounded-2xl bg-slate-50 p-5"><h3 className="font-extrabold text-slate-900">해설</h3><div className="mt-3"><QuestionContent body={item.explanation} compact /></div></section>
      <label className="mt-6 block text-sm font-extrabold text-slate-800" htmlFor="ai-review-note">나만의 복습 메모</label>
      <textarea key={`${jobId}-${questionNo}-${item.note}`} ref={noteRef} id="ai-review-note" maxLength={10000} defaultValue={item.note} className="mt-2 min-h-28 w-full resize-y rounded-2xl border border-slate-200 p-4 text-sm outline-none focus:border-blue-500" placeholder="틀린 이유와 다시 볼 개념을 적어두세요." />
      <div className="mt-3 flex flex-wrap gap-2"><Button variant="outline" disabled={save.isPending} onClick={() => save.mutate()}>메모 저장</Button>
        <Button variant="outline" disabled={status.isPending} onClick={() => status.mutate()}>{item.status === "OPEN" ? "해결 완료로 표시" : "다시 복습으로 표시"}</Button>
        {item.status === "OPEN" && <Button disabled={review.isPending} onClick={() => review.mutate()}>오답만 다시 풀기</Button>}</div>
      {error && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(error)} onRefresh={() => { save.reset(); status.reset(); review.reset(); void query.refetch(); }} /></div>}
    </article>
  </div>;
}
