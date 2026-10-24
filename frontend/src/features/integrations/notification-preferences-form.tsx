"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { weekdayOptions } from "@/features/onboarding/model";
import type { NotificationSettings } from "./contract";
import { settingsQuery, updateSettings } from "./queries";

type ReminderDraft = Pick<NotificationSettings, "studyReminder" | "testReminder" | "streakReminder" | "weeklyReport" | "scheduledRemindersEnabled">;

const fields = (value: NotificationSettings): ReminderDraft => ({
  studyReminder: value.studyReminder,
  testReminder: value.testReminder,
  streakReminder: value.streakReminder,
  weeklyReport: value.weeklyReport,
  scheduledRemindersEnabled: value.scheduledRemindersEnabled,
});
const validTime = (time: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(time);
const inputClass = "min-h-11 w-full min-w-0 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-[#182233] dark:text-slate-100 dark:focus:ring-blue-500/20";

export function NotificationPreferencesForm({ value }: { value: NotificationSettings }) {
  const client = useQueryClient();
  const [draft, setDraft] = useState<ReminderDraft | null>(null);
  const [baseRevision, setBaseRevision] = useState<number | null>(null);
  const [conflicted, setConflicted] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  const save = useMutation({
    mutationFn: updateSettings,
    onSuccess: result => {
      client.setQueryData(settingsQuery.queryKey, result.data);
      setDraft(null);
      setBaseRevision(null);
      setConflicted(false);
    },
    onError: error => { if (error instanceof ApiError && error.status === 412) setConflicted(true); },
  });
  const current = draft ?? fields(value);
  const stale = conflicted || (baseRevision !== null && baseRevision !== value.revision);
  const valid = validTime(current.studyReminder.time) && validTime(current.streakReminder.time)
    && validTime(current.weeklyReport.time) && weekdayOptions.some(day => day.value === current.weeklyReport.dayOfWeek)
    && Number.isInteger(current.testReminder.beforeMinutes) && current.testReminder.beforeMinutes >= 1
    && current.testReminder.beforeMinutes <= 1440;
  const changed = draft !== null && JSON.stringify(draft) !== JSON.stringify(fields(value));
  const busy = save.isPending || reloading;
  const edit = (next: ReminderDraft) => {
    save.reset();
    setBaseRevision(baseRevision ?? value.revision);
    setDraft(next);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (draft && changed && valid && !stale && !busy) save.mutate({ body: draft, revision: baseRevision ?? value.revision });
  };
  const refresh = async () => {
    setReloading(true);
    setReloadFailed(false);
    try {
      await client.fetchQuery({ ...settingsQuery, staleTime: 0 });
      setDraft(null);
      setBaseRevision(null);
      setConflicted(false);
      save.reset();
    } catch { setReloadFailed(true); }
    finally { setReloading(false); }
  };

  return <form onSubmit={submit}>
    <fieldset disabled={busy} className="grid gap-3 disabled:opacity-60 sm:grid-cols-2">
      <ReminderCard title="학습 시작 알림" enabled={current.studyReminder.enabled} onToggle={enabled => edit({ ...current, studyReminder: { ...current.studyReminder, enabled } })}>
        <label className="grid gap-1.5 text-xs font-bold text-slate-600 dark:text-slate-300">매일 (한국 시간)
          <input className={inputClass} type="time" required value={current.studyReminder.time} onChange={event => edit({ ...current, studyReminder: { ...current.studyReminder, time: event.target.value } })}/>
        </label>
      </ReminderCard>
      <ReminderCard title="시험 알림" enabled={current.testReminder.enabled} onToggle={enabled => edit({ ...current, testReminder: { ...current.testReminder, enabled } })}>
        <label className="grid gap-1.5 text-xs font-bold text-slate-600 dark:text-slate-300">시험 시작 전 (분)
          <input className={inputClass} type="number" min={1} max={1440} step={1} required value={Number.isNaN(current.testReminder.beforeMinutes) ? "" : current.testReminder.beforeMinutes} onChange={event => edit({ ...current, testReminder: { ...current.testReminder, beforeMinutes: event.target.valueAsNumber } })}/>
        </label>
      </ReminderCard>
      <ReminderCard title="연속 학습 알림" enabled={current.streakReminder.enabled} onToggle={enabled => edit({ ...current, streakReminder: { ...current.streakReminder, enabled } })}>
        <label className="grid gap-1.5 text-xs font-bold text-slate-600 dark:text-slate-300">매일 (한국 시간)
          <input className={inputClass} type="time" required value={current.streakReminder.time} onChange={event => edit({ ...current, streakReminder: { ...current.streakReminder, time: event.target.value } })}/>
        </label>
      </ReminderCard>
      <ReminderCard title="주간 리포트" enabled={current.weeklyReport.enabled} onToggle={enabled => edit({ ...current, weeklyReport: { ...current.weeklyReport, enabled } })}>
        <div className="grid min-w-0 grid-cols-2 gap-2">
          <label className="grid min-w-0 gap-1.5 text-xs font-bold text-slate-600 dark:text-slate-300">요일
            <select className={inputClass} value={current.weeklyReport.dayOfWeek} onChange={event => edit({ ...current, weeklyReport: { ...current.weeklyReport, dayOfWeek: event.target.value } })}>{weekdayOptions.map(day => <option key={day.value} value={day.value}>{day.label}요일</option>)}</select>
          </label>
          <label className="grid min-w-0 gap-1.5 text-xs font-bold text-slate-600 dark:text-slate-300">시간 (한국 시간)
            <input className={inputClass} type="time" required value={current.weeklyReport.time} onChange={event => edit({ ...current, weeklyReport: { ...current.weeklyReport, time: event.target.value } })}/>
          </label>
        </div>
      </ReminderCard>
    </fieldset>
    <label className="mt-4 flex items-start gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-slate-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-slate-100">
      <input type="checkbox" className="mt-0.5 size-4 shrink-0 accent-blue-600" checked={current.scheduledRemindersEnabled} onChange={event => edit({ ...current, scheduledRemindersEnabled: event.target.checked })}/>
      <span><strong className="block">정기 알림 받기</strong><span className="mt-1 block text-xs leading-5">설정한 시각에 해당하는 학습·시험·주간 기록이 있을 때 앱 알림을 생성합니다. 연결된 Slack·웹 푸시가 켜져 있고 해당 알림 종류를 허용했다면 그 채널에도 전송됩니다. 체크를 해제하면 이후 정기 알림을 멈춥니다.</span></span>
    </label>
    <p className="mt-3 text-xs leading-5 text-slate-500 dark:text-slate-400">기존 알림 종류와 시각 설정은 보존됩니다. 정기 알림은 위 항목을 직접 켜고 저장한 뒤부터 시작됩니다. 시간은 한국 시간을 따릅니다.</p>
    {stale && <p role="alert" className="mt-3 text-sm text-amber-700 dark:text-amber-300">다른 화면에서 설정이 변경됐어요. 최신 값을 불러온 뒤 다시 수정해 주세요.</p>}
    {save.isError && !stale && <p role="alert" className="mt-3 text-sm text-rose-600 dark:text-rose-300">알림 설정을 저장하지 못했어요. 입력한 값은 유지되니 다시 시도해 주세요.</p>}
    {reloadFailed && <p role="alert" className="mt-3 text-sm text-rose-600 dark:text-rose-300">최신 설정을 불러오지 못했어요. 입력한 값은 유지됩니다.</p>}
    <div className="mt-4 flex flex-wrap items-center gap-3">
      <Button type="submit" disabled={!changed || !valid || stale || busy}>{save.isPending ? "저장 중" : "알림 설정 저장"}</Button>
      {(draft || save.isError) && <Button type="button" variant="outline" disabled={busy} onClick={() => void refresh()}>{reloading ? "불러오는 중" : "최신 값 불러오기"}</Button>}
      {save.isSuccess && <span role="status" className="text-xs font-bold text-emerald-700 dark:text-emerald-300">알림 설정을 저장했어요.</span>}
    </div>
  </form>;
}

function ReminderCard({ title, enabled, onToggle, children }: { title: string; enabled: boolean; onToggle: (enabled: boolean) => void; children: React.ReactNode }) {
  return <div className="min-w-0 rounded-2xl border border-slate-200 p-4 dark:border-slate-700 dark:bg-[#151f2f]">
    <div className="mb-4 flex items-center justify-between gap-3"><strong className="text-sm text-slate-900 dark:text-slate-100">{title}</strong><label className="relative shrink-0 cursor-pointer"><span className="sr-only">{title} 사용</span><input type="checkbox" className="peer sr-only" checked={enabled} onChange={event => onToggle(event.target.checked)}/><span className="block h-7 w-12 rounded-full bg-slate-300 transition peer-checked:bg-blue-600 peer-focus-visible:ring-2 peer-focus-visible:ring-blue-400 dark:bg-slate-600"/><span className={`pointer-events-none absolute top-1 size-5 rounded-full bg-white shadow transition ${enabled ? "left-6" : "left-1"}`}/></label></div>
    {children}
  </div>;
}
