"use client";
import { QuestionContent } from "@/components/question-content";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getAiRun, submitAiRunAnswer } from "./practice-api";
import type { AiPracticeRun } from "./practice-contract";
import { AiQuestionReport } from "./ai-question-report";

const title = { STUDY: "AI 문제 다시 학습", EXAM: "AI 개인 연습 시험", REVIEW: "AI 오답 다시 풀기" } as const;

export function AiRunScreen({ runId }: { runId: string }) {
  const client = useQueryClient();
  const key = ["ai-practice-run", runId] as const;
  const query = useQuery({ queryKey: key, queryFn: () => getAiRun(runId) });
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [answer, setAnswer] = useState<number | null>(null);
  const submit = useMutation({
    mutationFn: ({ number, choice }: { number: number; choice: number }) => submitAiRunAnswer(runId, number, choice),
    onSuccess: (updated, variables) => {
      client.setQueryData<AiPracticeRun>(key, updated);
      setSelectedIndex(updated.questions.findIndex(question => question.number === variables.number));
    },
  });

  if (query.isPending) return <StatePanel state="loading" />;
  if (query.isError) return <ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={() => query.refetch()} />;

  const run = query.data;
  const firstUnanswered = run.questions.findIndex(question => question.submittedAnswer === null);
  const index = selectedIndex ?? (firstUnanswered < 0 ? 0 : firstUnanswered);
  const question = run.questions[index];
  const submitted = question.submittedAnswer !== null;
  const showFeedback = submitted && (run.mode !== "EXAM" || run.status === "COMPLETED");
  const returnHref = run.mode === "EXAM" ? "/tests" : run.mode === "REVIEW" ? "/review" : "/practice";
  const move = (next: number) => { setSelectedIndex(next); setAnswer(null); submit.reset(); };

  return <div className="mx-auto max-w-3xl pb-8">
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="text-sm font-extrabold text-blue-700">개인 AI 문제 · 공식 시험 점수와 별도</p>
        <h1 className="mt-1 text-2xl font-black text-slate-950">{title[run.mode]}</h1>
        <p className="mt-2 text-sm text-slate-600">제출 {run.answeredCount} / {run.totalCount}문제</p></div>
      <Link href={returnHref} className="inline-flex min-h-11 items-center rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700">목록으로 돌아가기</Link>
    </header>
    {run.status === "COMPLETED" && <section className="mt-5 rounded-2xl bg-emerald-50 p-5" aria-live="polite">
      <h2 className="font-extrabold text-emerald-900">{run.mode === "EXAM" ? "연습 시험 결과" : "이번 풀이 완료"}</h2>
      <p className="mt-1 text-sm font-bold text-emerald-800">AI 채점: 정답 {run.correctCount} / {run.scoredCount}문제</p>
      {run.scoredCount < run.totalCount && <p className="mt-2 text-sm text-amber-900">오류로 표시한 {run.totalCount - run.scoredCount}문항은 점수에서 제외했어요.</p>}
      <p className="mt-2 text-sm text-emerald-800">틀린 문제는 AI 오답노트에서 다시 볼 수 있어요.</p>
    </section>}
    <article className="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <p className="text-sm font-extrabold text-blue-700">문제 {index + 1} / {run.totalCount}{question.difficulty ? ` · 난이도 ${question.difficulty}/5` : ""}</p>
      <div className="mt-5"><h2 className="sr-only">문제 내용</h2><QuestionContent body={question.prompt} /></div>
      <fieldset className="mt-7 grid min-w-0 gap-3" disabled={submitted || submit.isPending}>
        <legend className="sr-only">답 선택</legend>
        {question.choices.map((choice, choiceIndex) => <label key={choiceIndex} className={`flex min-h-14 cursor-pointer items-start gap-3 rounded-2xl border px-4 py-4 text-sm font-normal focus-within:ring-2 focus-within:ring-blue-500 ${((submitted ? question.submittedAnswer : answer) === choiceIndex + 1) ? "border-blue-500 bg-blue-50 text-blue-950" : "border-slate-200 text-slate-800"}`}>
          <input className="mt-1.5 size-4 shrink-0" type="radio" name={`run-${runId}-question-${question.number}`} checked={(submitted ? question.submittedAnswer : answer) === choiceIndex + 1} onChange={() => setAnswer(choiceIndex + 1)} />
          <div className="flex min-w-0 flex-1 items-start gap-2"><span className="shrink-0 pt-0.5 font-semibold">{choiceIndex + 1}.</span><div className="min-w-0 flex-1"><QuestionContent body={choice} compact /></div></div>
        </label>)}
      </fieldset>
      {!submitted && <Button className="mt-6" disabled={answer === null || submit.isPending} onClick={() => submit.mutate({ number: question.number, choice: answer! })}>{submit.isPending ? "제출 중…" : "답안 제출"}</Button>}
      {run.mode === "EXAM" && submitted && run.status !== "COMPLETED" && <p className="mt-5 text-sm font-bold text-blue-700">답안을 저장했어요. 모든 문제를 제출하면 점수와 해설이 공개됩니다.</p>}
      {showFeedback && <section className="mt-6 rounded-2xl bg-slate-50 p-5" aria-live="polite">
        {question.reported ? <p className="font-extrabold text-amber-900">이 문항은 오류로 표시됐어요.</p> : <>
          <h3 className={`font-extrabold ${question.correct ? "text-emerald-700" : "text-rose-700"}`}>{question.correct ? "AI 채점: 정답" : "AI 채점: 오답"}</h3>
          <p className="mt-2 text-sm font-bold text-slate-800">AI가 제시한 정답: {question.correctAnswer}번</p>
          <div className="mt-2"><QuestionContent body={question.choices[(question.correctAnswer ?? 1) - 1]} compact /></div>
          <div className="mt-4 border-t border-slate-200 pt-4"><QuestionContent body={question.explanation ?? ""} compact /></div>
          {question.sourceExcerpt && <blockquote className="mt-4 border-l-4 border-blue-300 pl-3 text-sm text-slate-700">학습 본문에서 가져온 문장: {question.sourceExcerpt}</blockquote>}
        </>}
        <AiQuestionReport key={question.number} jobId={run.jobId} questionNo={question.number} reported={Boolean(question.reported)} />
      </section>}
      {submit.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(submit.error)} onRefresh={() => submit.reset()} /></div>}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button variant="outline" disabled={index === 0} onClick={() => move(index - 1)}>이전 문제</Button>
        {index < run.questions.length - 1 ? <Button disabled={!submitted} onClick={() => move(index + 1)}>다음 문제</Button> : run.status === "COMPLETED" && <Link href={returnHref} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-5 text-sm font-extrabold text-white">목록으로 돌아가기 →</Link>}
      </div>
    </article>
  </div>;
}
