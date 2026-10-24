"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CheckCheck, ExternalLink, Eye, EyeOff, NotebookPen, Plus, RotateCcw } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import {
  addRetry, categories, categoryLabel, codingKeys, codingLinksQuery, codingLogsQuery, codingOverviewQuery, createLink, createLog, reasonLabel, resultLabel, results,
  setLinkMark, updateCodingTarget, type Category, type CodingOverview, type LogInput, type PracticeLog, type ProblemLink, type Result,
} from "./api";

const panel = "rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7";
const input = "min-h-10 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-[#151f2f] dark:text-slate-100";
const resultTone: Record<Result, string> = { SUCCESS: "bg-emerald-100 text-emerald-700", FAILED: "bg-rose-100 text-rose-700", TIMEOUT: "bg-amber-100 text-amber-700", SOLUTION_VIEWED: "bg-violet-100 text-violet-700" };
const today = () => new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);
const problemKey = (log: PracticeLog) => log.problemLinkId ? `link:${log.problemLinkId}` : log.problemUrl ? `url:${log.problemUrl.toLowerCase()}` : `title:${log.problemTitle.toLowerCase()}`;

export function CodingScreen() {
  const overview = useQuery(codingOverviewQuery);
  const links = useQuery(codingLinksQuery);
  const logs = useQuery(codingLogsQuery);
  const [form, setForm] = useState<{ link: ProblemLink | null } | null>(null);
  if (overview.isPending || links.isPending || logs.isPending) return <StatePanel state="loading"/>;
  if (overview.isError || links.isError || logs.isError) return <StatePanel state="error" onRefresh={() => { void overview.refetch(); void links.refetch(); void logs.refetch(); }}/>;
  const openForm = (link: ProblemLink | null) => { setForm({ link }); requestAnimationFrame(() => document.getElementById("coding-log-form")?.scrollIntoView({ behavior: "smooth", block: "start" })); };
  return <div className="mx-auto grid max-w-5xl gap-5 pb-10">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">코딩 테스트</p>
        <h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">무엇을 풀지 고르고, 풀고 나서 기록해요</h1>
        <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">문제는 프로그래머스 같은 사이트에서 풀어요. 여기에는 문제 이름과 링크만 저장하고, 문제 본문은 가져오지 않아요.</p>
      </div>
      <TargetPicker target={overview.data.weeklyTarget}/>
    </header>
    {overview.data.weeklyTarget === 0
      ? <section className={panel}><p className="text-sm text-slate-600 dark:text-slate-300">주간 목표가 0이라 코딩 테스트 트랙이 꺼져 있어요. 목표를 1 이상으로 바꾸면 추천과 메뉴가 다시 나타나요.</p></section>
      : <ThisWeek overview={overview.data} onLog={openForm}/>}
    {form && <LogForm key={form.link?.linkId ?? "manual"} link={form.link} links={links.data} onDone={() => setForm(null)}/>}
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1.6fr)_minmax(280px,1fr)]">
      <LogList logs={logs.data} onAdd={() => openForm(null)}/>
      <div className="grid content-start gap-5"><RetrySchedule logs={logs.data}/><Stats overview={overview.data}/></div>
    </div>
    <LinkManager links={links.data}/>
  </div>;
}

function TargetPicker({ target }: { target: number }) {
  const client = useQueryClient();
  const save = useMutation({ mutationFn: updateCodingTarget, onSuccess: () => client.invalidateQueries({ queryKey: codingKeys.all }) });
  return <label className="grid gap-1 text-xs font-bold text-slate-500">주간 목표
    <select className={`${input} w-40`} value={target} disabled={save.isPending} onChange={event => save.mutate(Number(event.target.value))}>
      {[0, 1, 2, 3, 4, 5, 6, 7].map(value => <option key={value} value={value}>{value === 0 ? "끄기 (0)" : `주 ${value}문제`}</option>)}
    </select>
    {save.error && <span role="alert" className="text-rose-600">{save.error.message}</span>}
  </label>;
}

function ThisWeek({ overview, onLog }: { overview: CodingOverview; onLog: (link: ProblemLink | null) => void }) {
  const rate = Math.min(100, Math.round(overview.weekDone / overview.weeklyTarget * 100));
  return <section className={panel}>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">이번 주 추천 문제</h2><p className="mt-1 text-sm text-slate-500">{overview.weekStart} 주 · 다시 풀 문제 → 약한 유형 → 안 풀어 본 유형 순서예요.</p></div>
      <div className="flex items-center gap-3"><div className="h-2 w-32 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className="h-full rounded-full bg-blue-600" style={{ width: `${rate}%` }}/></div><strong className="text-sm text-[#0a1837] dark:text-slate-100">{overview.weekDone} / {overview.weeklyTarget}</strong></div>
    </div>
    {overview.recommendations.length === 0
      ? <p className="mt-4 rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">이번 주 목표를 채웠어요. 더 풀고 싶으면 아래 목록에서 골라 기록하면 돼요.</p>
      : <ul className="mt-4 grid gap-3 sm:grid-cols-2">{overview.recommendations.map((item, index) => {
        const title = item.link?.title ?? item.retryOf?.problemTitle ?? "";
        const url = item.link?.url ?? item.retryOf?.problemUrl;
        const category = item.link?.category ?? item.retryOf?.category;
        return <li key={index} className="flex flex-col gap-3 rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
          <div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-black text-blue-700 dark:bg-blue-500/15 dark:text-blue-300">{reasonLabel[item.reason]}</span>{category && <span className="text-xs font-semibold text-slate-500">{categoryLabel[category]}</span>}{item.link?.level !== null && item.link?.level !== undefined && <span className="text-xs font-semibold text-slate-400">Lv. {item.link.level}</span>}</div>
          <strong className="text-sm text-slate-900 dark:text-slate-100">{title}</strong>
          {item.retryOf && <p className="text-xs text-slate-500">지난 기록: {item.retryOf.solvedOn} · {resultLabel[item.retryOf.result]}{item.retryOf.stuckNote && ` · 막힌 곳: ${item.retryOf.stuckNote}`}</p>}
          <div className="mt-auto flex flex-wrap gap-2">
            {url && <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-blue-600 px-4 text-sm font-semibold text-white hover:bg-blue-700">문제 열기<ExternalLink size={15}/></a>}
            <Button variant="outline" onClick={() => onLog(item.link)}><NotebookPen size={16}/>기록하기</Button>
          </div>
        </li>;
      })}</ul>}
  </section>;
}

function LogForm({ link, links, onDone }: { link: ProblemLink | null; links: ProblemLink[]; onDone: () => void }) {
  const client = useQueryClient();
  const [value, setValue] = useState<LogInput>({ problemLinkId: link?.linkId ?? null, title: "", url: "", category: link?.category ?? "", language: "Java", result: "SUCCESS", minutes: null, stuckNote: "", note: "", solvedOn: today() });
  const save = useMutation({ mutationFn: () => createLog(value), onSuccess: async () => { await client.invalidateQueries({ queryKey: codingKeys.all }); onDone(); } });
  const chosen = links.find(item => item.linkId === value.problemLinkId) ?? null;
  const valid = (chosen !== null || value.title.trim() !== "" && value.category !== "") && (!value.url || /^https:\/\/\S+$/.test(value.url.trim()));
  return <section id="coding-log-form" className={`${panel} border-blue-200 dark:border-blue-900`}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">풀이 기록</h2>
    <div className="mt-4 grid gap-3 sm:grid-cols-2">
      <label className="grid gap-1 text-xs font-bold text-slate-500 sm:col-span-2">문제
        <select className={input} value={value.problemLinkId ?? ""} onChange={event => { const next = links.find(item => item.linkId === event.target.value) ?? null; setValue({ ...value, problemLinkId: next?.linkId ?? null, category: next?.category ?? value.category }); }}>
          <option value="">직접 입력</option>
          {categories.map(([code, label]) => { const group = links.filter(item => item.category === code && (!item.hidden || item.linkId === value.problemLinkId)); return group.length ? <optgroup key={code} label={label}>{group.map(item => <option key={item.linkId} value={item.linkId}>{item.title}{item.level !== null ? ` (Lv. ${item.level})` : ""}</option>)}</optgroup> : null; })}
        </select>
      </label>
      {!chosen && <>
        <label className="grid gap-1 text-xs font-bold text-slate-500">문제 이름<input className={input} value={value.title} onChange={event => setValue({ ...value, title: event.target.value })}/></label>
        <label className="grid gap-1 text-xs font-bold text-slate-500">링크 (선택, https)<input className={input} placeholder="https://" value={value.url} onChange={event => setValue({ ...value, url: event.target.value })}/></label>
      </>}
      <label className="grid gap-1 text-xs font-bold text-slate-500">유형
        <select className={input} value={value.category} onChange={event => setValue({ ...value, category: event.target.value as Category })}>
          <option value="" disabled>선택</option>{categories.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
        </select>
      </label>
      <label className="grid gap-1 text-xs font-bold text-slate-500">언어<input className={input} value={value.language} onChange={event => setValue({ ...value, language: event.target.value })}/></label>
      <label className="grid gap-1 text-xs font-bold text-slate-500">푼 날<input type="date" className={input} max={today()} value={value.solvedOn} onChange={event => setValue({ ...value, solvedOn: event.target.value })}/></label>
      <label className="grid gap-1 text-xs font-bold text-slate-500">소요 분<input type="number" min={0} max={600} className={input} value={value.minutes ?? ""} onChange={event => setValue({ ...value, minutes: event.target.value === "" ? null : Number(event.target.value) })}/></label>
    </div>
    <fieldset className="mt-4"><legend className="text-xs font-bold text-slate-500">결과</legend><div className="mt-2 flex flex-wrap gap-2">{results.map(([code, label]) =>
      <button key={code} type="button" aria-pressed={value.result === code} onClick={() => setValue({ ...value, result: code })} className={`min-h-10 rounded-xl border px-4 text-sm font-bold ${value.result === code ? "border-blue-600 bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300" : "border-slate-200 text-slate-600 dark:border-slate-700 dark:text-slate-300"}`}>{label}</button>)}</div>
      {value.result !== "SUCCESS" && <p className="mt-2 text-xs text-slate-500">3일 뒤 다시 풀기로 잡아 둘게요.</p>}
    </fieldset>
    <div className="mt-4 grid gap-3">
      <label className="grid gap-1 text-xs font-bold text-slate-500">막힌 지점 (선택)<textarea className={`${input} min-h-16 py-2`} placeholder="예: 방문 처리를 큐에 넣을 때 해야 했다" value={value.stuckNote} onChange={event => setValue({ ...value, stuckNote: event.target.value })}/></label>
      <label className="grid gap-1 text-xs font-bold text-slate-500">메모 (선택)<textarea className={`${input} min-h-16 py-2`} value={value.note} onChange={event => setValue({ ...value, note: event.target.value })}/></label>
    </div>
    <div className="mt-4 flex gap-2"><Button disabled={!valid || save.isPending} onClick={() => save.mutate()}>저장</Button><Button variant="outline" onClick={onDone}>취소</Button></div>
    {save.error && <p role="alert" className="mt-2 text-xs text-rose-600">{save.error.message}</p>}
  </section>;
}

function LogList({ logs, onAdd }: { logs: PracticeLog[]; onAdd: () => void }) {
  const client = useQueryClient();
  const retry = useMutation({ mutationFn: addRetry, onSuccess: () => client.invalidateQueries({ queryKey: codingKeys.all }) });
  const newest = new Set<string>();
  const isNewest = logs.map(log => { const key = problemKey(log); const first = !newest.has(key); newest.add(key); return first; });
  return <section className={panel}>
    <div className="flex items-center justify-between gap-3"><h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">풀이 기록</h2><Button variant="outline" onClick={onAdd}><Plus size={16}/>기록 추가</Button></div>
    {logs.length === 0 ? <p className="mt-3 text-sm text-slate-500">아직 기록이 없어요. 추천 문제를 풀고 &quot;기록하기&quot;를 눌러 주세요.</p>
      : <ul className="mt-4 grid gap-3">{logs.map((log, index) => <li key={log.logId} className="rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <strong className="text-sm text-slate-900 dark:text-slate-100">{log.problemUrl ? <a href={log.problemUrl} target="_blank" rel="noopener noreferrer" className="hover:text-blue-700 hover:underline">{log.problemTitle}</a> : log.problemTitle}</strong>
          <span className={`rounded-full px-2.5 py-1 text-xs font-black ${resultTone[log.result]}`}>{resultLabel[log.result]}</span>
        </div>
        <p className="mt-1 text-xs text-slate-500">{log.solvedOn} · {categoryLabel[log.category]}{log.language && ` · ${log.language}`}{log.minutes !== null && ` · ${log.minutes}분`}</p>
        {log.stuckNote && <p className="mt-2 text-xs text-slate-600 dark:text-slate-300"><b>막힌 지점</b> {log.stuckNote}</p>}
        {log.note && <p className="mt-1 text-xs text-slate-600 dark:text-slate-300">{log.note}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          {log.nextRetryAt && <span className="inline-flex items-center gap-1 font-semibold text-blue-700 dark:text-blue-300"><CalendarClock size={14}/>다시 풀기 {log.nextRetryAt}</span>}
          {!log.nextRetryAt && log.result === "SUCCESS" && isNewest[index] && <button type="button" disabled={retry.isPending} onClick={() => retry.mutate(log)} className="inline-flex items-center gap-1 font-bold text-slate-500 hover:text-blue-700"><RotateCcw size={14}/>다시 풀기 추가 (7일 뒤)</button>}
        </div>
      </li>)}</ul>}
    {retry.error && <p role="alert" className="mt-2 text-xs text-rose-600">{retry.error.message}</p>}
  </section>;
}

function RetrySchedule({ logs }: { logs: PracticeLog[] }) {
  const planned = logs.filter(log => log.nextRetryAt).sort((a, b) => a.nextRetryAt!.localeCompare(b.nextRetryAt!));
  const now = today();
  return <section className={panel}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">다시 풀기 일정</h2>
    <p className="mt-1 text-xs text-slate-500">실패·해설 참고는 3일 뒤, 다시 풀어 성공하면 7일 → 14일 뒤로 늘어나요.</p>
    {planned.length === 0 ? <p className="mt-3 text-sm text-slate-500">잡힌 일정이 없어요.</p>
      : <ul className="mt-3 grid gap-2">{planned.map(log => <li key={log.logId} className="flex items-center justify-between gap-2 text-sm"><span className="min-w-0 truncate text-slate-700 dark:text-slate-200">{log.problemTitle}</span><span className={`shrink-0 text-xs font-bold ${log.nextRetryAt! <= now ? "text-rose-600" : "text-slate-500"}`}>{log.nextRetryAt! <= now ? "오늘 풀 차례" : log.nextRetryAt}</span></li>)}</ul>}
  </section>;
}

function Stats({ overview }: { overview: CodingOverview }) {
  return <section className={panel}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">유형별 성공률</h2>
    <p className="mt-1 text-xs text-slate-500">최근 4주 · 해설 참고는 성공으로 세지 않아요. 70% 미만이면 약한 유형으로 먼저 추천해요.</p>
    {overview.categoryStats.length === 0 ? <p className="mt-3 text-sm text-slate-500">최근 4주 기록이 없어요.</p>
      : <ul className="mt-3 grid gap-2">{overview.categoryStats.map(stat => { const rate = Math.round(stat.successes / stat.attempts * 100); return <li key={stat.category} className="grid gap-1">
        <div className="flex justify-between text-xs"><span className="font-semibold text-slate-700 dark:text-slate-200">{categoryLabel[stat.category]}</span><span className={rate < 70 ? "font-bold text-rose-600" : "text-slate-500"}>{stat.successes}/{stat.attempts} · {rate}%</span></div>
        <div className="h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className={`h-full rounded-full ${rate < 70 ? "bg-rose-500" : "bg-emerald-500"}`} style={{ width: `${rate}%` }}/></div>
      </li>; })}</ul>}
  </section>;
}

function LinkManager({ links }: { links: ProblemLink[] }) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState<{ title: string; url: string; level: string; category: Category | "" }>({ title: "", url: "", level: "", category: "" });
  const refresh = () => client.invalidateQueries({ queryKey: codingKeys.all });
  const mark = useMutation({ mutationFn: ({ link, kind }: { link: ProblemLink; kind: "hidden" | "solved-before" }) =>
    setLinkMark(link.linkId, kind, kind === "hidden" ? !link.hidden : !link.solvedBefore), onSuccess: refresh });
  const solvedBefore = links.filter(link => link.solvedBefore).length;
  const add = useMutation({ mutationFn: () => createLink({ title: value.title.trim(), url: value.url.trim(), level: value.level === "" ? null : Number(value.level), category: value.category as Category }),
    onSuccess: async () => { setValue({ title: "", url: "", level: "", category: "" }); await refresh(); } });
  return <section className={panel}>
    <div className="flex items-center justify-between gap-3"><div><h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">추천 목록 관리</h2><p className="mt-1 text-xs text-slate-500">{links.length}개 · 이미 푼 문제 {solvedBefore}개 · 앱을 쓰기 전에 푼 문제는 &quot;이미 풂&quot;으로, 풀지 않을 문제는 숨기기로 표시하세요. 둘 다 추천에서 빠지고 주간 목표·성공률에는 세지 않아요. 프로그래머스 기본 목록은 2026-10-25에 링크를 확인했어요.</p></div><Button variant="outline" aria-expanded={open} onClick={() => setOpen(!open)}>{open ? "접기" : "펼치기"}</Button></div>
    {open && <>
      <div className="mt-4 grid gap-3 rounded-2xl border border-slate-200 p-4 dark:border-slate-700 sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1.4fr)_90px_minmax(0,1fr)_auto] sm:items-end">
        <label className="grid gap-1 text-xs font-bold text-slate-500">문제 이름<input className={input} value={value.title} onChange={event => setValue({ ...value, title: event.target.value })}/></label>
        <label className="grid gap-1 text-xs font-bold text-slate-500">링크 (https)<input className={input} placeholder="https://" value={value.url} onChange={event => setValue({ ...value, url: event.target.value })}/></label>
        <label className="grid gap-1 text-xs font-bold text-slate-500">레벨<input type="number" min={0} max={5} className={input} value={value.level} onChange={event => setValue({ ...value, level: event.target.value })}/></label>
        <label className="grid gap-1 text-xs font-bold text-slate-500">유형<select className={input} value={value.category} onChange={event => setValue({ ...value, category: event.target.value as Category })}><option value="" disabled>선택</option>{categories.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
        <Button disabled={!value.title.trim() || !/^https:\/\/\S+$/.test(value.url.trim()) || !value.category || add.isPending} onClick={() => add.mutate()}><Plus size={16}/>추가</Button>
      </div>
      {add.error && <p role="alert" className="mt-2 text-xs text-rose-600">{add.error.message}</p>}
      <div className="mt-4 grid gap-4 sm:grid-cols-2">{categories.map(([code, label]) => { const group = links.filter(link => link.category === code); return group.length === 0 ? null : <div key={code}>
        <h3 className="text-xs font-black text-slate-500">{label}</h3>
        <ul className="mt-2 grid gap-1">{group.map(link => <li key={link.linkId} className={`flex items-center justify-between gap-2 rounded-xl px-2 py-1 text-sm ${link.hidden || link.solvedBefore ? "opacity-50" : ""}`}>
          <a href={link.url} target="_blank" rel="noopener noreferrer" className="min-w-0 truncate text-slate-700 hover:text-blue-700 hover:underline dark:text-slate-200">{link.title}{link.level !== null && <span className="ml-1 text-xs text-slate-400">Lv. {link.level}</span>}{link.own && <span className="ml-1 text-xs text-blue-600">내 문제</span>}</a>
          <span className="flex shrink-0 items-center gap-1">{link.lastResult && <span className={`mr-1 rounded-full px-2 py-0.5 text-[10px] font-black ${resultTone[link.lastResult]}`}>{resultLabel[link.lastResult]}</span>}
            <button type="button" aria-pressed={link.solvedBefore} aria-label={link.solvedBefore ? `${link.title} 이미 푼 문제 표시 해제` : `${link.title} 이미 푼 문제로 표시`} disabled={mark.isPending} onClick={() => mark.mutate({ link, kind: "solved-before" })} className={`inline-flex h-8 items-center gap-1 rounded-lg px-2 text-[11px] font-bold ${link.solvedBefore ? "bg-emerald-100 text-emerald-700" : "text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800"}`}><CheckCheck size={14}/>이미 풂</button>
            <button type="button" aria-label={link.hidden ? `${link.title} 다시 보이기` : `${link.title} 숨기기`} disabled={mark.isPending} onClick={() => mark.mutate({ link, kind: "hidden" })} className="grid size-8 place-items-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800">{link.hidden ? <Eye size={15}/> : <EyeOff size={15}/>}</button></span>
        </li>)}</ul>
      </div>; })}</div>
    </>}
  </section>;
}
