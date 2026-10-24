"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Gauge,
  Loader2,
  NotebookPen,
  RefreshCw,
  Shuffle,
  Sparkles,
  Target,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { claimsQuery, factsQuery } from "@/features/career/api";
import { failureMessage, formatLabel, type CardSummary, type TaskSummary, type Today } from "@/features/daily-task/contract";
import { startToday, swapTask, taskKeys, todayQuery } from "@/features/daily-task/queries";
import { currentMonthlyHero } from "./monthly-hero";
import { savedDeveloperQuotes, type MotivationalQuote } from "./motivational-quotes";

const card = "rounded-[24px] border border-slate-200/80 bg-white shadow-[0_12px_32px_rgba(15,23,42,0.045)]";

export function DashboardScreen() {
  const client = useQueryClient();
  const router = useRouter();
  const today = useQuery(todayQuery);
  const refresh = () => client.invalidateQueries({ queryKey: taskKeys.all });
  const start = useMutation({ mutationFn: startToday, onSuccess: async task => { await refresh(); if (task.status !== "PREPARING" && task.status !== "FAILED") router.push(`/tasks/${task.taskId}`); } });
  const swap = useMutation({ mutationFn: swapTask, onSuccess: refresh });
  if (today.isPending) return <StatePanel state="loading"/>;
  if (today.isError) return <StatePanel state="error" onRefresh={() => void today.refetch()}/>;
  const data = today.data;
  const busy = start.isPending || swap.isPending;
  const error = start.error ?? swap.error;

  return <div className="grid gap-6 pb-8 xl:gap-7">
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px] xl:items-stretch"><Hero today={data} busy={busy} onStart={() => start.mutate()}/><CalendarCard/></div>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
      <section id="today-task" className={`${card} min-w-0 p-5 sm:p-7`}>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4 dark:border-slate-700"><h2 className="text-xl font-black tracking-tight text-[#0a1837]">오늘의 역량 과제</h2><span className="text-xs font-bold text-slate-400">하루 30~60분 · 과제 하나</span></div>
        {data.task ? <TaskCard task={data.task} busy={busy} onSwap={() => swap.mutate(data.task!.taskId)} onRetry={() => start.mutate()}/> : <NoTask again={data.week.completed > 0} busy={busy} onStart={() => start.mutate()}/>}
        {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600">{error instanceof Error ? error.message : "과제를 준비하지 못했어요."}</p>}
      </section>
      <aside className="grid content-start gap-5"><WeekCard week={data.week} ai={data.ai}/><Encouragement/></aside>
    </div>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(330px,0.72fr)]"><RecentCards cards={data.recentCards}/><QuickMenu/></div>
    <CareerReadiness/>
  </div>;
}

function Hero({ today, busy, onStart }: { today: Today; busy: boolean; onStart: () => void }) {
  const task = today.task;
  const remaining = Math.max(0, today.week.target - today.week.completed);
  const description = task
    ? `${task.competency.areaTitle} · ${task.competency.title}`
    : remaining > 0 ? `이번 주 목표까지 과제 ${remaining}개 남았어요.` : "이번 주 목표를 채웠어요. 여유가 있다면 한 과제 더 해 볼까요?";
  const cta = "mt-6 inline-flex min-h-12 w-fit items-center gap-3 rounded-xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-lg shadow-blue-600/20 transition hover:-translate-y-0.5 hover:bg-blue-700 disabled:opacity-60";
  return <section className="relative min-h-[310px] overflow-hidden rounded-[26px] border border-blue-100 bg-[#eaf4ff] shadow-[0_18px_50px_rgba(37,99,235,0.08)] dark:bg-[#17243a] sm:min-h-[350px] xl:min-h-[300px]">
    <Image src={currentMonthlyHero()} alt="책상에서 오늘의 과제를 준비하는 호범 캐릭터" fill priority unoptimized sizes="(min-width:1280px) 70vw, 100vw" className="object-cover object-[64%_center]"/>
    <div className="absolute inset-0 bg-gradient-to-r from-white via-white/90 to-transparent dark:from-[#08111f]/95 dark:via-[#08111f]/75 dark:to-[#08111f]/20 sm:via-white/58 dark:sm:via-[#08111f]/60"/>
    <div className="relative flex min-h-[310px] max-w-xl flex-col justify-center p-7 sm:min-h-[350px] sm:p-9 xl:min-h-[300px]">
      <span className="mb-4 inline-flex w-fit items-center gap-2 text-xs font-extrabold text-blue-600"><Sparkles size={14}/> 약한 역량부터 매일 하나씩</span>
      <h1 className="text-3xl leading-[1.15] font-black tracking-[-0.045em] text-[#071838] dark:text-white sm:text-5xl xl:text-[42px]">오늘도,<br/>더 나은 내가 되는 중!</h1>
      <p className="dashboard-hero-description mt-4 max-w-sm text-sm leading-6 font-medium text-slate-600">{description}</p>
      {!task ? <button type="button" disabled={busy} onClick={onStart} className={cta}>{busy ? <Loader2 className="animate-spin" size={17}/> : null}{today.week.completed > 0 ? "한 과제 더 받기" : "오늘 과제 받기"}<ArrowRight size={17}/></button>
        : task.status === "PREPARING" ? <span className={`${cta} opacity-80`}><Loader2 className="animate-spin" size={17}/>과제를 만들고 있어요</span>
        : task.status === "FAILED" ? <a href="#today-task" className={cta}>과제 상태 확인하기<ArrowRight size={17}/></a>
        : <Link href={`/tasks/${task.taskId}`} className={cta}>{task.status === "READY" ? "오늘 과제 시작하기" : "오늘 과제 이어 하기"}<ArrowRight size={17}/></Link>}
    </div>
  </section>;
}

function TaskCard({ task, busy, onSwap, onRetry }: { task: TaskSummary; busy: boolean; onSwap: () => void; onRetry: () => void }) {
  const canSwap = task.status === "READY" || task.status === "PREPARING";
  return <div className="mt-5">
    <div className="grid gap-4 rounded-2xl bg-gradient-to-r from-blue-50/90 to-slate-50 p-4 dark:from-[#17243a] dark:to-[#151f2f] sm:grid-cols-[56px_minmax(0,1fr)] sm:items-center">
      <span className="grid size-14 place-items-center rounded-2xl bg-white text-blue-600 shadow-sm dark:bg-[#1b2940]"><Target size={27}/></span>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2 text-xs font-extrabold">
          <span className="rounded-full bg-white px-2.5 py-1 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300">{task.competency.code} · {task.competency.priority}</span>
          <span className="rounded-full bg-white px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">{formatLabel[task.format]}</span>
          {task.estimatedMinutes && <span className="rounded-full bg-white px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">작성 약 {task.estimatedMinutes}분</span>}
        </div>
        <h3 className="mt-2 text-lg font-black text-[#0a1837]">{task.title ?? task.competency.title}</h3>
        <p className="mt-1 text-xs leading-5 text-slate-500">{task.planReason}</p>
      </div>
    </div>
    {task.status === "PREPARING" && <p className="mt-4 flex items-center gap-2 text-sm font-semibold text-slate-600 dark:text-slate-300"><Loader2 className="animate-spin" size={17}/>역량 시트로 과제를 만들고 있어요. 보통 1분 안에 끝나요.</p>}
    {task.status === "FAILED" && <div className="mt-4 rounded-2xl bg-rose-50 p-4 text-sm text-rose-700 dark:bg-rose-950/40 dark:text-rose-200"><p>{failureMessage(task.failureCode)}</p><Button className="mt-3" variant="outline" disabled={busy} onClick={onRetry}><RefreshCw size={16}/>다시 받기</Button></div>}
    <div className="mt-5 flex flex-wrap gap-2">
      {task.status !== "PREPARING" && task.status !== "FAILED" && <Button asChild><Link href={`/tasks/${task.taskId}`}>{task.status === "READY" ? "시작하기" : "이어 하기"}<ArrowRight size={17}/></Link></Button>}
      {canSwap && <Button variant="outline" disabled={busy} onClick={onSwap}><Shuffle size={16}/>다른 역량으로 바꾸기</Button>}
    </div>
  </div>;
}

function NoTask({ again, busy, onStart }: { again: boolean; busy: boolean; onStart: () => void }) {
  return <div className="py-6">
    <span className="grid size-14 place-items-center rounded-2xl bg-blue-50 text-blue-600 dark:bg-[#17243a]">{again ? <CheckCircle2 size={25}/> : <Target size={25}/>}</span>
    <h3 className="mt-4 text-xl font-extrabold text-[#0a1837]">{again ? "한 과제 더 할까요?" : "오늘 과제를 받아 보세요"}</h3>
    <p className="mt-2 max-w-lg text-sm leading-6 text-slate-600 dark:text-slate-300">우선순위·수준 차이·복습 시점을 보고 오늘 다룰 역량을 골라요. 직접 판단해 쓰고, AI 꼬리질문으로 빈틈을 채워요.</p>
    <Button className="mt-5" disabled={busy} onClick={onStart}>{busy ? <Loader2 className="animate-spin" size={17}/> : <ArrowRight size={17}/>}{again ? "한 과제 더 받기" : "오늘 과제 받기"}</Button>
  </div>;
}

function WeekCard({ week, ai }: { week: Today["week"]; ai: Today["ai"] }) {
  const rate = week.target > 0 ? Math.min(100, Math.round(week.completed / week.target * 100)) : 0;
  return <section className={`${card} p-5 sm:p-6`}>
    <h2 className="font-black text-[#0a1837]">이번 주 진행 현황</h2>
    <div className="mt-5 flex items-center gap-5"><div className="grid size-24 shrink-0 place-items-center rounded-full" style={{ background: `conic-gradient(#2f7df6 ${rate * 3.6}deg,var(--progress-track) 0deg)` }}><div className="grid size-[72px] place-items-center rounded-full bg-white text-xl font-black text-[#0a1837] dark:bg-[#111827]">{rate}%</div></div><p className="text-xs leading-5 text-slate-500"><span>주간 목표 달성률</span><br/><strong className="text-sm text-[#0a1837]">{week.completed} / {week.target}</strong> 과제 완료</p></div>
    <Link href="/settings/ai-usage" className="mt-5 flex items-center justify-between gap-3 border-t border-slate-100 pt-4 text-xs dark:border-slate-700"><span className="flex items-center gap-2 font-semibold text-slate-500"><Gauge size={15} className="text-blue-600"/>이번 달 AI 예산</span><strong className="text-sm font-black text-[#0a1837]">{ai.enabled ? `${Math.floor(ai.remainingKrw).toLocaleString("ko-KR")}원 남음` : "AI 꺼짐"}</strong></Link>
    <p className="mt-1 text-[11px] text-slate-400">상한 {ai.limitKrw.toLocaleString("ko-KR")}원 · 상한에 닿으면 자가 채점으로 진행해요</p>
  </section>;
}

function CalendarCard() {
  const now = new Date();
  const year = now.getFullYear(), month = now.getMonth() + 1, day = now.getDate();
  const first = new Date(year, month - 1, 1).getDay();
  const last = new Date(year, month, 0).getDate();
  const cells = Array.from({ length: first + last }, (_, index) => index < first ? null : index - first + 1);
  const weekStart = day - ((now.getDay() + 6) % 7);
  return <section className={`${card} p-5 sm:p-6`}><div className="flex items-center justify-between"><h2 className="text-lg font-black text-[#0a1837]">{year}년 {month}월</h2><CalendarDays size={18} className="text-blue-600"/></div><div className="mt-5 grid grid-cols-7 gap-y-2.5 text-center text-[11px] font-bold"><span className="text-rose-400">일</span>{["월", "화", "수", "목", "금"].map(v => <span key={v} className="text-slate-400">{v}</span>)}<span className="text-blue-500">토</span>{cells.map((value, index) => <span key={index} className={`mx-auto grid size-8 place-items-center rounded-full ${value === day ? "bg-blue-600 text-white shadow-md shadow-blue-200 dark:shadow-none" : value !== null && value >= weekStart && value < weekStart + 7 ? "bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300" : "text-slate-600"}`}>{value}</span>)}</div><p className="mt-4 text-[11px] font-semibold text-slate-400">파란 칸이 이번 주 목표 기간이에요.</p></section>;
}

function RecentCards({ cards }: { cards: CardSummary[] }) {
  return <section className={`${card} flex flex-col p-5 sm:p-7`}>
    <h2 className="flex items-center gap-2 text-lg font-black text-[#0a1837]"><NotebookPen size={20} className="text-blue-600"/>최근 핵심 카드</h2>
    {cards.length > 0 && <ul className="mt-4 divide-y divide-slate-100 dark:divide-slate-700">{cards.map(item => <li key={item.taskId}><Link href={`/tasks/${item.taskId}`} className="group flex min-h-14 items-center gap-3 py-2.5"><span className="grid size-8 shrink-0 place-items-center rounded-full bg-emerald-100 text-emerald-600"><CheckCircle2 size={16}/></span><span className="min-w-0 flex-1"><span className="block truncate text-sm font-extrabold text-slate-800 dark:text-slate-100">{item.title}</span><span className="mt-0.5 block truncate text-xs text-slate-500">{item.interviewLine}</span></span><span className="hidden rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-bold text-blue-700 dark:bg-blue-500/10 dark:text-blue-300 sm:inline">{item.competencyCode}</span><ChevronRight size={16} className="text-slate-300 group-hover:text-blue-600"/></Link></li>)}</ul>}
    {cards.length < 3 && <CardPrompt empty={cards.length === 0}/>}
  </section>;
}

function CardPrompt({ empty }: { empty: boolean }) {
  return <div className="mt-4 grid flex-1 grid-cols-[96px_minmax(0,1fr)] items-center gap-4 overflow-hidden rounded-2xl border border-blue-100 bg-gradient-to-br from-blue-50/90 to-white px-4 py-3 dark:border-blue-900/80 dark:from-[#17243a] dark:to-[#131c2d] sm:grid-cols-[150px_minmax(0,1fr)] sm:gap-6 sm:px-6">
    <div className="relative h-32 w-full sm:h-44"><Image src="/assets/dashboard/recent-activity-duck.png" alt="체크리스트와 연필을 든 학습 오리 캐릭터" fill sizes="(min-width:640px) 150px, 96px" className="object-contain drop-shadow-[0_16px_20px_rgba(245,158,11,0.2)]"/></div>
    <div className="min-w-0"><p className="text-sm font-black text-[#0a1837] sm:text-base">{empty ? "첫 핵심 카드를 만들어 볼까요?" : "답변집을 더 채워 볼까요?"}</p><p className="mt-1 text-xs leading-5 text-slate-500">과제를 마치고 고쳐 쓴 답을 카드로 저장하면 면접 답변집으로 차곡차곡 쌓여요.</p><a href="#today-task" className="mt-3 inline-flex min-h-10 items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 text-xs font-extrabold text-white shadow-lg shadow-blue-200/50 hover:bg-blue-700 dark:shadow-none sm:text-sm">오늘 과제로 가기<ArrowRight size={16}/></a></div>
  </div>;
}

const quickLinks: { href: string; label: string; detail: string; image: string; tone: string }[] = [
  { href: "/workspace#today-task", label: "오늘 과제", detail: "직접 판단해 답해요", image: "/assets/quick-menu/practice.png", tone: "from-blue-50 to-blue-100/55" },
  { href: "/career", label: "이력서 점검", detail: "면접 질문에 대비해요", image: "/assets/quick-menu/exam.png", tone: "from-violet-50 to-violet-100/55" },
  { href: "/notifications", label: "알림함", detail: "놓친 소식을 확인해요", image: "/assets/quick-menu/review.png", tone: "from-rose-50 to-rose-100/55" },
  { href: "/settings/ai-usage", label: "AI 사용량", detail: "예산을 관리해요", image: "/assets/quick-menu/progress.png", tone: "from-emerald-50 to-emerald-100/55" },
];

function QuickMenu() {
  return <section className={`${card} p-5 sm:p-6`}><h2 className="text-lg font-black text-[#0a1837]">빠른 메뉴</h2><div className="mt-4 grid grid-cols-2 gap-3">{quickLinks.map(({ href, label, detail, image, tone }) => <Link key={href} href={href} className={`group flex min-h-36 flex-col items-center overflow-hidden rounded-2xl bg-gradient-to-br p-4 text-center transition hover:-translate-y-0.5 dark:from-[#182235] dark:to-[#182235] ${tone}`}><div className="relative size-16 transition-transform duration-200 group-hover:scale-105"><Image src={image} alt="" fill unoptimized sizes="64px" className="object-contain drop-shadow-[0_8px_10px_rgba(15,23,42,0.12)]"/></div><p className="mt-2 text-sm font-black text-slate-900">{label}</p><p className="mt-1 text-[11px] text-slate-500">{detail}</p></Link>)}</div></section>;
}

function CareerReadiness() {
  const facts = useQuery(factsQuery);
  const claims = useQuery(claimsQuery);
  const confirmed = facts.data?.filter(item => item.status === "CONFIRMED").length ?? 0;
  const needsWork = claims.data?.filter(item => item.reviewStatus === "NEEDS_FIX" || item.reviewStatus === "NEEDS_EVIDENCE").length ?? 0;
  const ok = claims.data?.filter(item => item.reviewStatus === "OK").length ?? 0;
  const value = (query: { isPending: boolean; isError: boolean }, text: string) => query.isPending ? "불러오는 중" : query.isError ? "확인 필요" : text;
  return <section className={`${card} p-5 sm:p-7`}>
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="flex items-center gap-2 text-lg font-black text-[#0a1837]"><BriefcaseBusiness size={20} className="text-blue-600"/>경력·이력서 준비</h2><p className="mt-1 text-xs text-slate-500">확정한 경력과 검토한 이력서 문장이 면접 질문의 근거가 돼요.</p></div><Link href="/career" className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-blue-500 px-4 text-xs font-extrabold text-blue-700">경력·이력서 보기<ArrowRight size={15}/></Link></div>
    <div className="mt-5 grid gap-3 sm:grid-cols-3">
      <Metric image="/assets/app-icons/goal.png" label="확정한 경력" value={value(facts, `${confirmed} / ${facts.data?.length ?? 0}개`)}/>
      <Metric image="/assets/app-icons/achievement.png" label="검토 통과 문장" value={value(claims, `${ok}개`)}/>
      <Metric image="/assets/app-icons/assistant.png" label="보완할 문장" value={value(claims, `${needsWork}개`)}/>
    </div>
  </section>;
}

function Metric({ image, label, value }: { image: string; label: string; value: string }) {
  return <div className="flex items-center gap-4 rounded-2xl border border-slate-100 p-4 dark:border-slate-700"><span className="relative size-11 shrink-0"><Image src={image} alt="" fill sizes="44px" className="object-contain"/></span><div><p className="text-xs font-semibold text-slate-400">{label}</p><p className="mt-1 text-xl font-black text-[#0a1837]">{value}</p></div></div>;
}

let clientQuoteIndex: number | null = null;
const subscribeToPageLoad = () => () => undefined;
const getServerQuoteIndex = () => 0;
function getClientQuoteIndex() {
  clientQuoteIndex ??= Math.floor(Math.random() * savedDeveloperQuotes.length);
  return clientQuoteIndex;
}

function Encouragement() {
  const quoteIndex = useSyncExternalStore(subscribeToPageLoad, getClientQuoteIndex, getServerQuoteIndex);
  const online = useQuery({
    queryKey: ["daily-motivation-quote"],
    queryFn: async () => {
      const response = await fetch("/daily-quote", { cache: "no-store" });
      if (!response.ok) throw new Error("Online quote unavailable");
      return await response.json() as MotivationalQuote;
    },
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
  const item = online.data ?? savedDeveloperQuotes[quoteIndex];
  return <section aria-label="오늘의 개발자 명언" data-quote-source={item.source} className="flex min-h-[116px] items-center gap-4 rounded-[24px] border border-blue-100 bg-gradient-to-br from-blue-50 to-white p-5 dark:border-slate-700 dark:from-[#17243a] dark:to-[#111827]">
    <div className="relative size-14 shrink-0 overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-blue-100 dark:ring-slate-700"><Image src={item.image} alt={`${item.person} 얼굴`} fill unoptimized={item.source === "online"} sizes="56px" className="object-cover"/></div>
    <figure className="min-w-0"><blockquote className="text-sm font-black leading-5 text-[#0a1837]">“{item.quote}”</blockquote><figcaption className="mt-2 text-[11px] font-bold text-slate-500">— {item.person}</figcaption></figure>
  </section>;
}
