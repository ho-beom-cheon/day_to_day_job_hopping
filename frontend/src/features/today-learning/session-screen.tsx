"use client";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, CheckCircle2, Clock3, ListChecks, Play, Target } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import { getExtraDetail } from "@/features/extra-learning/api";
import { SessionExtraTools } from "@/features/extra-learning/session-extra-tools";
import { ExtraLearningEntry } from "@/features/extra-learning/extra-learning-screen";
import { ApiError } from "@/api/client";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { LearningMarkdown } from "@/components/learning-markdown";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { AiPracticePanel } from "@/features/ai/ai-practice-panel";
import { getPracticeJob, getPracticeSet } from "@/features/ai/practice-api";
import { getProblem } from "@/features/problems/api";
import type { LearningContentDto } from "./contract";
import { completeLearningContent, completeLearningSession, createSessionCompletionAction, dashboardQuery, learningContentQuery, learningSessionQuery, sessionContentsQuery, startLearningSession, todayLearningKeys, type CompletionAction } from "./queries";

export function LearningSessionScreen({ sessionId }: { sessionId: string }) {
  const client = useQueryClient();
  const dashboard = useQuery(dashboardQuery);
  const extra = useQuery({ queryKey: ["extra-learning", sessionId], queryFn: () => getExtraDetail(sessionId) });
  const sessionQuery = useQuery(learningSessionQuery(sessionId));
  const contentsQuery = useQuery(sessionContentsQuery(sessionId));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const completionAction = useRef<CompletionAction | null>(null);
  const selectedContentId = selectedId ?? contentsQuery.data?.[0]?.contentId ?? null;
  const contentQuery = useQuery({ ...learningContentQuery(selectedContentId ?? "0"), enabled: selectedContentId !== null });

  const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: ["extra-learning"] }), client.invalidateQueries({ queryKey: ["progress"] }),client.invalidateQueries({ queryKey: todayLearningKeys.dashboard() }), client.invalidateQueries({ queryKey: todayLearningKeys.session(sessionId) }), client.invalidateQueries({ queryKey: todayLearningKeys.contents(sessionId) })]); if (selectedContentId) await client.invalidateQueries({ queryKey: todayLearningKeys.content(selectedContentId) }); };
  const startMutation = useMutation({ mutationFn: () => startLearningSession(sessionId), onSuccess: refresh });
  const contentMutation = useMutation({ mutationFn: (contentId: string) => completeLearningContent(contentId), onSuccess: refresh });
  const sessionMutation = useMutation({ mutationFn: () => { completionAction.current ??= createSessionCompletionAction(null); return completeLearningSession(sessionId, completionAction.current); }, onSuccess: async () => { completionAction.current = null; await refresh(); } });
  const finishMutation = useMutation({
    mutationFn: async () => {
      if (sessionQuery.data?.status === "NOT_STARTED") await startLearningSession(sessionId);
      const onlyContent = contentsQuery.data?.[0];
      if (!onlyContent) throw new Error("학습 콘텐츠가 없습니다.");
      if (onlyContent.status !== "COMPLETED") await completeLearningContent(onlyContent.contentId);
      completionAction.current ??= createSessionCompletionAction(null);
      return completeLearningSession(sessionId, completionAction.current);
    },
    onSuccess: async () => { completionAction.current = null; await refresh(); },
  });

  if (dashboard.isPending || sessionQuery.isPending || contentsQuery.isPending) return <StatePanel state="loading" />;
  if (dashboard.isError || sessionQuery.isError || contentsQuery.isError) return <StatePanel state="error" onRefresh={() => { void dashboard.refetch(); void sessionQuery.refetch(); void contentsQuery.refetch(); }} />;
  const session = sessionQuery.data;
  const contents = contentsQuery.data;
  const historical = session.curriculumId !== dashboard.data.curriculum?.curriculumId;
  const currentSessionId = dashboard.data.today?.sessions.find(item => item.status !== "COMPLETED")?.sessionId;
  const allRequiredComplete = contents.filter(item => item.required).every(item => item.status === "COMPLETED");
  const selfPractice = contents.length === 1 && contents[0].type === "PRACTICE" && contents[0].completionRule === "READ_ACK";
  const mutationError = startMutation.error ?? contentMutation.error ?? sessionMutation.error ?? finishMutation.error;

  return <div className={`mx-auto grid w-full min-w-0 grid-cols-1 gap-6 ${contents.length === 1 ? "max-w-6xl" : ""}`}>
    <header><Link href="/learning" className="inline-flex min-h-11 items-center gap-2 rounded-xl pr-3 text-sm font-bold text-slate-600 hover:text-blue-700 focus-visible:outline-2 focus-visible:outline-blue-600"><ArrowLeft size={18}/>학습 목록으로 돌아가기</Link><div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-bold text-blue-700">{session.category} · {session.scheduledDate}</p><h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-950 sm:text-3xl">{session.title}</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">{historical ? "이전 과정의 학습 기록입니다. 본문만 확인할 수 있어요." : extra.data ? "선택한 예상 소요 시간에 맞춰 만든 추가 학습 세션이에요. 콘텐츠와 풀이 기록은 저장됩니다." : session.description}</p></div><span className={`inline-flex w-fit items-center gap-2 rounded-full px-3 py-2 text-xs font-bold ${session.status === "COMPLETED" && !historical ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"}`}>{historical ? <><Clock3 size={15}/> 이전 기록</> : session.status === "COMPLETED" ? <><CheckCircle2 size={15}/> 학습 완료</> : selfPractice ? <><Clock3 size={15}/> 학습 중</> : <><Clock3 size={15}/> {session.completedRequiredContentCount}/{session.requiredContentCount} 필수 콘텐츠</>}</span></div></header>

    {historical && <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5"><h2 className="font-extrabold text-amber-900">현재 과정이 아닌 학습 기록이에요</h2><p className="mt-2 text-sm leading-6 text-amber-800">이전 세션의 본문은 볼 수 있지만, 여기서는 문제를 풀거나 학습을 완료할 수 없어요. 현재 과정에서 이어서 학습해 주세요.</p><div className="mt-4 flex flex-wrap gap-2"><Link href={currentSessionId ? `/workspace/learning/${currentSessionId}` : "/learning"} className="inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-4 text-sm font-extrabold text-white hover:bg-blue-700">{currentSessionId ? "현재 세션 열기 →" : "현재 학습으로 이동 →"}</Link><Link href="/practice" className="inline-flex min-h-11 items-center rounded-xl border border-blue-200 px-4 text-sm font-bold text-blue-700">저장된 AI 문제 보기</Link></div></section>}
    {!historical && extra.data && <SessionExtraTools entry={extra.data}/>}
    {mutationError instanceof ApiError && <ApiErrorFeedback viewModel={toApiErrorViewModel(mutationError)} onRefresh={() => { if (finishMutation.isError && mutationError.error?.retryable) finishMutation.mutate(); else if (sessionMutation.isError && mutationError.error?.retryable) sessionMutation.mutate(); else void refresh(); }} />}

    {!historical && session.status === "NOT_STARTED" && !selfPractice ? <section className="rounded-3xl bg-gradient-to-br from-blue-700 to-indigo-600 p-7 text-white shadow-sm sm:p-9"><Play size={24}/><h2 className="mt-5 text-2xl font-extrabold">준비되면 세션을 시작하세요</h2><p className="mt-2 text-sm leading-6 text-blue-100">시작 시각이 저장되고, 완료한 콘텐츠는 다시 들어와도 유지됩니다.</p><Button className="mt-6 bg-white text-blue-700 hover:bg-blue-50" disabled={startMutation.isPending} onClick={() => startMutation.mutate()}>{startMutation.isPending ? "시작하는 중…" : "학습 시작"}</Button></section> : <div className={contents.length > 1 ? "grid min-w-0 gap-5 lg:grid-cols-[280px_minmax(0,1fr)]" : "min-w-0"}>
      {contents.length > 1 && <aside className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm lg:self-start"><h2 className="px-2 py-2 text-sm font-extrabold text-slate-950">학습 콘텐츠</h2><ol className="mt-2 grid gap-1">{contents.map(item => <li key={item.contentId}><button type="button" onClick={() => setSelectedId(item.contentId)} className={`flex min-h-14 w-full items-center gap-3 rounded-2xl px-3 py-2 text-left ${selectedContentId === item.contentId ? "bg-blue-50 text-blue-800" : "text-slate-700 hover:bg-slate-50"}`}><span className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-extrabold ${item.status === "COMPLETED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>{item.status === "COMPLETED" ? <Check size={15}/> : item.sequence}</span><span className="min-w-0"><span className="block truncate text-sm font-bold">{item.title}</span><span className="block text-[11px] opacity-65">{item.required ? "필수" : "선택"} · {item.status === "COMPLETED" ? "완료" : item.completionRule === "READ_ACK" ? "읽음 확인" : "문제 풀이 필요"}</span></span></button></li>)}</ol></aside>}
      <section aria-label="학습 본문" className="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7">{selectedContentId === null ? <StatePanel state="empty" /> : contentQuery.isPending ? <StatePanel state="loading" /> : contentQuery.isError ? <StatePanel state="error" onRefresh={() => void contentQuery.refetch()} /> : <><ContentDetail content={contentQuery.data} historical={historical} disabled={session.status === "COMPLETED" || contentMutation.isPending} showCompletion={!selfPractice} onComplete={() => contentMutation.mutate(contentQuery.data.contentId)} />{selfPractice && !historical && <div className="mt-8 border-t border-slate-200 pt-6">{session.status === "COMPLETED" ? <p className="text-sm font-bold text-emerald-700">학습을 완료했어요.</p> : <><p className="mb-3 text-sm text-slate-600">본문을 읽고 직접 해보기를 마쳤다면 완료해 주세요.</p><Button disabled={finishMutation.isPending} onClick={() => finishMutation.mutate()}>{finishMutation.isPending ? "완료 저장 중…" : "오늘 학습 완료"}</Button></>}</div>}</>}</section>
    </div>}

    {!historical && session.status === "COMPLETED" && <Link href="/tests" className="block rounded-2xl border border-blue-200 bg-blue-50 p-5 font-bold text-blue-800">완료한 학습으로 평가하기 →<span className="mt-2 block text-sm font-normal">정규·추가 학습 목표가 일간·주간·월간 평가에 함께 반영돼요.</span></Link>}
    {!historical && !selfPractice && session.status === "COMPLETED" && <ExtraLearningEntry/>}
    {!historical && !selfPractice && session.status !== "NOT_STARTED" && <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
      <div className="flex items-start gap-3"><span className="grid size-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><ListChecks size={20}/></span><div>
        <h2 className="font-extrabold text-slate-950">세션 마무리</h2>
        <p className="mt-1 text-sm leading-6 text-slate-500">필수 콘텐츠를 모두 마치면 세션을 완료할 수 있어요. 선택한 시간은 예상 소요 시간이며 실제 학습 시간으로 기록하지 않아요.</p>
      </div></div>
      {session.status === "COMPLETED"
        ? <div className="mt-5 rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-800">학습 완료 · 예상 {extra.data?.targetMinutes ?? session.estimatedMinutes}분{session.actualMinutes > 0 ? ` · 과거에 직접 기록한 시간 ${session.actualMinutes}분` : ""}</div>
        : <div className="mt-5 flex flex-wrap items-center gap-3"><p className="text-sm font-bold text-slate-700">예상 {extra.data?.targetMinutes ?? session.estimatedMinutes}분</p><Button disabled={!allRequiredComplete || sessionMutation.isPending} onClick={() => sessionMutation.mutate()}>{sessionMutation.isPending ? "완료 저장 중…" : "세션 완료"}</Button></div>}
      {session.status !== "COMPLETED" && !allRequiredComplete && <p className="mt-3 text-xs font-semibold text-amber-700">필수 콘텐츠를 모두 완료하면 버튼이 활성화됩니다.</p>}
    </section>}
  </div>;
}

function ContentDetail({ content, historical, disabled, showCompletion, onComplete }: { content: LearningContentDto; historical: boolean; disabled: boolean; showCompletion: boolean; onComplete: () => void }) {
  const practice = content.completionRule === "ALL_PROBLEMS_ATTEMPTED";
  const problemQueries = useQueries({ queries: (practice && !historical ? content.problems : []).map(problem => ({ queryKey: ["problems", problem.problemId, content.contentId], queryFn: () => getProblem(problem.problemId, content.contentId) })) });
  const allAttempted = content.problems.length > 0 && problemQueries.every(query => query.data?.answered);
  const nextBasicProblem = content.problems.find((_, index) => !problemQueries[index]?.data?.answered);
  const basicPracticeHref = content.status === "COMPLETED" || content.problems.length === 0 ? undefined : nextBasicProblem
    ? `/practice/${nextBasicProblem.problemId}?contentId=${content.contentId}` : `#content-complete-${content.contentId}`;
  const aiJob = useQuery({ queryKey: ["ai-practice-job", content.contentId], queryFn: () => getPracticeJob(content.contentId), enabled: practice && !historical });
  const aiSet = useQuery({
    queryKey: ["ai-practice-set", aiJob.data?.jobId ?? ""],
    queryFn: () => getPracticeSet(aiJob.data!.jobId!),
    enabled: !historical && aiJob.data?.status === "SUCCESS" && aiJob.data.jobId !== null,
  });
  const aiAttempted = Boolean(aiSet.data?.questions.length && aiSet.data.questions.every(question => question.submittedAnswer !== null));
  return <article className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-700">{content.type === "PRACTICE" ? "학습 · 실습" : content.type}</span>{content.required && <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">필수</span>}</div><h2 className="mt-4 text-xl font-extrabold text-slate-950 sm:text-2xl">{content.title}</h2><div className="mt-5 rounded-2xl bg-slate-50 p-4"><p className="flex items-center gap-2 text-sm font-extrabold text-slate-900"><Target size={17} className="text-blue-600"/>학습 목표</p><p className="mt-2 text-sm leading-6 text-slate-600">{content.learningObjective}</p></div><div className="mt-6 min-w-0"><LearningMarkdown body={content.body} /></div>{content.keyPoints.length > 0 && <section className="mt-7"><h3 className="font-extrabold text-slate-950">핵심 정리</h3><ul className="mt-3 grid gap-2">{content.keyPoints.map(point => <li key={point} className="flex gap-2 text-sm leading-6 text-slate-600"><span className="mt-2 size-1.5 shrink-0 rounded-full bg-blue-500"/>{point}</li>)}</ul></section>}
    {practice && !historical && <AiPracticePanel contentId={content.contentId} basicPracticeHref={basicPracticeHref} basicPracticeLabel={nextBasicProblem ? "기본 확인 문제 풀기" : "학습 완료하기"} />}
    {practice && !historical && <section className="mt-7"><h3 className="font-extrabold text-slate-950">기본 확인 문제</h3><p className="mt-2 text-sm text-slate-600">AI 문제를 생성하는 대신 기존 확인 문제를 풀어도 콘텐츠를 완료할 수 있어요.</p><ul className="mt-3 grid gap-2">{content.problems.map((problem, index) => <li key={problem.problemId}><Link href={`/practice/${problem.problemId}?contentId=${content.contentId}`} className="flex min-h-12 items-center justify-between gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-bold text-blue-700 hover:border-blue-300 hover:bg-blue-50"><span>{index + 1}. {problem.title}</span><span className="shrink-0 text-xs">{problemQueries[index]?.data?.answered ? "제출 완료" : "문제 풀기 →"}</span></Link></li>)}</ul>{problemQueries.some(query => query.isError) && <button type="button" className="mt-3 text-sm font-bold text-blue-700 underline" onClick={() => { void Promise.all(problemQueries.map(query => query.refetch())); }}>풀이 상태 다시 불러오기</button>}</section>}
    {!historical && showCompletion && <Button id={`content-complete-${content.contentId}`} className="mt-7 w-full sm:w-auto" variant={content.status === "COMPLETED" ? "outline" : "default"} disabled={disabled || content.status === "COMPLETED" || (practice && !allAttempted && !aiAttempted)} onClick={onComplete}>{content.status === "COMPLETED" ? "완료됨" : practice ? "문제 풀이 확인하고 완료" : "읽음 완료"}</Button>}
  </article>;
}
