"use client";
import { QuestionContent } from "@/components/question-content";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { getPracticeSet, submitPracticeAnswer } from "./practice-api";
import type { AiPracticeSet } from "./practice-contract";
import { AiQuestionReport } from "./ai-question-report";

export function AiPracticeScreen({ jobId }: { jobId: string }) {
  const client = useQueryClient();
  const key = ["ai-practice-set", jobId] as const;
  const set = useQuery({ queryKey: key, queryFn: () => getPracticeSet(jobId) });
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [answer, setAnswer] = useState<number | null>(null);
  const submit = useMutation({
    mutationFn: (questionNo: number) => submitPracticeAnswer(jobId, questionNo, answer!),
    onSuccess: updated => {
      setSelectedIndex(updated.number - 1);
      client.setQueryData<AiPracticeSet>(key, current => current && ({
        ...current, questions: current.questions.map(question => question.number === updated.number ? updated : question),
      }));
    },
  });

  if (set.isPending) return <StatePanel state="loading" />;
  if (set.isError) return <ApiErrorFeedback viewModel={toApiErrorViewModel(set.error)} onRefresh={() => set.refetch()} />;

  const questions = set.data.questions;
  const firstUnanswered = questions.findIndex(question => question.submittedAnswer === null);
  const index = selectedIndex ?? (firstUnanswered < 0 ? questions.length - 1 : firstUnanswered);
  const question = questions[index];
  const submitted = question.submittedAnswer !== null;
  const completed = questions.filter(item => item.submittedAnswer !== null).length;
  const sessionHref = `/workspace/learning/${set.data.sessionId}`;
  const move = (next: number) => { setSelectedIndex(next); setAnswer(null); submit.reset(); };

  return <div className="mx-auto max-w-3xl pb-8">
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="text-sm font-extrabold text-blue-700">AI 연습 · 검수 전 문제</p><h1 className="mt-1 text-2xl font-black text-slate-950">학습 확인 문제</h1><p className="mt-2 text-sm text-slate-600">제출 {completed} / {questions.length}문제 · 풀이 기록은 저장됩니다.</p></div>
      <Link href={sessionHref} className="inline-flex min-h-11 items-center rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700">학습으로 돌아가기</Link>
    </header>
    <div className="mt-5 h-2 overflow-hidden rounded-full bg-slate-200"><div className="h-full bg-blue-600" style={{ width: `${completed / questions.length * 100}%` }} /></div>
    <article className="mt-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
      <p className="text-sm font-extrabold text-blue-700">문제 {index + 1} / {questions.length}{question.difficulty ? ` · 난이도 ${question.difficulty}/5` : ""}</p>
      <div className="mt-5"><h2 className="sr-only">문제 내용</h2><QuestionContent body={question.prompt} /></div>
      <fieldset className="mt-7 grid min-w-0 gap-3" disabled={submitted || submit.isPending}>
        <legend className="sr-only">답 선택</legend>
        {question.choices.map((choice, choiceIndex) => <label key={choiceIndex} className={`flex min-h-14 cursor-pointer items-start gap-3 rounded-2xl border px-4 py-4 text-sm font-normal focus-within:ring-2 focus-within:ring-blue-500 ${((submitted ? question.submittedAnswer : answer) === choiceIndex + 1) ? "border-blue-500 bg-blue-50 text-blue-950 dark:text-blue-100" : "border-slate-200 text-slate-800"}`}>
          <input className="mt-1.5 size-4 shrink-0" type="radio" name={`question-${question.number}`} checked={(submitted ? question.submittedAnswer : answer) === choiceIndex + 1} onChange={() => setAnswer(choiceIndex + 1)} />
          <div className="flex min-w-0 flex-1 items-start gap-2"><span className="shrink-0 pt-0.5 font-semibold">{choiceIndex + 1}.</span><div className="min-w-0 flex-1"><QuestionContent body={choice} compact /></div></div>
        </label>)}
      </fieldset>
      {!submitted && <Button className="mt-6" disabled={answer === null || submit.isPending} onClick={() => submit.mutate(question.number)}>{submit.isPending ? "채점 중…" : "답안 제출"}</Button>}
      {submit.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(submit.error)} onRefresh={() => submit.reset()} /></div>}
      {submitted && <section aria-live="polite" className="mt-6 rounded-2xl bg-slate-50 p-5">
        {question.reported ? <p className="font-extrabold text-amber-900">이 문항은 오류로 표시됐어요.</p> : <>
          <h3 className={`font-extrabold ${question.correct ? "text-emerald-700" : "text-rose-700"}`}>{question.correct ? "AI 채점: 정답" : "AI 채점: 오답"}</h3>
          <p className="mt-2 text-sm font-bold text-slate-800">AI가 제시한 정답: {question.correctAnswer}번</p>
          <div className="mt-2"><QuestionContent body={question.choices[(question.correctAnswer ?? 1) - 1]} compact /></div>
          <div className="mt-4 border-t border-slate-200 pt-4"><QuestionContent body={question.explanation ?? ""} compact /></div>
          {question.sourceExcerpt && <blockquote className="mt-4 border-l-4 border-blue-300 pl-3 text-sm text-slate-700">학습 본문에서 가져온 문장: {question.sourceExcerpt}</blockquote>}
        </>}
        <AiQuestionReport key={question.number} jobId={jobId} questionNo={question.number} reported={Boolean(question.reported)} />
      </section>}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button variant="outline" disabled={index === 0} onClick={() => move(index - 1)}>이전 문제</Button>
        {index < questions.length - 1 ? <Button disabled={!submitted} onClick={() => move(index + 1)}>다음 문제</Button> : submitted && <Link href={sessionHref} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-5 text-sm font-extrabold text-white">학습으로 돌아가기 →</Link>}
      </div>
      {completed === questions.length && <p className="mt-5 text-sm font-extrabold text-emerald-700">AI 연습 문제 {questions.length}개를 모두 제출했어요.</p>}
    </article>
  </div>;
}
