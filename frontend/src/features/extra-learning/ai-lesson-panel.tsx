"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { Sparkles } from "lucide-react";
import { createMutationAction } from "@/api/client";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { categoryLabels, startExtra, type StartAction } from "./api";
import { createLesson, getLessonJobs, type LessonAction, type LessonInput, type LessonJob } from "./ai-lesson-api";

export type LessonSeed = LessonInput & { key: number; baseTitle?: string };
const isPending = (job: LessonJob) => job.status === "QUEUED" || job.status === "RUNNING";

export function AiLessonPanel({ seed, onClose }: { seed: LessonSeed | null; onClose: () => void }) {
  const client = useQueryClient();
  const router = useRouter();
  const startAction = useRef<StartAction | null>(null);
  const jobs = useQuery({ queryKey: ["ai-lesson-jobs"], queryFn: getLessonJobs,
    refetchInterval: query => query.state.data?.some(isPending) ? 2500 : false });
  const completed = jobs.data?.filter(job => job.status === "SUCCEEDED").map(job => job.jobId).join(",") ?? "";
  useEffect(() => { if (completed) void client.invalidateQueries({ queryKey: ["extra-topics"] }); }, [client, completed]);
  const start = useMutation({ mutationFn: (job: LessonJob) => {
    if (!job.contentVersionId || job.status !== "SUCCEEDED") throw new Error("자료가 아직 준비되지 않았어요.");
    const prior = startAction.current?.getPayload();
    if (prior?.contentVersionId !== job.contentVersionId || prior?.targetMinutes !== job.targetMinutes)
      startAction.current = createMutationAction({ mode: "EXPLORE" as const, contentVersionId: job.contentVersionId, sourceSessionId: null, targetMinutes: job.targetMinutes });
    return startExtra(startAction.current!);
  }, onSuccess: async entry => {
    startAction.current = null;
    await client.invalidateQueries({ queryKey: ["extra-learning"] });
    router.push(`/workspace/learning/${entry.sessionId}`);
  } });
  if (!seed && !jobs.data?.length && !jobs.isError) return null;
  return <section aria-label="내 AI 학습자료" className="rounded-3xl border border-blue-200 bg-white p-5 sm:p-7">
    <h2 className="flex items-center gap-2 text-lg font-black text-slate-950"><Sparkles size={20} className="shrink-0 text-blue-600"/>내 AI 학습자료</h2>
    <p className="mt-2 text-sm leading-6 text-slate-600">원하는 주제로 새 본문·사례·실습을 만들어요. 저장한 자료는 기존 학습 화면에서 공부하고 AI 확인 문제도 만들 수 있어요.</p>
    {seed && <LessonForm key={seed.key} seed={seed} onClose={onClose}/>} 
    {jobs.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(jobs.error)} onRefresh={() => void jobs.refetch()}/></div>}
    {!!jobs.data?.length && <ul className="mt-5 grid gap-3">{jobs.data.slice(0, 5).map(job => <li key={job.jobId} className="min-w-0 rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <p className="text-xs font-bold text-blue-700">{categoryLabels[job.subject] ?? job.subject} · {job.stage} · {job.targetMinutes}분 · AI 생성</p>
      <h3 className="mt-2 break-words font-extrabold text-slate-950">{job.title ?? job.topic}</h3>
      {isPending(job) && <p role="status" className="mt-2 text-sm text-blue-700">{job.status === "QUEUED" ? "생성을 기다리고 있어요." : "공식 자료를 확인하며 본문과 실습을 만들고 있어요."} 다른 화면에 다녀와도 여기서 이어 확인할 수 있어요.</p>}
      {job.status === "SUCCEEDED" && job.contentVersionId && <><p className="mt-2 text-xs leading-5 text-slate-600">저장 완료 · 자동 구조·출처 검사를 통과한 AI 자료예요. 내용의 오류 가능성은 남아 있어요.</p><Button className="mt-3" disabled={start.isPending} onClick={() => start.mutate(job)}>이 자료로 학습 시작</Button></>}
      {job.status === "FAILED" && <p className="mt-2 text-sm leading-6 text-amber-800">{job.errorCode === "AI_LEARNING_QUALITY_REJECTED" ? "자료의 분량·구조·출처 또는 문제 근거를 확인하지 못해 학습자료로 저장하지 않았어요. 주제나 조건을 조정해 다시 요청할 수 있어요." : job.errorCode === "AI_PROVIDER_QUOTA_EXHAUSTED" ? "OpenAI API 잔액을 확인한 뒤 다시 요청해 주세요." : "자료 생성을 완료하지 못했어요. AI 연결 상태를 확인한 뒤 다시 요청할 수 있어요."} 생성 요청 비용은 발생했을 수 있어요.</p>}
      {job.status === "UNKNOWN" && <p className="mt-2 text-sm leading-6 text-amber-800">생성 응답을 확인하지 못했어요. 중복 비용을 막기 위해 자동 재요청하지 않습니다.</p>}
      {job.status === "CANCELED" && <p className="mt-2 text-sm text-slate-600">취소된 생성 요청이에요.</p>}
    </li>)}</ul>}
    {(jobs.data?.length ?? 0) > 5 && <p className="mt-3 text-xs text-slate-500">최근 생성 5건을 표시해요. 저장된 모든 자료는 위의 주제 검색에서 다시 찾을 수 있어요.</p>}
    {start.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(start.error)} onRefresh={() => start.reset()}/></div>}
  </section>;
}

function LessonForm({ seed, onClose }: { seed: LessonSeed; onClose: () => void }) {
  const client = useQueryClient();
  const [topic, setTopic] = useState(seed.topic);
  const [subject, setSubject] = useState(seed.subject);
  const [stage, setStage] = useState(seed.stage);
  const [minutes, setMinutes] = useState(seed.targetMinutes);
  const action = useRef<LessonAction | null>(null);
  const create = useMutation({ mutationFn: () => {
    action.current ??= createMutationAction({ topic: topic.trim(), subject, stage, targetMinutes: minutes, baseContentVersionId: seed.baseContentVersionId });
    return createLesson(action.current);
  }, onSuccess: async () => { action.current = null; await client.invalidateQueries({ queryKey: ["ai-lesson-jobs"] }); onClose(); } });
  const change = () => { action.current = null; create.reset(); };
  const control = "min-h-12 w-full min-w-0 rounded-xl border border-slate-300 bg-white px-3 text-slate-950";
  return <form className="mt-5 grid gap-4" onSubmit={event => { event.preventDefault(); if (topic.trim().length >= 2 && !create.isPending) create.mutate(); }}>
    {seed.baseTitle && <p className="rounded-xl bg-blue-50 p-3 text-sm leading-6 text-blue-800">심화할 기존 주제: {seed.baseTitle}</p>}
    <label className="grid gap-2 text-sm font-bold text-slate-700">새로 공부할 내용<textarea autoFocus value={topic} maxLength={300} minLength={2} required disabled={create.isPending} onChange={event => { setTopic(event.target.value); change(); }} placeholder="예: 동시 송금 요청에서 멱등성과 잠금 방식을 비교하고 싶어" className={`${control} min-h-24 py-3`}/></label>
    <div className="grid gap-3 sm:grid-cols-3">
      <label className="grid gap-2 text-sm font-bold text-slate-700">생성할 분야<select value={subject} disabled={create.isPending || !!seed.baseContentVersionId} onChange={event => { setSubject(event.target.value); change(); }} className={control}>{Object.entries(categoryLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
      <label className="grid gap-2 text-sm font-bold text-slate-700">생성할 학습 단계<select value={stage} disabled={create.isPending} onChange={event => { setStage(event.target.value); change(); }} className={control}>{["기초", "실무", "심화"].map(level => <option key={level}>{level}</option>)}</select></label>
      <label className="grid gap-2 text-sm font-bold text-slate-700">생성 학습 시간<select value={minutes} disabled={create.isPending} onChange={event => { setMinutes(Number(event.target.value)); change(); }} className={control}>{[30, 60, 90, 120].map(value => <option key={value} value={value}>{value}분</option>)}</select></label>
    </div>
    <p className="text-xs leading-5 text-slate-500">생성 버튼을 누를 때 OpenAI와 공식 자료 검색을 사용해요. 같은 조건으로 이미 준비된 내 자료가 있으면 재사용해요.</p>
    <div className="flex flex-wrap gap-3"><Button type="submit" disabled={create.isPending || topic.trim().length < 2}>{create.isPending ? "생성 요청 중…" : "학습자료 생성"}</Button><Button type="button" variant="outline" disabled={create.isPending} onClick={onClose}>닫기</Button></div>
    {create.isError && <ApiErrorFeedback viewModel={toApiErrorViewModel(create.error)} onRefresh={() => create.reset()}/>}
  </form>;
}
