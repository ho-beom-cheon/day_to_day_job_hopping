"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { api } from "@/api";
import { StatePanel } from "@/components/ui/state-panel";

type Day = { dayNo: number; title: string; objectives: { id: string; description: string }[] };
type Program = { curriculum: { title: string; weeks: { week: number; title: string; subjects: string[]; days: Day[] }[] };
  readinessMessage: string; generationReady: boolean; availableCatalog: { subject: string; publishedLessonCount: number }[];
  topicPath: { contentVersionId: string; title: string; subject: string; stage: string; week: number; objective: string }[] };
type Preview = { message: string; objectiveCount: number; plan: { kind: string; totalSeconds: number; learningSeconds: number;
  assessmentSeconds: number; slots: unknown[]; sessions: string[][]; deferredObjectives: string[] } };

function data<T>(value: unknown): T {
  if (!value || typeof value !== "object" || !("data" in value) || !value.data || typeof value.data !== "object") throw new TypeError("Invalid study program response");
  return value.data as T;
}
const names: Record<string, string> = { JAVA: "Java", SPRING: "Spring", SQL: "SQL·DB", API: "API 연동", TEST: "테스트", FINANCE: "금융 업무", ALGORITHM: "알고리즘", AI: "AI 개발 활용", INTERVIEW: "면접" };
const kinds: Record<string, string> = { DAILY: "일간", WEEKLY: "주간", MONTHLY: "월간", FINAL: "최종" };

export function StudyProgramScreen() {
  const [day, setDay] = useState(1); const [minutes, setMinutes] = useState(60); const [finalAssessment, setFinalAssessment] = useState(false);
  const program = useQuery({ queryKey: ["study-program"], queryFn: async () => (await api.request({ path: "/api/v1/study-program", decode: data<Program> })).data });
  const preview = useQuery({ queryKey: ["study-program-preview", day, minutes, finalAssessment], queryFn: async () => (await api.request({ path: `/api/v1/study-program/preview?day=${day}&minutes=${minutes}&finalAssessment=${finalAssessment}`, decode: data<Preview> })).data });
  if (program.isPending) return <StatePanel state="loading" />;
  if (program.isError) return <StatePanel state="error" onRefresh={() => void program.refetch()} />;
  const plan = preview.data?.plan;
  return <div className="mx-auto grid max-w-5xl gap-6 pb-8">
    <header><Link href="/learning" className="inline-flex min-h-11 items-center text-sm font-bold text-blue-700">학습으로 돌아가기</Link>
      <h1 className="mt-2 text-2xl font-black text-slate-950 sm:text-3xl">{program.data.curriculum.title}</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">여러 분야를 함께 익히며 금융권 백엔드 면접을 준비합니다. 26 교육 주차를 기준으로 하고, 실제 완료 시점은 공부한 분량에 따라 달라집니다.</p></header>
    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-amber-950" aria-label="과정 준비 상태">
      <h2 className="font-extrabold">분야별 학습 자료 · 선택 학습 가능</h2><p className="mt-2 text-sm leading-6">{program.data.readinessMessage}</p>
      <Link className="mt-3 inline-flex min-h-11 items-center font-bold underline" href="/learning/extra">현재 발행된 주제로 공부하기</Link>
    </section>
    <section aria-label="발행된 26주 학습 주제"><h2 className="text-lg font-black text-slate-950">기초부터 실무·심화까지 · 26주 권장 흐름</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">각 주제를 누르면 실제 학습 자료를 선택할 수 있어요. 여러 분야를 섞어 진행하며, 권장 주차와 관계없이 먼저 공부해도 됩니다. 기존 Java 기초와 다른 발행 자료도 더 공부하기에서 함께 선택할 수 있어요.</p>
      <div className="mt-3 flex flex-wrap gap-2">{program.data.availableCatalog.map(c => <span key={c.subject} className="rounded-lg bg-blue-50 px-3 py-2 text-sm font-bold text-blue-800">{names[c.subject] ?? c.subject} {c.publishedLessonCount}개</span>)}</div>
      <div className="mt-4 grid gap-3">{Array.from({length:26},(_,i)=>i+1).map(week => <details key={week} className="rounded-2xl border border-slate-200 bg-white p-5" open={week===1}>
        <summary className="cursor-pointer font-extrabold text-slate-950">권장 {week}주 · {program.data.topicPath.filter(t=>t.week===week).length}개 주제</summary>
        <ul className="mt-3 grid gap-2">{program.data.topicPath.filter(t=>t.week===week).map(t=><li key={t.contentVersionId}><Link href={`/learning/extra?topic=${t.contentVersionId}`} className="block rounded-xl border border-slate-200 p-4 hover:border-blue-400"><span className="text-xs font-bold text-blue-700">{names[t.subject]} · {t.stage}</span><strong className="mt-1 block text-sm text-slate-950">{t.title} →</strong><p className="mt-1 text-sm leading-6 text-slate-600">{t.objective}</p></Link></li>)}</ul>
      </details>)}</div>
    </section>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 sm:p-7">
      <h2 className="text-lg font-black text-slate-950">시간과 평가 구성 미리 보기</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">선택 시간에는 학습·실습·풀이·해설이 모두 포함됩니다. 주간은 일간을, 월간은 일간과 주간을 대체합니다.</p>
      <div className="mt-5 flex flex-wrap items-end gap-4">
        <label className="grid gap-2 text-sm font-bold">학습일<select value={day} onChange={e => setDay(Number(e.target.value))} className="min-h-11 rounded-xl border border-slate-300 bg-white px-3">{Array.from({length: 130}, (_, i) => <option key={i+1} value={i+1}>{i+1}일차 · {Math.floor(i/5)+1}주</option>)}</select></label>
        <label className="grid gap-2 text-sm font-bold">총시간<select value={minutes} onChange={e => setMinutes(Number(e.target.value))} className="min-h-11 rounded-xl border border-slate-300 bg-white px-3">{[30,60,90,120].map(n => <option key={n} value={n}>{n}분</option>)}</select></label>
        <label className="flex min-h-11 items-center gap-2 text-sm font-bold"><input type="checkbox" checked={finalAssessment} onChange={e => setFinalAssessment(e.target.checked)} />선택 일차까지 최종 평가 예상</label>
      </div>
      {preview.isError ? <StatePanel state="error" onRefresh={() => void preview.refetch()} /> : plan && <div className="mt-5 rounded-2xl bg-slate-50 p-5" aria-live="polite">
        <p className="font-extrabold text-slate-950">{kinds[plan.kind]} 평가 · 목표 {preview.data?.objectiveCount}개</p>
        <p className="mt-2 text-sm leading-6 text-slate-700">학습·복습 {plan.learningSeconds/60}분 / 평가·해설 {plan.assessmentSeconds/60}분 · 예상 {plan.slots.length}문항{plan.kind === "FINAL" ? ` · ${plan.sessions.length}회차로 분할` : ""}</p>
        <p className="mt-2 text-sm leading-6 text-slate-600">{preview.data?.message}</p>
        {plan.deferredObjectives.length > 0 && <p className="mt-2 font-bold text-amber-800">시간 조정이 필요한 목표 {plan.deferredObjectives.length}개 · 현재 계획으로는 평가를 생성할 수 없습니다.</p>}
      </div>}
    </section>
    <section><h2 className="text-lg font-black text-slate-950">통합 평가를 위한 목표 설계</h2><p className="mt-2 text-sm text-slate-600">아래는 별도 평가 설계의 목표 목차입니다. 실제 선택 학습은 위의 발행된 주제에서 시작하세요.</p>
      <div className="mt-4 grid gap-3">{program.data.curriculum.weeks.map(week => <details key={week.week} className="rounded-2xl border border-slate-200 bg-white p-5">
        <summary className="cursor-pointer font-extrabold text-slate-950">{week.week}주 · {week.title}<span className="mt-2 block text-xs font-semibold text-slate-500">{week.subjects.map(s => names[s] ?? s).join(" · ")}</span></summary>
        <ol className="mt-4 grid gap-3">{week.days.map(d => <li key={d.dayNo} className="border-t border-slate-100 pt-3"><strong className="text-sm">{d.dayNo}일차 · {d.title}</strong><p className="mt-1 text-sm leading-6 text-slate-600">{d.objectives.map(o => o.description).join(" ")}</p></li>)}</ol>
      </details>)}</div>
    </section>
  </div>;
}
