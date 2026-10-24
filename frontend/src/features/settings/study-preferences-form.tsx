"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { HelpHint } from "@/components/ui/help-hint";
import { toggleWeekday, weekdayOptions } from "@/features/onboarding/model";
import { isStudyTime, type StudyPreferences, type UserSettings } from "./contract";
import { updateStudyPreferences, userSettingsKey, userSettingsQuery } from "./queries";

export function StudyPreferencesForm() {
  const query = useQuery(userSettingsQuery);
  if (!query.data) return <div className="mt-5" role="status">
    <p className="text-sm text-slate-600 dark:text-slate-300">{query.isError ? "학습 기본값을 불러오지 못했어요." : "학습 기본값을 불러오는 중이에요."}</p>
    {query.isError && <Button variant="outline" className="mt-3" onClick={() => void query.refetch()}>학습 기본값 다시 불러오기</Button>}
  </div>;
  return <PreferencesEditor value={query.data} reload={async () => (await query.refetch()).isSuccess} />;
}

const inputStyle = "min-h-12 w-full min-w-0 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-900 dark:border-slate-600 dark:bg-[#182233] dark:text-slate-100";
const preferences = ({ studyPolicy, weekdayStudyStartTime, weekendStudyStartTime }: UserSettings): StudyPreferences => ({ studyPolicy, weekdayStudyStartTime, weekendStudyStartTime });

function PreferencesEditor({ value, reload }: { value: UserSettings; reload: () => Promise<boolean> }) {
  const client = useQueryClient();
  const [draft, setDraft] = useState<StudyPreferences | null>(null);
  const [baseRevision, setBaseRevision] = useState<number | null>(null);
  const [reloading, setReloading] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  const save = useMutation({ mutationFn: updateStudyPreferences, onSuccess: next => {
    client.setQueryData(userSettingsKey, next);
    setDraft(null);
    setBaseRevision(null);
  }, onError: error => { if (error instanceof ApiError && error.status === 412) setConflicted(true); } });
  const current = draft ?? preferences(value);
  const stale = (baseRevision !== null && baseRevision !== value.revision) || conflicted;
  const valid = Number.isInteger(current.studyPolicy.dailyStudyMinutes) && current.studyPolicy.dailyStudyMinutes >= 15 && current.studyPolicy.dailyStudyMinutes <= 480 && isStudyTime(current.weekdayStudyStartTime) && isStudyTime(current.weekendStudyStartTime);
  const busy = save.isPending || reloading;
  const edit = (patch: Partial<StudyPreferences>) => {
    save.reset();
    setBaseRevision(baseRevision ?? value.revision);
    setDraft({ ...current, ...patch });
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (valid && draft && !stale && !busy) save.mutate({ body: draft, revision: baseRevision ?? value.revision });
  };
  const refresh = async () => {
    setReloading(true);
    setReloadFailed(false);
    try { if (await reload()) { setDraft(null); setBaseRevision(null); setConflicted(false); save.reset(); } else setReloadFailed(true); }
    finally { setReloading(false); }
  };

  return <form className="mt-6 border-t border-slate-100 pt-6 dark:border-slate-700" onSubmit={submit}>
    <div className="flex items-center gap-2"><h3 className="text-sm font-extrabold text-slate-900 dark:text-slate-100">다음 과정의 학습 기본값</h3><HelpHint label="학습 기본값" summary="새 과정을 시작할 때 먼저 채워 둘 시간과 휴식 요일이에요." items={[
      { term: "적용 시점", description: "다음 과정을 만들 때 기본값으로 불러와요." },
      { term: "현재 과정", description: "진행 중인 과정의 일정은 바뀌지 않아요." },
    ]} note="현재 일정은 로드맵의 '휴식과 일정 조정'에서 미리본 뒤 변경하세요." /></div>
    <p className="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400">새 과정을 만들 때 불러올 학습 시간과 휴식일입니다. 현재 과정의 일정은 바뀌지 않으며, 일정 조정은 캘린더에서 미리 확인한 뒤 적용할 수 있어요.</p>
    <fieldset disabled={busy} className="mt-5 grid min-w-0 gap-5 disabled:opacity-60">
      <div className="grid min-w-0 gap-4 sm:grid-cols-3">
        <label className="grid min-w-0 gap-2 text-xs font-bold text-slate-700 dark:text-slate-200">하루 학습 시간 (분)
          <input className={inputStyle} type="number" min={15} max={480} step={1} required value={Number.isNaN(current.studyPolicy.dailyStudyMinutes) ? "" : current.studyPolicy.dailyStudyMinutes} onChange={event => edit({ studyPolicy: { ...current.studyPolicy, dailyStudyMinutes: event.target.valueAsNumber } })}/>
        </label>
        <label className="grid min-w-0 gap-2 text-xs font-bold text-slate-700 dark:text-slate-200">평일 학습 시작 시간
          <input className={inputStyle} type="time" required value={current.weekdayStudyStartTime} onChange={event => edit({ weekdayStudyStartTime: event.target.value })}/>
        </label>
        <label className="grid min-w-0 gap-2 text-xs font-bold text-slate-700 dark:text-slate-200">주말 학습 시작 시간
          <input className={inputStyle} type="time" required value={current.weekendStudyStartTime} onChange={event => edit({ weekendStudyStartTime: event.target.value })}/>
        </label>
      </div>
      <fieldset><legend className="text-xs font-bold text-slate-700 dark:text-slate-200">매주 쉬는 요일 ({current.studyPolicy.restDaysPerWeek}일)</legend>
        <div className="mt-3 grid grid-cols-7 gap-1.5">{weekdayOptions.map(day => {
          const selected = current.studyPolicy.restWeekdays.includes(day.value);
          return <button key={day.value} type="button" aria-label={`${day.label}요일 휴식`} aria-pressed={selected} disabled={!selected && current.studyPolicy.restDaysPerWeek === 6} onClick={() => {
            const restWeekdays = toggleWeekday(current.studyPolicy.restWeekdays, day.value);
            edit({ studyPolicy: { ...current.studyPolicy, restDaysPerWeek: restWeekdays.length, restWeekdays } });
          }} className={`min-h-11 rounded-xl border text-sm font-bold disabled:opacity-40 ${selected ? "border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-200" : "border-slate-200 text-slate-600 dark:border-slate-600 dark:text-slate-300"}`}>{day.label}</button>;
        })}</div>
      </fieldset>
    </fieldset>
    <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">하루 15~480분 · 휴식일 최대 6일 · 한국 시간 기준. 알림 발송 시각은 알림 설정에서 따로 관리합니다.</p>
    {stale && <p role="alert" className="mt-4 text-sm text-amber-700 dark:text-amber-300">다른 화면에서 설정이 변경됐어요. 최신 값을 불러온 뒤 다시 수정해 주세요.</p>}
    {save.isError && !stale && <p role="alert" className="mt-4 text-sm text-rose-600 dark:text-rose-300">학습 기본값을 저장하지 못했어요. 입력한 값은 유지되니 다시 시도해 주세요.</p>}
    {reloadFailed && <p role="alert" className="mt-4 text-sm text-rose-600 dark:text-rose-300">최신 값을 불러오지 못했어요. 입력한 값은 유지됩니다.</p>}
    <div className="mt-5 flex flex-wrap items-center gap-3">
      <Button type="submit" disabled={!draft || !valid || stale || busy}>{save.isPending ? "저장 중" : "학습 기본값 저장"}</Button>
      {(draft || save.isError) && <Button type="button" variant="outline" disabled={busy} onClick={() => void refresh()}>{reloading ? "불러오는 중" : "최신 값 불러오기"}</Button>}
      {save.isSuccess && <span role="status" className="text-xs font-bold text-emerald-700 dark:text-emerald-300">학습 기본값을 저장했어요.</span>}
    </div>
  </form>;
}
