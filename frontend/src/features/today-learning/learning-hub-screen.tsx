"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, BookOpen, CalendarDays, Check, CheckCircle2, Clock3, Coffee, ListChecks, Play, RotateCcw } from "lucide-react";
import Link from "next/link";
import { ExtraLearningEntry } from "@/features/extra-learning/extra-learning-screen";
import { ActiveExtraSessions } from "@/features/extra-learning/active-extra-sessions";
import { StatePanel } from "@/components/ui/state-panel";
import type { LearningDayDto, SessionSummaryDto } from "./contract";
import { formatKoreanDate, statusLabel, toDashboardViewModel } from "./model";
import { dashboardQuery } from "./queries";

const card = "rounded-[24px] border border-slate-200/80 bg-white shadow-[0_12px_35px_rgba(15,23,42,0.05)]";

export function LearningHubScreen() {
  const query = useQuery(dashboardQuery);
  const activeExtra = <ActiveExtraSessions enabled={Boolean(query.data?.curriculum)}/>;
  if (query.isPending) return <StatePanel state="loading" />;
  if (query.isError) return <StatePanel state="error" onRefresh={() => void query.refetch()} />;

  const view = toDashboardViewModel(query.data.curriculum, query.data.today);
  if (!view.curriculum) return <NoCurriculum />;
  if (!view.today || view.today.kind === "NONE") return <div className="grid gap-6">{activeExtra}<ExtraLearningEntry/><NoLearningDay title={view.curriculum.title}/></div>;
  if (view.today.kind === "REST") return <div className="grid gap-6">{activeExtra}<ExtraLearningEntry/><RestDay day={view.today} title={view.curriculum.title}/></div>;

  const day = view.today;
  const completed = day.sessions.filter(session => session.status === "COMPLETED").length;
  const progress = day.totalSessionCount === 0 ? 0 : Math.round((completed / day.totalSessionCount) * 100);

  return <div className="grid gap-6 pb-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-sm font-extrabold text-blue-600">{formatKoreanDate(day.date)} · {day.weekNo ? `${day.weekNo}주차` : "오늘"}</p>
        <h1 className="mt-1 text-3xl font-black tracking-[-0.04em] text-[#071838] sm:text-4xl">학습하기</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">오늘의 세션을 순서대로 완료하고 학습 기록을 남겨보세요.</p>
      </div>
      <Link href="/roadmap" className="inline-flex min-h-11 w-fit items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-600 hover:border-blue-300 hover:text-blue-700"><CalendarDays size={17}/>전체 로드맵</Link>
    </header>

    <section className="overflow-hidden rounded-[28px] bg-gradient-to-br from-[#0b5ee8] via-[#2563eb] to-[#5b5ee8] p-6 text-white shadow-[0_20px_55px_rgba(37,99,235,0.2)] dark:from-[#213b60] dark:via-[#2e4b75] dark:to-[#484e80] dark:shadow-[0_20px_55px_rgba(4,12,28,0.35)] sm:p-8">
      <div className="grid gap-7 lg:grid-cols-[minmax(0,1fr)_280px] lg:items-end">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-xs font-extrabold text-blue-100"><span className="rounded-full bg-white/14 px-3 py-1.5">오늘 배정된 정규 학습</span><span>{day.monthNo ? `${day.monthNo}단계` : "현재 과정"} · {view.curriculum.title}</span></div>
          <h2 className="mt-5 max-w-2xl text-2xl font-black tracking-[-0.035em] sm:text-3xl">{day.title || "오늘의 학습"}</h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-blue-100">{day.description || "배정된 학습 세션을 차근차근 진행해 보세요."}</p>
          {view.nextSession ? <Link href={`/workspace/learning/${view.nextSession.sessionId}`} className="mt-6 inline-flex min-h-12 items-center gap-2 rounded-xl bg-white px-5 text-sm font-extrabold text-blue-700 shadow-lg shadow-blue-950/10 hover:bg-blue-50"><Play size={17} fill="currentColor"/>{view.nextSession.status === "IN_PROGRESS" ? "이어서 학습" : "다음 세션 시작"}<ArrowRight size={17}/></Link> : <span className="mt-6 inline-flex min-h-12 items-center gap-2 rounded-xl bg-emerald-400/20 px-5 text-sm font-extrabold text-white"><CheckCircle2 size={18}/>오늘 학습 완료</span>}
        </div>
        <div className="rounded-2xl border border-white/15 bg-white/10 p-5 backdrop-blur-sm">
          <div className="flex items-end justify-between"><div><p className="text-xs font-bold text-blue-100">오늘 진행률</p><p className="mt-1 text-3xl font-black">{progress}%</p></div><p className="text-sm font-bold text-blue-100">{completed} / {day.totalSessionCount} 세션</p></div>
          <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-blue-950/25"><div className="h-full rounded-full bg-white transition-[width]" style={{width:`${progress}%`}}/></div>
          <div className="mt-4 flex items-center justify-between text-xs text-blue-100"><span className="inline-flex items-center gap-1.5"><Clock3 size={14}/>{day.actualMinutes > 0 ? `직접 기록한 ${day.actualMinutes}분` : `예상 ${day.estimatedMinutes}분`}</span><span>Day {day.dayNo ?? "-"}</span></div>
        </div>
      </div>
    </section>

    {activeExtra}
    <ExtraLearningEntry/>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
      <section className={`${card} p-5 sm:p-7`} aria-labelledby="session-list-title">
        <div className="flex flex-wrap items-end justify-between gap-3"><div><p className="text-xs font-extrabold text-blue-600">TODAY&apos;S PLAN</p><h2 id="session-list-title" className="mt-1 text-xl font-black text-[#071838]">오늘 배정된 정규 학습 세션</h2></div><p className="text-xs font-semibold text-slate-400">순서대로 학습해 보세요</p></div>
        {day.sessions.length ? <ol className="mt-5 grid gap-3">{day.sessions.map((session, index) => <SessionCard key={session.sessionId} session={session} index={index} />)}</ol> : <div className="mt-5 rounded-2xl bg-slate-50 p-6 text-sm text-slate-500">오늘 등록된 학습 세션이 없습니다.</div>}
      </section>

      <aside className="grid content-start gap-5">
        <section className={`${card} p-5 sm:p-6`}><div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-600"><ListChecks size={20}/></span><div><h2 className="font-black text-[#071838]">오늘의 목표</h2><p className="mt-0.5 text-xs text-slate-500">완료까지 한 걸음씩</p></div></div><dl className="mt-5 grid gap-3 text-sm"><Metric label="전체 세션" value={`${day.totalSessionCount}개`}/><Metric label="완료한 세션" value={`${completed}개`} strong/><Metric label="남은 학습" value={`${Math.max(0, day.totalSessionCount - completed)}개`}/><Metric label="예상 시간" value={`${day.estimatedMinutes}분`}/></dl></section>
        <section className="rounded-[24px] border border-blue-100 bg-gradient-to-br from-blue-50 to-white p-6 dark:border-slate-700 dark:from-[#17243a] dark:to-[#111827]"><BookOpen size={23} className="text-blue-600 dark:text-blue-400"/><h2 className="mt-4 text-lg font-black text-[#071838]">학습 기록은 자동으로 이어져요</h2><p className="mt-2 text-sm leading-6 text-slate-600">세션을 시작하고 콘텐츠를 완료하면 진행 상태가 저장됩니다. 중간에 나가도 이어서 학습할 수 있어요.</p><Link href="/progress" className="mt-4 inline-flex min-h-10 items-center gap-1.5 text-sm font-extrabold text-blue-700 dark:text-blue-300">학습 통계 보기<ArrowRight size={15}/></Link></section>
      </aside>
    </div>
  </div>;
}

function SessionCard({session,index}:{session:SessionSummaryDto;index:number}) {
  const completed = session.status === "COMPLETED";
  const inProgress = session.status === "IN_PROGRESS";
  return <li><Link href={`/workspace/learning/${session.sessionId}`} aria-label={`${session.title} ${inProgress ? "이어서 학습" : completed ? "완료한 세션 보기" : "학습 시작"}`} className={`group grid min-h-24 grid-cols-[44px_minmax(0,1fr)_auto] items-center gap-3 rounded-2xl border p-3 transition sm:gap-4 sm:p-4 ${inProgress ? "border-blue-300 bg-blue-50/70 shadow-[0_8px_24px_rgba(37,99,235,0.08)] dark:border-[#3b5b87] dark:bg-[#1a2d49] dark:shadow-none" : completed ? "border-emerald-100 bg-emerald-50/35 dark:border-emerald-800/70 dark:bg-[#102723]" : "border-slate-100 hover:border-blue-200 hover:bg-blue-50/30"}`}><span className={`grid size-11 place-items-center rounded-xl text-sm font-black ${completed ? "bg-emerald-100 text-emerald-700 dark:text-emerald-300" : inProgress ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-500"}`}>{completed ? <Check size={19}/> : index + 1}</span><span className="min-w-0"><span className="flex flex-wrap items-center gap-2"><strong className="truncate text-sm font-black text-[#071838] sm:text-base">{session.title}</strong>{session.required && <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-extrabold text-slate-500">필수</span>}</span><span className="mt-1 flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-500"><span>{session.category}</span><span aria-hidden="true">·</span><span>{session.estimatedMinutes}분</span><span aria-hidden="true">·</span><span className={completed ? "text-emerald-700 dark:text-emerald-300" : inProgress ? "text-blue-700" : ""}>{statusLabel(session.status)}</span></span></span><span className={`grid size-10 place-items-center rounded-full ${inProgress ? "bg-blue-600 text-white" : completed ? "bg-emerald-50 text-emerald-700 shadow-sm dark:text-emerald-300" : "bg-white text-slate-400 shadow-sm group-hover:text-blue-600"}`}>{inProgress ? <RotateCcw size={17}/> : completed ? <CheckCircle2 size={18}/> : <Play size={16}/>}</span></Link></li>;
}

function Metric({label,value,strong=false}:{label:string;value:string;strong?:boolean}) { return <div className="flex items-center justify-between border-b border-slate-100 pb-3 last:border-0 last:pb-0"><dt className="font-semibold text-slate-500">{label}</dt><dd className={strong ? "font-black text-blue-700" : "font-extrabold text-slate-800"}>{value}</dd></div>; }

function NoCurriculum() { return <EmptyState icon={BookOpen} eyebrow="학습 준비" title="먼저 학습 과정을 선택해 주세요" description="과정을 선택하면 오늘의 학습 세션과 진행 순서가 이곳에 표시됩니다." href="/onboarding" action="학습 계획 만들기"/>; }
function NoLearningDay({title}:{title:string}) { return <EmptyState icon={CalendarDays} eyebrow={title} title="오늘 배정된 학습이 없어요" description="전체 로드맵에서 다음 학습일과 예정된 세션을 확인할 수 있습니다." href="/roadmap" action="로드맵 확인하기"/>; }
function RestDay({day,title}:{day:LearningDayDto;title:string}) { return <EmptyState icon={Coffee} eyebrow={`${title} · ${formatKoreanDate(day.date)}`} title="오늘은 휴식일이에요" description={day.description || "충분히 쉬고 다음 학습을 위한 에너지를 채워보세요."} href="/calendar" action="학습 일정 확인하기"/>; }
function EmptyState({icon:Icon,eyebrow,title,description,href,action}:{icon:typeof BookOpen;eyebrow:string;title:string;description:string;href:string;action:string}) { return <section className="mx-auto grid min-h-[560px] max-w-3xl place-items-center"><div className={`${card} w-full p-7 text-center sm:p-12`}><span className="mx-auto grid size-16 place-items-center rounded-2xl bg-blue-50 text-blue-600"><Icon size={29}/></span><p className="mt-6 text-xs font-extrabold text-blue-600">{eyebrow}</p><h1 className="mt-2 text-2xl font-black tracking-tight text-[#071838] sm:text-3xl">{title}</h1><p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-500">{description}</p><Link href={href} className="mt-7 inline-flex min-h-12 items-center gap-2 rounded-xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-[0_4px_10px_rgba(15,23,42,0.14)] hover:bg-blue-700 dark:shadow-[0_4px_10px_rgba(0,0,0,0.28)]">{action}<ArrowRight size={17}/></Link></div></section>; }
