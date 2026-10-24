"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { HelpHint } from "@/components/ui/help-hint";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getPracticeJob, requestPracticeQuestions } from "./practice-api";
import styles from "./ai-practice-panel.module.css";

const difficultyNames: Record<number, string> = { 3: "적용 중심", 4: "진단 중심", 5: "설계 중심" };

export function AiPracticePanel({ contentId, basicPracticeHref, basicPracticeLabel = "기본 확인 문제 풀기" }: {
  contentId: string; basicPracticeHref?: string; basicPracticeLabel?: string;
}) {
  const client = useQueryClient();
  const [selectedDifficulty, setSelectedDifficulty] = useState<number | null>(null);
  const key = ["ai-practice-job", contentId, selectedDifficulty] as const;
  const job = useQuery({
    queryKey: key, queryFn: () => getPracticeJob(contentId, selectedDifficulty),
    refetchInterval: query => ["READY", "PROCESSING"].includes(query.state.data?.status ?? "") ? 2000 : false,
  });
  const create = useMutation({
    mutationFn: () => requestPracticeQuestions(contentId, selectedDifficulty ?? job.data?.targetDifficulty ?? 4),
    onSuccess: () => { void client.invalidateQueries({ queryKey: ["ai-practice-job", contentId] }); },
  });
  const status = job.data?.status;
  const count = job.data?.questionCount;
  const levels = job.data?.difficultyLevels;
  const difficulty = selectedDifficulty ?? job.data?.targetDifficulty ?? 4;
  const available = job.data?.generationAvailable ?? false;
  const anyDifficultyAvailable = (job.data?.availableDifficulties.length ?? 0) > 0;
  const alternativeDifficulty = job.data?.availableDifficulties.find(level => level !== difficulty);
  const savedCount = status === "SUCCESS" ? job.data?.latestQuestionCount : null;
  const displayCount = available ? savedCount ?? count : savedCount;
  const showUnavailable = job.data && !available && status !== "SUCCESS" && status !== "READY" && status !== "PROCESSING";

  return <section className="mt-7 rounded-2xl border border-blue-200 bg-blue-50/60 p-5">
    <h3 className="font-extrabold text-slate-950">AI 확인 문제{displayCount ? ` ${displayCount}개` : ""} <span className="ml-1 text-xs font-semibold text-slate-500">선택 학습</span></h3>
    {(!job.data || anyDifficultyAvailable) && <p className="mt-2 text-sm leading-6 text-slate-600">학습 내용을 바탕으로 요청할 때 생성해요. 제출 기록은 저장되며 공식 시험 점수와 구분됩니다. 생성한 문제는 검수 전 연습 문제예요.</p>}
    {(!job.data || anyDifficultyAvailable) && <>
    <div className="mt-4 flex flex-wrap items-center gap-2"><label htmlFor={`ai-difficulty-${contentId}`} className="text-sm font-bold text-slate-800">출제 난이도</label><HelpHint inline label="출제 난이도" summary="어떤 방식으로 생각할 문제를 만들지 선택해요." items={[
      { term: "적용 · 3/5", description: "배운 개념을 주어진 상황에 적용해요." },
      { term: "진단 · 4/5", description: "문제의 원인과 해결 방법을 찾아요." },
      { term: "설계 · 5/5", description: "여러 조건을 비교해 설계 방향을 골라요." },
    ]} note="같은 학습 시간에는 난이도가 높을수록 생성 문항 수가 줄어요. 이 값은 AI 출제 목표이며, 내 실력을 측정한 결과가 아니에요." /></div>
    <select id={`ai-difficulty-${contentId}`} className="mt-2 min-h-11 w-full rounded-xl border border-blue-200 bg-white px-3 text-sm font-semibold text-slate-900 sm:w-auto"
      value={difficulty} onChange={event => setSelectedDifficulty(Number(event.target.value))}>
      <option value={3}>적용 중심 · 3/5</option>
      <option value={4}>진단 중심 · 4/5</option>
      <option value={5}>설계 중심 · 5/5</option>
    </select>
    <p className="mt-2 text-xs text-slate-600">같은 학습 시간이라도 어려운 문제를 선택하면 문항 수가 줄어듭니다.</p>
    {job.data && levels && <p className="mt-2 text-sm font-bold text-blue-800">학습 세션 {job.data.estimatedMinutes}분 · 목표 {count}문제 · 난이도 {Math.min(...levels)}~{Math.max(...levels)}/5</p>}
    </>}
    {job.isPending && <p className="mt-4 text-sm text-slate-600">기존 문제 묶음을 확인하는 중…</p>}
    {job.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(job.error)} onRefresh={() => job.refetch()} /></div>}
    {showUnavailable && <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
      <p className="text-sm font-extrabold text-amber-900">{anyDifficultyAvailable ? `이 본문으로는 ${difficultyNames[difficulty]} ${count}문제를 만들기 어려워요.` : "이 학습 자료는 AI 문제를 만들 만큼 준비되지 않았어요."}</p>
      <p className="mt-1 text-sm leading-6 text-amber-800">{anyDifficultyAvailable ? "문항 수가 적은 난이도로 바꾸거나 기본 확인 문제를 풀 수 있어요." : "학습 설명을 보강해야 합니다. 이미 배정된 기본 확인 문제와 이전 학습 기록은 계속 볼 수 있어요."}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {alternativeDifficulty && <button type="button" onClick={() => setSelectedDifficulty(alternativeDifficulty)} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-4 text-sm font-extrabold text-white hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">{difficultyNames[alternativeDifficulty]} · {alternativeDifficulty}/5로 바꾸기</button>}
        {basicPracticeHref && <Link href={basicPracticeHref} className={`inline-flex min-h-11 items-center rounded-xl px-4 text-sm font-extrabold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${alternativeDifficulty ? "border border-blue-200 text-blue-700" : "bg-blue-600 text-white hover:bg-blue-700"}`}>{basicPracticeLabel} →</Link>}
        {status === "FAILED" && <Link href="/practice" className="inline-flex min-h-11 items-center rounded-xl px-2 text-sm font-bold text-blue-700 underline">저장된 AI 문제 보기</Link>}
      </div>
      {status === "FAILED" && job.data.qualityRejected && <p className="mt-3 text-xs leading-5 text-amber-800">이전 AI 생성 결과는 검사를 통과하지 못해 저장되지 않았어요. 해당 요청의 AI 사용 비용은 이미 발생했을 수 있습니다.</p>}
    </div>}
    {(status === "NONE" || status === "FAILED") && available && <Button className="mt-4" disabled={create.isPending || job.isPending} onClick={() => create.mutate()}>
      {create.isPending ? "생성 요청 중…" : status === "FAILED" ? "다시 생성 요청" : `AI 문제 ${count ?? ""}개 만들기`}
    </Button>}
    {status === "FAILED" && available && job.data?.qualityRejected && <p className="mt-3 text-sm font-bold text-amber-800">이전 생성 결과가 문제 검사를 통과하지 못해 저장되지 않았어요. 해당 요청의 AI 사용 비용은 이미 발생했을 수 있습니다.</p>}
    {status === "FAILED" && available && <Link href="/practice" className="ml-3 inline-block text-sm font-bold text-blue-700 underline">기존 저장 문제 보기</Link>}
    {(create.isPending || status === "READY" || status === "PROCESSING") && <div role="status" aria-live="polite" aria-atomic="true" className="mt-5 text-blue-700">
      <div className="flex items-center gap-3">
        <span className={styles.emblem} aria-hidden="true"><span className={styles.orbit}/><Sparkles size={19}/></span>
        <div className="min-w-0">
          <p className="text-sm font-extrabold leading-6">{create.isPending ? "생성 요청을 보내고 있어요" : "문제를 생성하는 중이에요"}<span className={styles.dots} aria-hidden="true"><span/><span/><span/></span></p>
          <p className="mt-1 text-xs leading-5 text-slate-600">완료되면 이곳에서 바로 이어 풀 수 있어요.</p>
        </div>
      </div>
      <div className={styles.track} aria-hidden="true"><span className={styles.sweep}/></div>
    </div>}
    {status === "SUCCESS" && job.data?.jobId && <div className="mt-4 flex flex-wrap gap-2">
      {job.data.rejectedQuestionCount > 0 && <p className="w-full text-sm font-bold text-amber-800">요청한 {count}개 중 검사를 통과한 {savedCount}개를 저장했어요. 추가 생성 요청 없이 바로 풀 수 있습니다.</p>}
      <Link href={`/practice/ai/${job.data.jobId}`} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-5 text-sm font-extrabold text-white hover:bg-blue-700">저장된 {job.data.latestQuestionCount}문제 이어 풀기 →</Link>
      <Link href="/practice" className="inline-flex min-h-11 items-center rounded-xl border border-blue-200 px-4 text-sm font-bold text-blue-700">다시 학습하기</Link>
      <Link href="/tests" className="inline-flex min-h-11 items-center rounded-xl border border-blue-200 px-4 text-sm font-bold text-blue-700">연습 시험 보기</Link>
      {available && <Button variant="outline" disabled={create.isPending || job.isPending} onClick={() => create.mutate()}>{create.isPending ? "생성 요청 중…" : `새 ${count}문제 만들기`}</Button>}
    </div>}
    {status === "UNKNOWN" && <p className="mt-4 text-sm font-bold text-amber-800">공급자 응답 결과를 확인할 수 없습니다. 중복 생성 요청을 막기 위해 자동으로 다시 요청하지 않아요.</p>}
    {create.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(create.error)} onRefresh={() => create.reset()} /><Link href="/settings/integrations" className="mt-3 inline-block text-sm font-bold text-blue-700 underline">AI 연결 설정 확인</Link></div>}
  </section>;
}
