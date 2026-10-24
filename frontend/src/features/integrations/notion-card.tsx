"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Cloud, History, Link2, Play, RotateCcw, Save, Unplug } from "lucide-react";
import { Button } from "@/components/ui/button";
import { currentCurriculumQuery } from "@/features/roadmap/queries";
import type { NotionConnection, NotionEntityType, NotionSyncJob, NotionTarget } from "./contract";
import { NOTION_ENTITY_TYPES } from "./contract";
import {
  disconnectNotion,
  integrationKeys,
  notionHistoryQuery,
  notionJobQuery,
  notionTargetsQuery,
  retryNotionSync,
  saveNotionTargets,
  startNotionAuthorization,
  startNotionSync,
} from "./queries";

import { NotionTargetPicker } from "./notion-target-picker";
import { notionSetupQuery } from "./browser-connections";

const LABELS: Record<NotionEntityType, string> = {
  LEARNING_RESULT: "학습 결과", PROBLEM: "문제 풀이", TEST_RESULT: "시험 결과", WRONG_ANSWER: "오답",
  WEEKLY_REPORT: "주간 리포트", MONTHLY_REPORT: "월간 리포트",
};
const blankTargets = (): NotionTarget[] => NOTION_ENTITY_TYPES.map(entityType => ({ entityType, databaseId: "", dataSourceId: "" }));
const today = () => new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul" }).format(new Date());
const monthAgo = () => { const value = new Date(); value.setDate(value.getDate() - 30); return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul" }).format(value); };

export function NotionCard({ connection }: { connection: NotionConnection }) {
  const client = useQueryClient();
  const setup = useQuery(notionSetupQuery);
  const targets = useQuery({ ...notionTargetsQuery, enabled: connection.connected });
  const history = useQuery(notionHistoryQuery);
  const curriculum = useQuery(currentCurriculumQuery);
  const [draftOverride, setDraftOverride] = useState<NotionTarget[] | null>(null);
  const [selectedTypes, setSelectedTypes] = useState<NotionEntityType[]>([...NOTION_ENTITY_TYPES]);
  const [from, setFrom] = useState(monthAgo);
  const [to, setTo] = useState(today);
  const [selectedJob, setSelectedJob] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const detail = useQuery({ ...notionJobQuery(selectedJob ?? "0"), enabled: selectedJob !== null });
  const refresh = () => client.invalidateQueries({ queryKey: integrationKeys.all });

  const draft = draftOverride ?? (targets.data?.targets
    ? NOTION_ENTITY_TYPES.map(type => targets.data!.targets.find(target => target.entityType === type) ?? { entityType: type, databaseId: "", dataSourceId: "" })
    : blankTargets());

  const connect = useMutation({
    mutationFn: startNotionAuthorization,
    onMutate: () => setNotice(null),
    onSuccess: result => window.location.assign(result.data.authorizationUrl),
  });
  const disconnect = useMutation({
    mutationFn: disconnectNotion,
    onMutate: () => setNotice(null),
    onSuccess: async result => { setNotice(result.data.providerRevocationStatus === "PENDING" ? "앱 연결을 해제했습니다. 공급자 토큰 폐기는 백그라운드에서 재시도합니다." : "Notion 연결을 해제했습니다."); await refresh(); },
  });
  const save = useMutation({
    mutationFn: () => saveNotionTargets({ targets: draft, revision: targets.data?.revision ?? 0 }),
    onMutate: () => setNotice(null),
    onSuccess: async () => { setDraftOverride(null); setNotice("6개 Notion 대상을 검증하고 저장했습니다."); await refresh(); },
  });
  const sync = useMutation({
    mutationFn: () => startNotionSync({ curriculumId: curriculum.data!.data!.curriculumId, entityTypes: selectedTypes, from, to }),
    onMutate: () => setNotice(null),
    onSuccess: async result => { setNotice(`동기화 작업 #${result.data.jobId}을 등록했습니다.`); setSelectedJob(result.data.jobId); await refresh(); },
  });
  const retry = useMutation({
    mutationFn: retryNotionSync,
    onMutate: () => setNotice(null),
    onSuccess: async result => { setNotice(`실패 항목 재시도 #${result.data.jobId}을 등록했습니다.`); setSelectedJob(result.data.jobId); await refresh(); },
  });

  const currentCourse = curriculum.data?.data;
  const validTargets = useMemo(() => draft.every(target => uuid(target.databaseId) && uuid(target.dataSourceId)), [draft]);
  const error = connect.error ?? disconnect.error ?? save.error ?? sync.error ?? retry.error ?? targets.error ?? history.error ?? detail.error;
  const busy = connect.isPending || disconnect.isPending || save.isPending || sync.isPending || retry.isPending;
  const status = connection.connected ? connection.targetsConfigured ? "동기화 준비됨" : "대상 설정 필요"
    : connection.status === "REAUTH_REQUIRED" ? "재연결 필요" : "연결 안 됨";

  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6 lg:col-span-2">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <span className="flex items-center gap-3 text-lg font-black text-[#0a1837] dark:text-slate-50"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Cloud size={20}/></span>Notion</span>
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{status}</span>
    </div>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">학습 내용과 복습 기록을 Notion 페이지 본문에 모아 볼 수 있습니다. 연결 후 공유한 데이터베이스를 선택하고 동기화를 시작하세요.</p>

    {!connection.connected ? <div className="mt-5 rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
      <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">{setup.data?.configured ? "Notion 사이트에서 워크스페이스와 공유할 페이지를 선택하세요." : "운영자의 최초 Notion 앱 설정이 필요합니다. 설정이 끝나면 연결할 수 있습니다."}</p>
      <Button className="mt-4 w-full sm:w-auto" disabled={busy || !setup.data?.configured} onClick={() => connect.mutate()}><Link2 size={17}/>{connection.status === "REAUTH_REQUIRED" ? "Notion 다시 연결" : "Notion 연결"}</Button>
    </div> : <>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-emerald-50 px-4 py-3 dark:bg-emerald-950/40">
        <span className="text-sm font-bold text-emerald-800 dark:text-emerald-200">{connection.workspaceName ?? "Notion 워크스페이스"} · API {connection.notionVersion}</span>
        <Button className="min-h-9 px-3 py-1.5" variant="outline" disabled={busy} onClick={() => disconnect.mutate()}><Unplug size={15}/>연결 해제</Button>
      </div>

      <section className="mt-6">
        <div className="flex items-center justify-between gap-3"><h3 className="text-sm font-black text-slate-900 dark:text-slate-100">6종 동기화 대상</h3><span className="text-xs font-semibold text-slate-500">revision {targets.data?.revision ?? 0}</span></div>
        <NotionTargetPicker onSelect={selected => setDraftOverride(NOTION_ENTITY_TYPES.map(entityType => ({ entityType, databaseId: selected.databaseId, dataSourceId: selected.dataSourceId })))}/>
        {targets.isPending ? <p className="mt-3 text-sm text-slate-500">대상을 불러오는 중입니다.</p> : <div className="mt-3 grid gap-3">
          {draft.map((target, index) => <div key={target.entityType} className="grid gap-2 rounded-2xl border border-slate-200 p-3 dark:border-slate-700 sm:grid-cols-[140px_1fr_1fr] sm:items-center">
            <strong className="text-sm text-slate-800 dark:text-slate-100">{LABELS[target.entityType]}</strong>
            <input aria-label={`${LABELS[target.entityType]} database ID`} className="min-w-0 rounded-xl border border-slate-200 bg-transparent px-3 py-2 text-xs dark:border-slate-700" placeholder="Database UUID" value={target.databaseId} onChange={event => setDraftOverride(draft.map((item, itemIndex) => itemIndex === index ? { ...item, databaseId: event.target.value.trim() } : item))}/>
            <input aria-label={`${LABELS[target.entityType]} data source ID`} className="min-w-0 rounded-xl border border-slate-200 bg-transparent px-3 py-2 text-xs dark:border-slate-700" placeholder="Data source UUID" value={target.dataSourceId} onChange={event => setDraftOverride(draft.map((item, itemIndex) => itemIndex === index ? { ...item, dataSourceId: event.target.value.trim() } : item))}/>
          </div>)}
          <Button variant="outline" disabled={!validTargets || busy} onClick={() => save.mutate()}><Save size={16}/>대상 검증 후 저장</Button>
        </div>}
      </section>

      <section className="mt-7 border-t border-slate-200 pt-6 dark:border-slate-700">
        <h3 className="text-sm font-black text-slate-900 dark:text-slate-100">수동 동기화</h3>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <label className="text-xs font-bold text-slate-600 dark:text-slate-300">시작일<input type="date" className="mt-1 block w-full rounded-xl border border-slate-200 bg-transparent px-3 py-2 dark:border-slate-700" value={from} max={to} onChange={event => setFrom(event.target.value)}/></label>
          <label className="text-xs font-bold text-slate-600 dark:text-slate-300">종료일<input type="date" className="mt-1 block w-full rounded-xl border border-slate-200 bg-transparent px-3 py-2 dark:border-slate-700" value={to} min={from} max={today()} onChange={event => setTo(event.target.value)}/></label>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">{NOTION_ENTITY_TYPES.map(type => <label key={type} className="flex items-center gap-2 rounded-full border border-slate-200 px-3 py-2 text-xs font-bold dark:border-slate-700"><input type="checkbox" checked={selectedTypes.includes(type)} onChange={event => setSelectedTypes(current => event.target.checked ? [...current, type] : current.filter(value => value !== type))}/>{LABELS[type]}</label>)}</div>
        <p className="mt-3 text-xs leading-5 text-slate-600 dark:text-slate-300">선택한 기간의 학습 본문, 문제 지문·제출 답·채점 완료 후 정답과 해설, 시험 문항·점수, 오답 메모와 주간·월간 학습 내역이 선택한 Notion 데이터베이스로 복사됩니다. 기존 항목은 같은 페이지에서 갱신합니다.</p>
        <Button className="mt-4 w-full sm:w-auto" disabled={!connection.targetsConfigured || !currentCourse || selectedTypes.length === 0 || from > to || busy} onClick={() => sync.mutate()}><Play size={16}/>현재 과정 동기화</Button>
        {!currentCourse && <p className="mt-2 text-xs font-semibold text-amber-700 dark:text-amber-300">진행 중인 과정이 있어야 동기화를 시작할 수 있습니다.</p>}
      </section>
    </>}

    <section className="mt-7 border-t border-slate-200 pt-6 dark:border-slate-700">
      <div className="flex items-center justify-between gap-3"><h3 className="flex items-center gap-2 text-sm font-black text-slate-900 dark:text-slate-100"><History size={17}/>최근 동기화</h3>{history.data && <span className="text-xs font-semibold text-slate-500">총 {history.data.totalElements}건</span>}</div>
      {history.isPending ? <p className="mt-3 text-sm text-slate-500">동기화 이력을 확인하는 중입니다.</p> : history.data?.content.length ? <ul className="mt-3 grid gap-2">{history.data.content.map(job => <JobRow key={job.syncId} job={job} busy={busy} onDetail={() => setSelectedJob(job.syncId)} onRetry={() => retry.mutate(job.syncId)}/>)}</ul> : <p className="mt-3 text-sm text-slate-500">아직 동기화 이력이 없습니다.</p>}
      {selectedJob && detail.data && <div className="mt-4 rounded-2xl bg-slate-50 p-4 text-xs dark:bg-slate-800/70"><strong className="text-sm text-slate-900 dark:text-slate-100">작업 #{detail.data.syncId} 상세</strong><p className="mt-2 text-slate-600 dark:text-slate-300">생성 {detail.data.createdCount} · 갱신 {detail.data.updatedCount} · 건너뜀 {detail.data.skippedCount} · 실패 {detail.data.failedCount}</p>{detail.data.failures.map(failure => <p key={`${failure.entityType}-${failure.sourceId}`} className="mt-2 text-rose-700 dark:text-rose-300">{LABELS[failure.entityType]} #{failure.sourceId}: {failure.message} ({failure.code})</p>)}</div>}
    </section>
    {notice && <p role="status" className="mt-4 text-sm font-semibold text-emerald-700 dark:text-emerald-300">{notice}</p>}
    {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600 dark:text-rose-300">{error instanceof Error ? error.message : "Notion 작업을 완료하지 못했습니다."}</p>}
  </article>;
}

function JobRow({ job, busy, onDetail, onRetry }: { job: NotionSyncJob; busy: boolean; onDetail: () => void; onRetry: () => void }) {
  const retryable = (job.status === "FAILED" || job.status === "PARTIAL_SUCCESS") && job.failures.some(failure => failure.retryable);
  return <li className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 px-4 py-3 dark:border-slate-700"><span><strong className="block text-sm text-slate-900 dark:text-slate-100">#{job.syncId} · {statusLabel(job.status)}</strong><span className="mt-1 block text-xs text-slate-500">{job.from} ~ {job.to} · {job.totalCount}건</span></span><span className="flex gap-2"><Button className="min-h-9 px-3 py-1.5" variant="outline" onClick={onDetail}>상세</Button>{retryable && <Button className="min-h-9 px-3 py-1.5" variant="outline" disabled={busy} onClick={onRetry}><RotateCcw size={14}/>실패 재시도</Button>}</span></li>;
}
function statusLabel(status: NotionSyncJob["status"]) { return ({ READY: "대기", PROCESSING: "처리 중", SUCCESS: "성공", PARTIAL_SUCCESS: "부분 성공", FAILED: "실패" })[status]; }
function uuid(value: string) { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
