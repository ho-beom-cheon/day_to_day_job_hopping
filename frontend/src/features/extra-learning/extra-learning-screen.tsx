"use client";

import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, BookOpen, Compass, FastForward, RotateCcw, Sparkles } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { createMutationAction } from "@/api/client";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { HelpHint } from "@/components/ui/help-hint";
import { StatePanel } from "@/components/ui/state-panel";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { categoryLabels, getExtraHistory, getExtraTopics, modeLabels, startExtra, type ExtraMode, type StartAction } from "./api";
import { AiLessonPanel, type LessonSeed } from "./ai-lesson-panel";

const choices = [
  { mode: "DEEPEN", icon: Sparkles, description: "오늘 배운 내용을 다시 살펴보고 더 연습해요." },
  { mode: "EXPLORE", icon: Compass, description: "공부하고 싶은 분야와 주제를 직접 골라요." },
  { mode: "REVIEW", icon: RotateCcw, description: "배웠던 내용을 다시 익혀요. 오답이 있는 주제를 먼저 보여드려요." },
  { mode: "AHEAD", icon: FastForward, description: "다음 학습을 미리 진행하고 정규 진도에 반영해요." },
] as const;

export function ExtraLearningEntry() {
  return <section className="flex flex-col gap-4 rounded-3xl border border-blue-200 bg-blue-50 p-5 dark:border-slate-700 dark:bg-slate-900 sm:flex-row sm:items-center sm:justify-between sm:p-6">
    <div><h2 className="flex items-center gap-2 text-lg font-black text-slate-950"><BookOpen size={21} className="text-blue-600"/>오늘 조금 더 공부할까요?</h2><p className="mt-2 text-sm leading-6 text-slate-600">원하는 주제와 시간으로 더 공부해요. 휴식일에도 자유롭게 시작할 수 있어요.</p></div>
    <Link href="/learning/extra" className="inline-flex min-h-12 shrink-0 items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 text-sm font-extrabold text-white hover:bg-blue-700">더 공부하기<ArrowRight size={17}/></Link>
  </section>;
}

export function ExtraLearningScreen({ initialTopic }: { initialTopic?: string } = {}) {
  const router = useRouter(); const client = useQueryClient();
  const [mode, setMode] = useState<ExtraMode>("EXPLORE"); const [category, setCategory] = useState("");
  const [selected, setSelected] = useState(initialTopic ? `${initialTopic}:` : ""); const [minutes, setMinutes] = useState(30);
  const [search, setSearch] = useState(""); const [stage, setStage] = useState("");
  const [lessonSeed, setLessonSeed] = useState<LessonSeed | null>(null);
  const lessonSequence = useRef(0);
  const openLesson = (seed: Omit<LessonSeed, "key">) => { lessonSequence.current += 1; setLessonSeed({ ...seed, key: lessonSequence.current }); };
  const action = useRef<StartAction | null>(null);
  const topics = useQuery({ queryKey: ["extra-topics", mode], queryFn: () => getExtraTopics(mode) });
  const history = useQuery({ queryKey: ["extra-learning"], queryFn: getExtraHistory });
  const available = (topics.data ?? []).filter(t => (!category || t.category === category)
    && (!stage || t.stage === stage) && (!search.trim() || `${t.title} ${t.learningObjective ?? ""}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())));
  const topic = available.find(t => `${t.contentVersionId}:${t.sourceSessionId ?? ""}` === selected) ?? (mode === "EXPLORE" ? undefined : available[0]);
  const categories = [...new Set((topics.data ?? []).map(t => t.category))];
  const start = useMutation({ mutationFn: () => {
    if (!topic) throw new Error("학습 주제를 선택해 주세요.");
    action.current ??= createMutationAction({ mode, contentVersionId: mode === "AHEAD" ? null : topic.contentVersionId, sourceSessionId: mode === "AHEAD" ? topic.sourceSessionId : null, targetMinutes: minutes });
    return startExtra(action.current);
  }, onSuccess: async entry => { action.current = null; await client.invalidateQueries({ queryKey: ["extra-learning"] }); router.push(`/workspace/learning/${entry.sessionId}`); } });
  const change = () => { action.current = null; start.reset(); };
  const questionCount = topic ? Math.min(topic.problemCount, minutes === 15 ? 3 : minutes === 30 ? 6 : 10) : 0;
  return <div className="mx-auto grid max-w-5xl gap-6 pb-8">
    <header><Link href="/learning" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-slate-600"><ArrowLeft size={16}/>학습으로</Link><h1 className="mt-3 text-3xl font-black text-slate-950">더 공부하기</h1><p className="mt-3 text-sm leading-6 text-slate-600">정규 과정의 일차와 관계없이 오늘 원하는 분야와 주제를 골라 공부하세요. 선택 학습과 문제 풀이는 별도로 기록됩니다.</p></header>
    <fieldset disabled={start.isPending} className="grid gap-3 sm:grid-cols-2"><legend className="mb-3 font-extrabold text-slate-950">어떻게 공부할까요?</legend>{choices.map(({ mode: value, icon: Icon, description }) => <label key={value} className={`flex cursor-pointer gap-3 rounded-2xl border p-5 focus-within:ring-2 focus-within:ring-blue-500 ${mode === value ? "border-blue-500 bg-blue-50 dark:bg-slate-800" : "border-slate-200 bg-white"}`}><input type="radio" name="extra-mode" value={value} checked={mode === value} onChange={() => { setMode(value); setCategory(""); setSelected(""); setSearch(""); setStage(""); change(); }} className="mt-1 accent-blue-600"/><div className="min-w-0"><span className="flex items-center gap-2 font-extrabold text-slate-950"><Icon size={18} className="shrink-0 text-blue-600"/>{modeLabels[value]}</span><p className="mt-2 text-sm leading-6 text-slate-600">{description}</p></div></label>)}</fieldset>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 sm:p-7">
      <h2 className="text-lg font-black text-slate-950">주제와 시간 선택</h2>
      {mode === "EXPLORE" && topics.data && <p className="mt-2 text-sm font-semibold text-blue-700">선택 가능한 {categories.length}개 분야 · {topics.data.length}개 주제</p>}
      {mode === "EXPLORE" && <Link href="/learning/program" className="mt-2 inline-flex min-h-11 items-center text-sm font-bold text-blue-700 underline">26주 분야별 학습 흐름 보기</Link>}
      {mode === "EXPLORE" && <div className="mt-3"><Button variant="outline" disabled={start.isPending} onClick={() => openLesson({ topic: search, subject: category || topic?.category || "JAVA", stage: stage || "실무", targetMinutes: minutes, baseContentVersionId: null })}><Sparkles size={16}/>AI로 새 주제 만들기</Button></div>}
      {mode === "EXPLORE" && !!topics.data?.length && <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_160px]">
        <label className="grid min-w-0 gap-2 text-sm font-bold text-slate-700">주제 검색<input type="search" value={search} disabled={start.isPending} onChange={e => { setSearch(e.target.value); setSelected(""); change(); }} placeholder="예: 여신, 잠금, 에이전트, JVM" className="min-h-12 min-w-0 rounded-xl border border-slate-300 bg-white px-3"/></label>
        <label className="grid gap-2 text-sm font-bold text-slate-700">학습 단계<select value={stage} disabled={start.isPending} onChange={e => { setStage(e.target.value); setSelected(""); change(); }} className="min-h-12 rounded-xl border border-slate-300 bg-white px-3"><option value="">전체 단계</option>{["기초","실무","심화"].map(s => <option key={s}>{s}</option>)}</select></label>
        <p className="text-xs text-slate-500">검색 결과 {available.length}개 · 기존 자료는 단계가 지정되지 않을 수 있어요.</p>
        {(search || stage || category) && <button type="button" onClick={() => { setSearch(""); setStage(""); setCategory(""); setSelected(""); change(); }} className="min-h-11 text-sm font-bold text-blue-700">검색·필터 초기화</button>}
      </div>}
      {mode === "EXPLORE" && topics.data && categories.length === 1 && categories[0] === "JAVA" &&
        <p role="status" className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">현재 검수된 추가 학습 자료는 Java 분야만 제공해요. 다른 분야는 학습 본문과 문제를 보강한 뒤 제공해야 합니다.</p>}
      {topics.isPending ? <StatePanel state="loading"/> : topics.isError ? <StatePanel state="error" onRefresh={() => void topics.refetch()}/> : available.length === 0 ? <div className="mt-5 rounded-2xl bg-slate-50 p-5 text-sm leading-6 text-slate-600">{mode === "DEEPEN" ? "오늘 배정된 학습이 없어요. 다른 분야나 복습을 선택해 보세요." : mode === "AHEAD" ? "미리 진행할 다음 학습이 없어요. 다른 분야나 복습으로 이어갈 수 있어요." : mode === "REVIEW" ? "아직 시작한 학습이 없어요. 오늘 학습이나 다른 분야를 먼저 공부해 보세요." : topics.data?.length ? "검색 조건에 맞는 주제가 없어요. 조건을 바꾸거나 AI로 새 주제를 만들어 보세요." : "현재 선택할 수 있는 공개 학습 자료가 없어요."}</div> : <>
        <div className="mt-5 grid gap-4 sm:grid-cols-[160px_1fr]"><label className="grid gap-2 text-sm font-bold text-slate-700">분야<select disabled={start.isPending} value={category} onChange={e => { setCategory(e.target.value); setSelected(""); change(); }} className="min-h-12 min-w-0 rounded-xl border border-slate-300 bg-white px-3"><option value="">전체 분야</option>{categories.map(c => <option key={c} value={c}>{categoryLabels[c] ?? c} · {topics.data?.filter(t => t.category === c).length ?? 0}개</option>)}</select></label><label className="grid min-w-0 gap-2 text-sm font-bold text-slate-700">공부할 주제<select disabled={start.isPending} value={topic ? `${topic.contentVersionId}:${topic.sourceSessionId ?? ""}` : ""} onChange={e => { setSelected(e.target.value); change(); }} className="min-h-12 w-full min-w-0 rounded-xl border border-slate-300 bg-white px-3">{mode === "EXPLORE" && <option value="">공부할 주제를 선택하세요</option>}{available.map(t => <option key={`${t.contentVersionId}:${t.sourceSessionId}`} value={`${t.contentVersionId}:${t.sourceSessionId ?? ""}`}>{t.title}{t.generated ? " · AI 생성" : ""}{t.wrongCount > 0 ? ` · 복습할 오답 ${t.wrongCount}개` : ""}</option>)}</select></label></div>
        {topic?.learningObjective && <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-slate-700"><p className="font-bold text-blue-800">{topic.stage ?? "기존 학습"}{topic.recommendedWeek ? ` · 권장 ${topic.recommendedWeek}주` : ""}{topic.generated ? " · 내 AI 학습자료" : ""}</p><p className="mt-1">{topic.learningObjective}</p></div>}
        {topic && mode !== "AHEAD" && <Button className="mt-3" variant="outline" disabled={start.isPending} onClick={() => openLesson({ topic: `${topic.title}의 심화 사례와 대안 비교`, subject: topic.category, stage: "심화", targetMinutes: minutes, baseContentVersionId: topic.contentVersionId, baseTitle: topic.title })}>이 주제 더 깊게 만들기</Button>}
        <fieldset disabled={start.isPending} className="mt-6 flex flex-wrap gap-3"><legend className="mb-3 text-sm font-bold text-slate-700">예상 소요 시간</legend>{[30,60,90,120].map(value => <label key={value} className={`flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border px-5 focus-within:ring-2 focus-within:ring-blue-500 ${minutes === value ? "border-blue-500 bg-blue-50 text-blue-800 dark:text-blue-200" : "border-slate-200"}`}><input type="radio" name="extra-minutes" checked={minutes === value} onChange={() => { setMinutes(value); change(); }} className="accent-blue-600"/>{value}분</label>)}</fieldset>
        <div className="mt-5 rounded-2xl bg-slate-50 p-4 text-sm leading-6 text-slate-600">{mode === "AHEAD" ? "선택한 시간만큼 다음 세션을 진행하고 이어서 학습할 수 있어요. 세션의 필수 콘텐츠를 모두 마치면 정규 진도에 반영돼요." : !topic ? "분야와 주제를 고르면 해당 학습 자료와 확인 문제 수를 보여드려요." : <><strong className="text-slate-900">{minutes === 15 ? "핵심 설명을 읽고 짧게 확인해요." : minutes === 30 ? "설명과 예제를 살펴보고 직접 적용해요." : "본문과 예제를 읽고 실습까지 차근차근 진행해요."}</strong><p>기존 학습 자료와 확인 문제 {questionCount}개를 묶어요. 새로운 문제가 필요하면 학습 안에서 AI 연습을 요청할 수 있어요.</p></>}</div>
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3"><span className="flex items-center gap-2 text-sm text-slate-500">{mode === "AHEAD" ? "완료하면 정규 진도에 반영" : "기본 학습 완료 상태는 그대로"}<HelpHint label="추가 학습 기록" summary="선택한 시간은 세션의 예상 소요 시간이에요. 실제 학습 시간으로 기록하지 않으며, 완료 여부와 문제 풀이 기록은 따로 저장돼요." note="다음 진도 이어가기에서 완료한 세션은 예정일에도 완료로 유지돼요. 시험 일정은 그대로예요."/></span><Button disabled={start.isPending || !topic} onClick={() => start.mutate()}>{start.isPending ? "학습 준비 중…" : "추가 학습 시작"}<ArrowRight size={17}/></Button></div>
      </>}
      {start.isError && <div className="mt-4"><ApiErrorFeedback viewModel={toApiErrorViewModel(start.error)} onRefresh={() => start.mutate()}/></div>}
    </section>
    <AiLessonPanel seed={lessonSeed} onClose={() => setLessonSeed(null)}/>
    <section><h2 className="text-lg font-black text-slate-950">나의 추가 학습</h2>{history.isPending ? <StatePanel state="loading"/> : history.isError ? <StatePanel state="error" onRefresh={() => void history.refetch()}/> : !history.data.length ? <p className="mt-3 text-sm text-slate-500">시작한 추가 학습이 여기에 저장돼요.</p> : <><p className="mt-2 text-sm font-bold text-blue-700">추가 학습 {history.data.length}건 · 완료 {history.data.filter(e => e.status === "COMPLETED").length}건</p><ul className="mt-4 grid gap-3">{history.data.map(entry => <li key={entry.sessionId}><Link href={`/workspace/learning/${entry.sessionId}`} className="flex min-h-24 items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-5"><div className="min-w-0"><p className="text-xs font-bold text-blue-700">{modeLabels[entry.mode]} · {categoryLabels[entry.category] ?? entry.category}{entry.courseStatus === "CANCELED" ? " · 이전 과정" : ""}</p><h3 className="mt-1 font-extrabold text-slate-950">{entry.title}</h3><p className="mt-2 text-xs text-slate-500">예상 {entry.targetMinutes}분 · {entry.status === "COMPLETED" ? "완료" : "진행 중"}</p></div><span className="shrink-0 text-sm font-bold text-blue-700">{entry.courseStatus === "CANCELED" ? "이전 기록" : entry.status === "COMPLETED" ? "완료 기록" : "이어서 학습"} →</span></Link></li>)}</ul></>}</section>
  </div>;
}
