"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, Coffee, ListChecks } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { StatePanel } from "@/components/ui/state-panel";
import { IllustrationIcon } from "@/components/ui/illustration-icon";
import { getExtraHistory, modeLabels, type ExtraEntry } from "@/features/extra-learning/api";
import { decodeLearningDays, type LearningDayDto } from "@/features/today-learning/contract";
import { cn } from "@/lib/utils";

const weekdays = ["일", "월", "화", "수", "목", "금", "토"];
const dateKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const fromKey = (year: number, month: number) => dateKey(new Date(year, month, 1 - new Date(year, month, 1).getDay()));
const toKey = (year: number, month: number) => { const first = new Date(year, month, 1); const start = new Date(year, month, 1 - first.getDay()); return dateKey(new Date(start.getFullYear(), start.getMonth(), start.getDate() + 41)); };
const seoulToday = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());

function monthQuery(year: number, month: number) {
  const from = fromKey(year, month); const to = toKey(year, month);
  return { queryKey: ["calendar", from, to], queryFn: async () => (await api.request({ path: `/api/v1/learning-days?from=${from}&to=${to}`, decode: value => decodeEnvelope(value, decodeLearningDays).data })).data };
}

export function LearningCalendarScreen() {
  const today = seoulToday();
  const now = new Date(`${today}T12:00:00`);
  const [cursor, setCursor] = useState({ year: now.getFullYear(), month: now.getMonth() });
  const [selected, setSelected] = useState(today);
  const query = useQuery(monthQuery(cursor.year, cursor.month));
  const extra = useQuery({ queryKey: ["extra-learning"], queryFn: getExtraHistory });
  const byDate = useMemo(() => new Map((query.data ?? []).map(day => [day.date, day])), [query.data]);
  const extraByDate = useMemo(() => {
    const result = new Map<string, ExtraEntry[]>();
    for (const entry of extra.data ?? []) {
      if (entry.courseStatus === "CANCELED" || entry.mode === "AHEAD") continue;
      const entries = result.get(entry.scheduledDate) ?? [];
      entries.push(entry);
      result.set(entry.scheduledDate, entries);
    }
    return result;
  }, [extra.data]);
  const first = new Date(cursor.year, cursor.month, 1); const gridStart = new Date(cursor.year, cursor.month, 1 - first.getDay());
  const cells = Array.from({ length: 42 }, (_, index) => { const date = new Date(gridStart); date.setDate(gridStart.getDate() + index); return { key: dateKey(date), day: date.getDate(), current: date.getMonth() === cursor.month }; });
  const selectedDay = byDate.get(selected);
  const move = (delta: number) => { const next = new Date(cursor.year, cursor.month + delta, 1); setCursor({ year: next.getFullYear(), month: next.getMonth() }); setSelected(dateKey(next)); };
  const resetToday = () => { setCursor({ year: now.getFullYear(), month: now.getMonth() }); setSelected(today); };

  return <div className="space-y-6 pb-4">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex items-center gap-4"><IllustrationIcon name="calendar" size="lg"/><div><p className="text-sm font-bold text-blue-700">내 학습 리듬</p><h1 className="mt-1 text-3xl font-extrabold tracking-[-0.035em] text-slate-950">학습 캘린더</h1><p className="mt-2 text-sm leading-6 text-slate-600">날짜에 배정된 학습과 내가 선택해 공부한 주제를 함께 확인하세요. 다른 분야 학습은 해당 날짜의 정규 진도를 완료하지 않아도 시작할 수 있어요.</p></div></div>
      <Link href="/roadmap" className="inline-flex min-h-11 w-fit items-center gap-2 rounded-xl border border-blue-200 bg-white px-4 text-sm font-bold text-blue-700 shadow-sm hover:border-blue-400"><ListChecks size={17}/>학습 로드맵</Link>
    </header>

    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
      <section className="app-card overflow-hidden" aria-label={`${cursor.year}년 ${cursor.month + 1}월 학습 캘린더`}>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-4 sm:px-6">
          <div className="flex items-center gap-2"><button type="button" aria-label="이전 달" onClick={() => move(-1)} className="grid size-11 place-items-center rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50"><ChevronLeft size={20}/></button><button type="button" aria-label="다음 달" onClick={() => move(1)} className="grid size-11 place-items-center rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50"><ChevronRight size={20}/></button><button type="button" onClick={resetToday} className="min-h-11 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-600 hover:bg-slate-50">오늘</button></div>
          <h2 className="order-first w-full text-xl font-extrabold tracking-tight text-slate-950 sm:order-none sm:w-auto">{cursor.year}년 {cursor.month + 1}월</h2>
          <div className="flex flex-wrap items-center gap-3 text-xs font-bold text-slate-500"><Legend color="bg-blue-500" label="배정"/><Legend color="bg-emerald-500" label="완료"/><Legend color="bg-violet-500" label="휴식"/><Legend color="bg-amber-500" label="선택 학습"/></div>
        </div>
        {query.isError ? <div className="p-5"><StatePanel state="error" onRefresh={() => void query.refetch()}/></div> : <>
          <div className="grid grid-cols-7 border-b border-slate-100 bg-slate-50/70 px-2 dark:bg-[#111827] sm:px-4">{weekdays.map((day, index) => <div key={day} className={cn("py-3 text-center text-xs font-bold", index === 0 ? "text-rose-500" : index === 6 ? "text-blue-600" : "text-slate-500")}>{day}</div>)}</div>
          <div className="grid grid-cols-7 p-2 sm:p-4">{cells.map(cell => <DayCell key={cell.key} cell={cell} value={byDate.get(cell.key)} extraCount={extraByDate.get(cell.key)?.length ?? 0} selected={selected === cell.key} today={today === cell.key} onSelect={() => setSelected(cell.key)}/>)}</div>
          {query.isPending && <p className="px-6 pb-5 text-sm font-medium text-slate-500">일정을 불러오는 중이에요.</p>}
        </>}
      </section>
      <Agenda date={selected} value={selectedDay} extra={extraByDate.get(selected) ?? []} extraError={extra.isError}/>
    </div>
  </div>;
}

function Legend({ color, label }: { color: string; label: string }) { return <span className="inline-flex items-center gap-1.5"><span className={cn("size-2.5 rounded-full", color)}/>{label}</span>; }

function DayCell({ cell, value, extraCount, selected, today, onSelect }: { cell: { key: string; day: number; current: boolean }; value?: LearningDayDto; extraCount: number; selected: boolean; today: boolean; onSelect: () => void }) {
  const tone = value?.kind === "REST" ? "bg-violet-500" : value?.status === "COMPLETED" ? "bg-emerald-500" : value?.kind === "STUDY" ? "bg-blue-500" : null;
  return <button type="button" onClick={onSelect} aria-label={`${cell.key}${value ? `, ${value.title}` : ", 일정 없음"}${extraCount ? `, 선택 학습 ${extraCount}건` : ""}`} aria-pressed={selected} className={cn("group relative flex min-h-[62px] flex-col items-center rounded-xl px-1 py-2 transition sm:min-h-24 sm:items-start sm:p-3", selected ? "bg-blue-600 text-white shadow-lg shadow-blue-200" : "hover:bg-blue-50", !cell.current && !selected && "text-slate-300")}>
    <span className={cn("grid size-7 place-items-center rounded-full text-sm font-bold", today && !selected && "bg-amber-100 text-amber-800", !today && !selected && cell.current && "text-slate-700")}>{cell.day}</span>
    {tone && <><span className={cn("mt-1 size-2 rounded-full sm:hidden", selected ? "bg-white" : tone)}/><span className={cn("mt-auto hidden max-w-full truncate rounded-lg px-2 py-1 text-[11px] font-bold sm:block", selected ? "bg-white/15 text-white" : value?.kind === "REST" ? "bg-violet-50 text-violet-700" : value?.status === "COMPLETED" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700")}>{value?.kind === "REST" ? "휴식" : value?.title}</span></>}
    {extraCount > 0 && <span className={cn("mt-1 size-2 rounded-full sm:mt-1 sm:h-auto sm:w-auto sm:px-2 sm:py-1 sm:text-[11px] sm:font-bold", selected ? "bg-white text-amber-700" : "bg-amber-500 text-white")}><span className="sr-only sm:not-sr-only">선택 {extraCount}건</span></span>}
  </button>;
}

function Agenda({ date, value, extra, extraError }: { date: string; value?: LearningDayDto; extra: ExtraEntry[]; extraError: boolean }) {
  const formatted = new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" }).format(new Date(`${date}T12:00:00`));
  return <aside className="app-card h-fit p-5 sm:p-6 xl:sticky xl:top-24"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-bold text-blue-700">선택한 날짜</p><h2 className="mt-1 text-xl font-extrabold text-slate-950">{formatted}</h2></div><span className={cn("icon-tile", value?.kind === "REST" ? "bg-violet-50 text-violet-700 dark:text-violet-300" : "bg-blue-50 text-blue-700")}>{value?.kind === "REST" ? <Coffee size={21}/> : <CalendarDays size={21}/>}</span></div>
    {!value || value.kind === "NONE" ? <div className="mt-8 rounded-2xl bg-slate-50 p-5"><p className="font-bold text-slate-800">배정된 학습이 없어요</p><p className="mt-2 text-sm leading-6 text-slate-500">원하는 분야를 선택해 자유롭게 공부할 수 있어요.</p></div> : value.kind === "REST" ? <div className="mt-8 rounded-2xl border border-violet-100 bg-violet-50 p-5 dark:border-violet-400/30"><p className="font-extrabold text-violet-950 dark:text-violet-100">정규 과정 휴식일</p><p className="mt-2 text-sm leading-6 text-violet-700 dark:text-violet-200">{value.description || "정기 휴식일이에요."} 선택 학습은 자유롭게 진행할 수 있어요.</p></div> : <div className="mt-6"><div className="flex items-center justify-between gap-3"><h3 className="font-extrabold text-slate-900">배정 · {value.title}</h3><span className={cn("rounded-full px-2.5 py-1 text-xs font-bold", value.status === "COMPLETED" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700")}>{value.status === "COMPLETED" ? "완료" : `${value.completedSessionCount}/${value.totalSessionCount}`}</span></div><p className="mt-2 flex items-center gap-2 text-sm text-slate-500"><Clock3 size={15}/>예상 {value.estimatedMinutes}분</p><div className="mt-5 grid gap-2">{value.sessions.map(session => <Link key={session.sessionId} href={`/workspace/learning/${session.sessionId}`} className="flex min-h-14 items-center gap-3 rounded-2xl border border-slate-200 px-4 hover:border-blue-300 hover:bg-blue-50"><span className={cn("grid size-8 shrink-0 place-items-center rounded-full", session.status === "COMPLETED" ? "bg-emerald-100 text-emerald-700" : "bg-blue-100 text-blue-700")}>{session.status === "COMPLETED" ? <CheckCircle2 size={16}/> : session.sequence}</span><span className="min-w-0"><strong className="block truncate text-sm text-slate-900">{session.title}</strong><span className="text-xs text-slate-500">{session.category} · {session.estimatedMinutes}분</span></span></Link>)}</div></div>}
    {extra.length > 0 && <div className="mt-6"><h3 className="font-extrabold text-slate-900">내가 선택한 학습 · {extra.length}건</h3><div className="mt-3 grid gap-2">{extra.map(entry => <Link key={entry.sessionId} href={`/workspace/learning/${entry.sessionId}`} className="rounded-2xl border border-amber-200 bg-amber-50/50 px-4 py-3 hover:bg-amber-50"><strong className="block text-sm text-slate-900">{entry.title}</strong><span className="mt-1 block text-xs text-slate-600">{modeLabels[entry.mode]} · {entry.category} · {entry.status === "COMPLETED" ? "완료" : "학습 중"}</span></Link>)}</div></div>}
    {extraError && <p role="status" className="mt-4 text-xs text-amber-800">선택 학습 기록을 불러오지 못했어요. 더 공부하기에서 기록을 확인할 수 있어요.</p>}
    <Link href="/learning/extra" className="mt-6 inline-flex min-h-11 items-center rounded-xl bg-blue-600 px-4 text-sm font-bold text-white hover:bg-blue-700">지금 다른 주제 공부하기</Link>
  </aside>;
}
