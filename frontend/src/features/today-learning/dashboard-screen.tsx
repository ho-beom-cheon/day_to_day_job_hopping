"use client";

import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowRight,
  BookOpen,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Flame,
  Play,
  Sparkles,
  Target,
  TrendingUp,
  type LucideIcon,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useSyncExternalStore } from "react";
import { ActiveExtraSessions } from "@/features/extra-learning/active-extra-sessions";
import { StatePanel } from "@/components/ui/state-panel";
import type { CategoryProgress, OverallProgress } from "@/features/progress/contract";
import { categoryProgressQuery, overallProgressQuery } from "@/features/progress/queries";
import type { RoadmapMonthDto } from "@/features/roadmap/contract";
import { roadmapMonthsQuery } from "@/features/roadmap/queries";
import { dashboardQuery } from "./queries";
import type { RecentSessionDto } from "./contract";
import { formatKoreanDate, progressLabel, statusLabel, toDashboardViewModel, type DashboardViewModel } from "./model";
import { currentMonthlyHero } from "./monthly-hero";
import { savedDeveloperQuotes, type MotivationalQuote } from "./motivational-quotes";

type LoadedDashboardViewModel = DashboardViewModel & { curriculum: NonNullable<DashboardViewModel["curriculum"]> };
const card = "rounded-[24px] border border-slate-200/80 bg-white shadow-[0_12px_32px_rgba(15,23,42,0.045)]";

export function TodayDashboardScreen() {
  const dashboard = useQuery(dashboardQuery);
  const curriculumId = dashboard.data?.curriculum?.curriculumId ?? "0";
  const hasCurriculum = Boolean(dashboard.data?.curriculum);
  const progress = useQuery({ ...overallProgressQuery, enabled: hasCurriculum });
  const categories = useQuery({ ...categoryProgressQuery, enabled: hasCurriculum });
  const months = useQuery({ ...roadmapMonthsQuery(curriculumId), enabled: hasCurriculum });

  if (dashboard.isPending) return <StatePanel state="loading" />;
  if (dashboard.isError) return <StatePanel state="error" onRefresh={() => void dashboard.refetch()} />;

  const view = toDashboardViewModel(dashboard.data.curriculum, dashboard.data.today);
  if (!view.curriculum) return <EmptyDashboard />;
  const loadedView = view as LoadedDashboardViewModel;

  return <div className="grid gap-6 pb-8 xl:gap-7">
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px] xl:items-stretch"><Hero view={loadedView}/><CalendarCard view={loadedView}/></div>
    <ActiveExtraSessions/>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
      <LearningPanel view={loadedView}/>
      <aside className="grid content-start gap-5"><ProgressCard view={loadedView} progress={progress.data ?? null}/><Encouragement/></aside>
    </div>
    <LearningInsight categories={categories.data ?? []} pending={categories.isPending} failed={categories.isError}/>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(330px,0.72fr)]"><RecentActivity sessions={dashboard.data.recentSessions} nextHref={loadedView.nextSession ? `/workspace/learning/${loadedView.nextSession.sessionId}` : "/roadmap"}/><QuickMenu/></div>
    <CareerGoal months={months.data ?? []} completionRate={loadedView.curriculum.completionRate}/>
    <GrowthRecord progress={progress.data ?? null} pending={progress.isPending} failed={progress.isError}/>
  </div>;
}

function Hero({ view }: { view: DashboardViewModel }) {
  const href = view.nextSession ? `/workspace/learning/${view.nextSession.sessionId}` : "#today-learning";
  return <section className="relative min-h-[310px] overflow-hidden rounded-[26px] border border-blue-100 bg-[#eaf4ff] shadow-[0_18px_50px_rgba(37,99,235,0.08)] dark:bg-[#17243a] sm:min-h-[350px] xl:min-h-[300px]">
    <Image src={currentMonthlyHero()} alt="책상에서 오늘의 학습을 준비하는 호범 캐릭터" fill priority unoptimized sizes="(min-width:1280px) 70vw, 100vw" className="object-cover object-[64%_center]"/>
    <div className="absolute inset-0 bg-gradient-to-r from-white via-white/90 to-transparent dark:from-[#08111f]/95 dark:via-[#08111f]/75 dark:to-[#08111f]/20 sm:via-white/58 dark:sm:via-[#08111f]/60"/>
    <div className="relative flex min-h-[310px] max-w-xl flex-col justify-center p-7 sm:min-h-[350px] sm:p-9 xl:min-h-[300px]">
      <span className="mb-4 inline-flex w-fit items-center gap-2 text-xs font-extrabold text-blue-600"><Sparkles size={14}/> 매일의 학습을 이어가요</span>
      <h1 className="text-3xl leading-[1.15] font-black tracking-[-0.045em] text-[#071838] dark:text-white sm:text-5xl xl:text-[42px]">오늘도,<br/>더 나은 내가 되는 중!</h1>
      <p className="dashboard-hero-description mt-4 max-w-sm text-sm leading-6 font-medium text-slate-600">{view.curriculum?.title}<br/>{view.isRestDay ? "오늘은 휴식일이에요. 다음 학습을 준비해 보세요." : "오늘 배정된 학습부터 차근차근 이어가요."}</p>
      <Link href={href} className="mt-6 inline-flex min-h-12 w-fit items-center gap-3 rounded-xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-lg shadow-blue-600/20 transition hover:-translate-y-0.5 hover:bg-blue-700">{view.nextSession ? "오늘 정규 학습 시작하기" : "오늘 정규 학습 확인하기"}<ArrowRight size={17}/></Link>
    </div>
  </section>;
}

function LearningPanel({ view }: { view: LoadedDashboardViewModel }) {
  return <section id="today-learning" className={`${card} min-w-0 p-5 sm:p-7`}>
    {view.today === null || view.today.kind === "NONE"
      ? <EmptyLearningContent/>
      : view.isRestDay
        ? <RestDay title={view.today.title} description={view.today.description}/>
        : <><TodayCard view={view}/><Sessions view={view}/></>}
  </section>;
}

function TodayCard({ view }: { view: LoadedDashboardViewModel }) {
  const today = view.today!;
  return <div>
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4"><h2 className="text-xl font-black tracking-tight text-[#0a1837]">오늘 배정된 정규 학습</h2><Link href="/roadmap" className="inline-flex min-h-10 items-center text-sm font-bold text-blue-600">과정 변경하기<ChevronRight size={16}/></Link></div>
    <div className="mt-5 grid gap-4 rounded-2xl bg-gradient-to-r from-blue-50/90 to-slate-50 p-4 dark:from-[#17243a] dark:to-[#151f2f] sm:grid-cols-[56px_minmax(0,1fr)_auto] sm:items-center">
      <span className="grid size-14 place-items-center rounded-2xl bg-white text-blue-600 shadow-sm"><BookOpen size={27}/></span>
      <div className="min-w-0"><p className="text-xs font-bold text-slate-500">{today.monthNo}단계 · {today.weekNo}주차 · {formatKoreanDate(today.date)}</p><h3 className="mt-1 truncate text-lg font-black text-[#0a1837]">{today.title}</h3><p className="mt-1 text-xs text-slate-500">{today.description || "오늘의 커리큘럼을 차근차근 완료해 보세요."}</p></div>
      {view.nextSession ? <Link href={`/workspace/learning/${view.nextSession.sessionId}`} className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-md shadow-blue-600/15 hover:bg-blue-700"><Play size={16} fill="currentColor"/> {view.nextSession.status === "IN_PROGRESS" ? "이어서 학습" : "학습하기"}</Link> : <span className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-emerald-50 px-5 text-sm font-bold text-emerald-700"><CheckCircle2 size={17}/>오늘 학습 완료</span>}
    </div>
  </div>;
}

function Sessions({ view }: { view: LoadedDashboardViewModel }) {
  const today = view.today!;
  return <ol className="mt-3 divide-y divide-slate-100">{today.sessions.map((session) => <li key={session.sessionId}>
    <Link href={`/workspace/learning/${session.sessionId}`} className="group flex min-h-14 items-center gap-3 py-2.5">
      <span className={`grid size-8 shrink-0 place-items-center rounded-full ${session.status === "IN_PROGRESS" ? "bg-blue-600 text-white" : session.status === "COMPLETED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>{session.status === "COMPLETED" ? <CheckCircle2 size={16}/> : <Play size={14} fill={session.status === "IN_PROGRESS" ? "currentColor" : "none"}/>}</span>
      <span className="min-w-0 flex-1"><span className="block truncate text-sm font-extrabold text-slate-900">{session.sequence}. {session.title}</span><span className="mt-0.5 block text-xs text-slate-500">{session.category}</span></span>
      <span className={`hidden rounded-full px-3 py-1 text-xs font-bold sm:inline ${session.status === "IN_PROGRESS" ? "bg-blue-50 text-blue-700" : session.status === "COMPLETED" ? "bg-emerald-50 text-emerald-700" : "bg-slate-50 text-slate-500"}`}>{statusLabel(session.status)}</span>
      <span className="w-10 text-right text-xs font-semibold text-slate-400">{session.estimatedMinutes}분</span><ChevronRight size={16} className="text-slate-300 group-hover:text-blue-600"/>
    </Link>
  </li>)}</ol>;
}

function ProgressCard({ view, progress }: { view: LoadedDashboardViewModel; progress: OverallProgress | null }) {
  const rate = Math.round(progress?.completionRate ?? view.curriculum.completionRate ?? 0);
  const completed = progress?.completedRequiredSessions ?? view.curriculum.completedRequiredSessions;
  const total = progress?.totalRequiredSessions ?? view.curriculum.totalRequiredSessions;
  return <section className={`${card} p-5 sm:p-6`}>
    <div className="flex items-center justify-between"><h2 className="font-black text-[#0a1837]">학습 진행 현황</h2><Link href="/progress" className="text-xs font-bold text-blue-600">자세히 보기</Link></div>
    <div className="mt-5 flex items-center gap-5"><div className="grid size-24 shrink-0 place-items-center rounded-full" style={{background:`conic-gradient(#2f7df6 ${rate * 3.6}deg,var(--progress-track) 0deg)`}}><div className="grid size-[72px] place-items-center rounded-full bg-white text-xl font-black text-[#0a1837]">{rate}%</div></div><p className="text-xs leading-5 text-slate-500"><span>배정된 학습 완료율</span><br/><strong className="text-sm text-[#0a1837]">{completed} / {total}</strong> 세션 완료</p></div>
    <div className="mt-5 grid grid-cols-3 divide-x divide-slate-100 border-t border-slate-100 pt-5"><MiniMetric label="완료 세션 예상 시간" value={progress ? formatMinutes(progress.completedEstimatedMinutes) : "평가 전"}/><MiniMetric label="연속 학습" value={progress ? `${progress.streakDays}일` : "평가 전"}/><MiniMetric label="오늘 진행" value={progressLabel(view.today?.progressRate ?? null)}/></div>
  </section>;
}

function MiniMetric({label,value}:{label:string;value:string}) { return <div className="px-2 text-center first:pl-0 last:pr-0"><p className="text-[10px] font-semibold text-slate-400">{label}</p><p className="mt-1 text-sm font-black text-[#0a1837]">{value}</p></div>; }

function CalendarCard({ view }: { view: DashboardViewModel }) {
  const rawDate = view.today?.date ?? new Date().toISOString().slice(0,10);
  const [year,month,day] = rawDate.split("-").map(Number);
  const first = new Date(year,month-1,1).getDay();
  const last = new Date(year,month,0).getDate();
  const cells = Array.from({length:first+last},(_,index)=>index<first?null:index-first+1);
  return <section className={`${card} p-5 sm:p-6`}><div className="flex items-center justify-between"><h2 className="text-lg font-black text-[#0a1837]">{year}년 {month}월</h2><Link href="/calendar" aria-label="학습 캘린더 전체 보기" className="grid size-9 place-items-center rounded-xl text-blue-600 hover:bg-blue-50"><CalendarDays size={18}/></Link></div><div className="mt-5 grid grid-cols-7 gap-y-2.5 text-center text-[11px] font-bold"><span className="text-rose-400">일</span>{["월","화","수","목","금"].map(v=><span key={v} className="text-slate-400">{v}</span>)}<span className="text-blue-500">토</span>{cells.map((value,index)=><span key={index} className={`mx-auto grid size-8 place-items-center rounded-full ${value===day?"bg-blue-600 text-white shadow-md shadow-blue-200":"text-slate-600"}`}>{value}</span>)}</div></section>;
}

function LearningInsight({categories,pending,failed}:{categories:CategoryProgress[];pending:boolean;failed:boolean}) {
  const ranked = categories.map(item=>({...item,score:item.masteryRate ?? item.progressRate})).filter(item=>item.score!==null).sort((a,b)=>(a.score ?? 0)-(b.score ?? 0));
  const weakest = ranked[0];
  return <section className={`${card} p-5 sm:p-7`} aria-labelledby="insight-title"><div className="flex flex-wrap items-end justify-between gap-3"><div><div className="flex items-center gap-2"><h2 id="insight-title" className="text-lg font-black text-[#0a1837]">맞춤 학습 인사이트</h2><Sparkles size={17} className="text-blue-600"/></div><p className="mt-1 text-xs text-slate-500">저장된 학습 데이터를 바탕으로 현재 성장 지점을 확인하세요.</p></div><Link href="/progress" className="inline-flex min-h-10 items-center gap-1 text-xs font-extrabold text-blue-600">학습 통계 전체 보기<ChevronRight size={15}/></Link></div>
    <div className="mt-5 grid gap-4 lg:grid-cols-[1.15fr_0.9fr_1fr]">
      <div className="rounded-2xl bg-slate-50 p-5"><p className="text-xs font-extrabold text-slate-600">카테고리별 성장</p>{pending ? <InsightMessage text="학습 데이터를 불러오고 있어요."/> : failed ? <InsightMessage text="인사이트를 불러오지 못했어요. 통계 화면에서 다시 확인해 주세요."/> : categories.length === 0 ? <InsightMessage text="학습 기록이 쌓이면 영역별 성장이 표시됩니다."/> : <div className="mt-4 grid gap-3">{categories.slice(0,4).map(item=><CategoryBar key={item.category} item={item}/>)}</div>}</div>
      <div className={`rounded-2xl border p-5 dark:border-slate-700 dark:bg-[#182235] ${weakest?"border-amber-200 bg-amber-50/60":"border-blue-100 bg-blue-50/60"}`}><div className="flex items-center gap-2">{weakest?<AlertTriangle size={18} className="text-amber-500"/>:<Target size={18} className="text-blue-600"/>}<p className="text-sm font-black text-[#0a1837]">{weakest?`${weakest.category} 영역을 보완해 보세요`:"다음 학습을 시작해 보세요"}</p></div><p className="mt-3 text-xs leading-5 text-slate-600">{weakest?"현재 기록에서 상대적으로 낮은 영역입니다. 관련 세션과 문제를 먼저 복습하면 좋아요.":"영역별 기록이 생기면 우선 학습할 항목을 안내해 드립니다."}</p><Link href={weakest?"/review":"/roadmap"} className="mt-5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl border border-blue-500 bg-white text-sm font-extrabold text-blue-700">맞춤 학습 추천 보기<ArrowRight size={16}/></Link></div>
      <div className="rounded-2xl bg-gradient-to-br from-blue-50 to-white p-5 dark:from-[#182235] dark:to-[#182235]"><div className="flex items-center justify-between"><p className="text-xs font-extrabold text-slate-600">학습 데이터 요약</p><TrendingUp size={18} className="text-blue-600"/></div><div className="mt-5 grid grid-cols-2 gap-3"><DataPoint label="측정 영역" value={`${categories.length}개`}/><DataPoint label="평가 가능" value={`${ranked.length}개`}/><DataPoint label="완료 세션" value={`${categories.reduce((sum,item)=>sum+item.completedSessionCount,0)}개`}/><DataPoint label="전체 세션" value={`${categories.reduce((sum,item)=>sum+item.totalSessionCount,0)}개`}/></div></div>
    </div>
  </section>;
}

function CategoryBar({item}:{item:CategoryProgress}) { const value=item.masteryRate ?? item.progressRate; return <div><div className="flex items-center justify-between text-[11px]"><span className="font-bold text-slate-600">{item.category}</span><strong className="text-blue-700">{value===null?"산정 전":`${Math.round(value)}%`}</strong></div><div className="mt-1.5 h-2 overflow-hidden rounded-full bg-white"><div className="h-full rounded-full bg-blue-500" style={{width:`${value===null?0:Math.max(4,Math.min(100,value))}%`}}/></div></div>; }
function InsightMessage({text}:{text:string}) { return <p className="mt-5 text-xs leading-5 text-slate-500">{text}</p>; }
function DataPoint({label,value}:{label:string;value:string}) { return <div className="rounded-xl bg-white p-3 shadow-sm"><p className="text-[10px] font-semibold text-slate-400">{label}</p><p className="mt-1 text-base font-black text-[#0a1837]">{value}</p></div>; }

function RecentActivity({sessions,nextHref}:{sessions:RecentSessionDto[];nextHref:string}) {
  return <section className={`${card} flex flex-col p-5 sm:p-7`}><div className="flex items-center justify-between"><h2 className="text-lg font-black text-[#0a1837]">최근 활동</h2><Link href="/progress" className="text-xs font-bold text-blue-600">전체보기</Link></div>{sessions.length>0&&<ul className="mt-4 divide-y divide-slate-100">{sessions.map(session=><li key={session.sessionId}><Link href={`/workspace/learning/${session.sessionId}`} className="flex min-h-14 items-center gap-3 py-2"><span className={`grid size-8 shrink-0 place-items-center rounded-full ${session.status==="COMPLETED"?"bg-emerald-100 text-emerald-600":"bg-blue-100 text-blue-600"}`}>{session.status==="COMPLETED"?<CheckCircle2 size={16}/>:<Play size={14}/>}</span><span className="min-w-0 flex-1 truncate"><span className="block truncate text-sm font-bold text-slate-700">{session.title}</span><span className="block text-xs text-slate-400">{formatKoreanDate(session.scheduledDate)}</span></span><span className="text-xs font-semibold text-slate-400">{statusLabel(session.status)}</span></Link></li>)}</ul>}{sessions.length<3&&<ActivityPrompt href={nextHref} empty={sessions.length===0}/>}</section>;
}

function ActivityPrompt({href,empty}:{href:string;empty:boolean}) {return <div className="mt-4 grid flex-1 grid-cols-[96px_minmax(0,1fr)] items-center gap-4 overflow-hidden rounded-2xl border border-blue-100 bg-gradient-to-br from-blue-50/90 to-white px-4 py-3 dark:border-blue-900/80 dark:from-[#17243a] dark:to-[#131c2d] sm:grid-cols-[150px_minmax(0,1fr)] sm:gap-6 sm:px-6"><div className="relative h-32 w-full sm:h-44"><Image src="/assets/dashboard/recent-activity-duck.png" alt="체크리스트와 연필을 든 학습 오리 캐릭터" fill sizes="(min-width:640px) 150px, 96px" className="object-contain drop-shadow-[0_16px_20px_rgba(245,158,11,0.2)]"/></div><div className="min-w-0"><p className="text-sm font-black text-[#0a1837] sm:text-base">{empty?"첫 활동을 만들어 볼까요?":"최근 활동을 더 채워볼까요?"}</p><p className="mt-1 text-xs leading-5 text-slate-500">{empty?"오늘의 학습을 시작하면 실제 기록이 차곡차곡 쌓여요.":"다음 학습을 이어가면 진행 기록과 완료 활동을 한눈에 확인할 수 있어요."}</p><Link href={href} className="mt-3 inline-flex min-h-10 items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 text-xs font-extrabold text-white shadow-lg shadow-blue-200/50 hover:bg-blue-700 dark:shadow-none sm:text-sm">{empty?"첫 학습 시작하기":"학습 이어가기"}<ArrowRight size={16}/></Link></div></div>;}

const quickLinks:{href:string;label:string;detail:string;image:string;tone:string}[]=[
  {href:"/practice",label:"문제 풀기",detail:"지금 바로 연습해요",image:"/assets/quick-menu/practice.png",tone:"from-blue-50 to-blue-100/55"},
  {href:"/tests",label:"시험 보기",detail:"실력을 점검해요",image:"/assets/quick-menu/exam.png",tone:"from-violet-50 to-violet-100/55"},
  {href:"/review",label:"오답노트",detail:"약점을 보완해요",image:"/assets/quick-menu/review.png",tone:"from-rose-50 to-rose-100/55"},
  {href:"/progress",label:"학습 통계",detail:"성장을 확인해요",image:"/assets/quick-menu/progress.png",tone:"from-emerald-50 to-emerald-100/55"},
];
function QuickMenu(){return <section className={`${card} p-5 sm:p-6`}><h2 className="text-lg font-black text-[#0a1837]">빠른 메뉴</h2><div className="mt-4 grid grid-cols-2 gap-3">{quickLinks.map(({href,label,detail,image,tone})=><Link key={href} href={href} className={`group flex min-h-36 flex-col items-center overflow-hidden rounded-2xl bg-gradient-to-br p-4 text-center transition hover:-translate-y-0.5 dark:from-[#182235] dark:to-[#182235] ${tone}`}><div className="relative size-16 transition-transform duration-200 group-hover:scale-105"><Image src={image} alt="" fill unoptimized sizes="64px" className="object-contain drop-shadow-[0_8px_10px_rgba(15,23,42,0.12)]"/></div><p className="mt-2 text-sm font-black text-slate-900">{label}</p><p className="mt-1 text-[11px] text-slate-500">{detail}</p></Link>)}</div></section>;}

function CareerGoal({months,completionRate}:{months:RoadmapMonthDto[];completionRate:number|null}) {
  return <section className={`${card} p-5 sm:p-7`}><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-black text-[#0a1837]">나의 학습 로드맵</h2><p className="mt-1 text-xs text-slate-500">배정된 단계와 실제 완료 기록을 확인해요. 전체 {progressLabel(completionRate)}</p></div><Link href="/roadmap" className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-blue-500 px-4 text-xs font-extrabold text-blue-700">학습 로드맵 보기<ArrowRight size={15}/></Link></div><div className="mt-5 grid gap-3 sm:grid-cols-2">{months.map(month=><Link key={month.monthNo} href="/roadmap" className="rounded-2xl border border-slate-200 p-4 text-sm hover:border-blue-400"><span className="text-xs font-bold text-blue-700">{month.monthNo}단계</span><p className="mt-1 font-extrabold text-slate-950">{month.title}</p><p className="mt-2 text-xs text-slate-500">필수 세션 {month.completedSessionCount} / {month.requiredSessionCount}개 완료 · {progressLabel(month.progressRate)}</p></Link>)}</div></section>;
}

function GrowthRecord({progress,pending,failed}:{progress:OverallProgress|null;pending:boolean;failed:boolean}) {return <section className={`${card} p-5 sm:p-7`}><div><h2 className="text-lg font-black text-[#0a1837]">학습 성장 기록</h2><p className="mt-1 text-xs text-slate-500">저장된 학습 결과를 기준으로 꾸준한 변화를 확인하세요.</p></div><div className="mt-5 grid gap-3 sm:grid-cols-3"><GrowthMetric icon={Clock3} label="완료 세션 예상 시간" value={progress?formatMinutes(progress.completedEstimatedMinutes):pending?"불러오는 중":"산정 전"} tone="bg-blue-50 text-blue-600"/><GrowthMetric icon={BookOpen} label="완료한 세션" value={progress?`${progress.completedRequiredSessions}개`:failed?"확인 필요":"산정 전"} tone="bg-violet-50 text-violet-600"/><GrowthMetric icon={Flame} label="연속 학습" value={progress?`${progress.streakDays}일`:"산정 전"} tone="bg-orange-50 text-orange-500"/></div></section>;}
function GrowthMetric({icon:Icon,label,value,tone}:{icon:LucideIcon;label:string;value:string;tone:string}){return <div className="flex items-center gap-4 rounded-2xl border border-slate-100 p-4"><span className={`grid size-11 place-items-center rounded-xl ${tone}`}><Icon size={21}/></span><div><p className="text-xs font-semibold text-slate-400">{label}</p><p className="mt-1 text-xl font-black text-[#0a1837]">{value}</p></div></div>;}

let clientQuoteIndex: number | null = null;
const subscribeToPageLoad = () => () => undefined;
const getServerQuoteIndex = () => 0;
function getClientQuoteIndex(){
  clientQuoteIndex ??= Math.floor(Math.random()*savedDeveloperQuotes.length);
  return clientQuoteIndex;
}

function Encouragement(){
  const quoteIndex=useSyncExternalStore(subscribeToPageLoad,getClientQuoteIndex,getServerQuoteIndex);
  const online=useQuery({
    queryKey:["daily-motivation-quote"],
    queryFn:async()=>{
      const response=await fetch("/daily-quote",{cache:"no-store"});
      if(!response.ok)throw new Error("Online quote unavailable");
      return await response.json() as MotivationalQuote;
    },
    retry:false,
    staleTime:Infinity,
    refetchOnWindowFocus:false,
  });
  const item=online.data??savedDeveloperQuotes[quoteIndex];
  return <section aria-label="오늘의 개발자 명언" data-quote-source={item.source} className="flex min-h-[116px] items-center gap-4 rounded-[24px] border border-blue-100 bg-gradient-to-br from-blue-50 to-white p-5 dark:border-slate-700 dark:from-[#17243a] dark:to-[#111827]">
    <div className="relative size-14 shrink-0 overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-blue-100 dark:ring-slate-700"><Image src={item.image} alt={`${item.person} 얼굴`} fill unoptimized={item.source==="online"} sizes="56px" className="object-cover"/></div>
    <figure className="min-w-0"><blockquote className="text-sm font-black leading-5 text-[#0a1837]">“{item.quote}”</blockquote><figcaption className="mt-2 text-[11px] font-bold text-slate-500">— {item.person}</figcaption></figure>
  </section>;
}
function RestDay({title,description}:{title:string;description:string}){return <div className="rounded-2xl bg-blue-50 p-7"><CalendarDays className="text-blue-600"/><h2 className="mt-4 text-xl font-extrabold text-slate-950">{title}</h2><p className="mt-2 text-sm leading-6 text-slate-600">{description||"오늘은 쉬어 가는 날이에요. 다음 학습을 위해 충분히 충전하세요."}</p></div>;}
function EmptyLearningContent(){return <><div className="flex items-center justify-between border-b border-slate-100 pb-4"><h2 className="text-xl font-black text-[#0a1837]">오늘의 학습</h2><span className="text-sm font-bold text-slate-400">일정 없음</span></div><div className="py-7"><span className="grid size-14 place-items-center rounded-2xl bg-blue-50 text-blue-600"><BookOpen size={25}/></span><h3 className="mt-4 text-xl font-extrabold text-slate-950">오늘 배정된 학습이 없어요</h3><p className="mt-2 text-sm text-slate-600">로드맵에서 다음 학습 일정을 확인해 주세요.</p></div></>;}
function formatMinutes(minutes:number){return `${Math.floor(minutes/60)}시간 ${minutes%60}분`;}

function EmptyDashboard(){return <div className="grid gap-6 pb-8 xl:gap-7"><div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]"><EmptyHero/><CalendarCard view={{curriculum:null,today:null,nextSession:null,isRestDay:false}}/></div><div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]"><section className={`${card} p-5 sm:p-7`}><div className="flex items-center justify-between border-b border-slate-100 pb-4"><h2 className="text-xl font-black text-[#0a1837]">오늘의 학습</h2><span className="text-sm font-bold text-slate-400">과정 설정 전</span></div><span className="mt-6 grid size-14 place-items-center rounded-2xl bg-blue-50 text-blue-600"><BookOpen size={25}/></span><h3 className="mt-4 text-2xl font-extrabold text-slate-950">아직 배정된 과정이 없어요</h3><p className="mt-2 max-w-lg text-sm leading-6 text-slate-600">과정을 선택하면 오늘 학습과 세션 목록이 이 자리에 표시됩니다.</p><span aria-disabled="true" className="mt-6 inline-flex min-h-11 items-center rounded-xl bg-slate-100 px-5 text-sm font-bold text-slate-400">과정 배정 후 시작 가능</span></section><aside className="grid content-start gap-5"><EmptyProgress/><Encouragement/></aside></div><LearningInsight categories={[]} pending={false} failed={false}/><div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(330px,0.72fr)]"><section className={`${card} flex flex-col p-5 sm:p-7`}><h2 className="text-lg font-black text-[#0a1837]">최근 활동</h2><ActivityPrompt href="/roadmap" empty/></section><QuickMenu/></div><CareerGoal months={[]} completionRate={0}/><GrowthRecord progress={null} pending={false} failed={false}/></div>;}
function EmptyHero(){return <section className="relative min-h-[310px] overflow-hidden rounded-[26px] border border-blue-100 bg-[#eaf4ff] shadow-[0_18px_50px_rgba(37,99,235,0.08)] dark:bg-[#17243a]"><Image src={currentMonthlyHero()} alt="책상에서 학습 계획을 세우는 호범 캐릭터" fill priority unoptimized sizes="(min-width:1280px) 70vw, 100vw" className="object-cover object-[64%_center]"/><div className="absolute inset-0 bg-gradient-to-r from-white via-white/90 to-transparent dark:from-[#08111f]/95 dark:via-[#08111f]/75 dark:to-[#08111f]/20 sm:via-white/58 dark:sm:via-[#08111f]/60"/><div className="relative flex min-h-[310px] max-w-xl flex-col justify-center p-7 sm:p-9"><span className="mb-4 inline-flex w-fit items-center gap-2 text-xs font-extrabold text-blue-600"><Sparkles size={14}/> 첫 커리어 여정을 준비해요</span><h1 className="text-3xl leading-[1.15] font-black tracking-[-0.045em] text-[#071838] sm:text-5xl xl:text-[42px]">오늘도,<br/>더 나은 내가 되는 중!</h1><p className="dashboard-hero-description mt-4 text-sm leading-6 text-slate-600">나에게 맞는 과정을 고르면<br/>오늘의 학습과 로드맵이 채워져요.</p><Link href="/onboarding" className="mt-6 inline-flex min-h-12 w-fit items-center gap-3 rounded-xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-lg shadow-blue-600/20">첫 학습 계획 만들기<ArrowRight size={17}/></Link></div></section>;}
function EmptyProgress(){return <section className={`${card} p-5 sm:p-6`}><h2 className="font-black text-[#0a1837]">학습 진행 현황</h2><div className="mt-5 flex items-center gap-5"><div className="grid size-24 place-items-center rounded-full border-[11px] border-slate-100 text-xl font-black text-slate-400">0%</div><p className="text-xs leading-5 text-slate-500">과정을 시작하면<br/>진행률이 표시돼요.</p></div></section>;}
