"use client";
import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, CalendarClock, CalendarDays, ChevronDown, Coffee, RefreshCw } from "lucide-react";
import Link from "next/link";
import { api } from "@/api";
import { ApiError, createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { Button } from "@/components/ui/button";
import { HelpHint } from "@/components/ui/help-hint";
import { StatePanel } from "@/components/ui/state-panel";
import { IllustrationIcon } from "@/components/ui/illustration-icon";
import { decodeLearningDay } from "@/features/today-learning/contract";
import { curriculumTemplatesQuery } from "@/features/onboarding/queries";
import type { CurriculumTemplateDto } from "@/features/onboarding/contract";
import { maxStartDate } from "@/features/onboarding/model";
import { decodeCurriculumEnvelope, decodePreviewEnvelope, decodeResultEnvelope, type CurriculumDto, type RestReason, type ScheduleAction, type SchedulePreviewDto } from "./contract";
import { clearPendingPreview, confirmRequest, etagFor, formatDate, restorePendingPreview, savePendingPreview, seoulToday, toRoadmapViewModel } from "./model";
import { currentCurriculumQuery, roadmapKeys, roadmapMonthsQuery, roadmapWeekQuery, roadmapWeeksQuery } from "./queries";

const reasonLabels: Record<RestReason, string> = { PERSONAL: "개인 일정", VACATION: "휴가", HEALTH: "건강", WORK: "업무", ETC: "기타" };
const actionLabels: Record<ScheduleAction, string> = { REST_ADD: "휴식일 추가", REST_REMOVE: "휴식일 해제", SHIFT_BACKLOG: "밀린 일정 다시 배치", POLICY_APPLY: "학습 정책 변경" };
const needsScheduleRefresh = (error: unknown) => error instanceof ApiError && (
  error.status === 412 || error.status === 428 ||
  ["RESCHEDULE_PREVIEW_EXPIRED", "RESCHEDULE_PREVIEW_MISMATCH", "RESCHEDULE_PREVIEW_CONSUMED"].includes(error.error?.code ?? "")
);

export function RoadmapScreen() {
  const queryClient = useQueryClient();
  const curriculumQuery = useQuery(currentCurriculumQuery);
  const course = curriculumQuery.data?.data;
  const templatesQuery = useQuery({ ...curriculumTemplatesQuery, enabled: !!course });
  const monthsQuery = useQuery({ ...roadmapMonthsQuery(course?.curriculumId ?? "0"), enabled: !!course });
  const weeksQuery = useQuery({ ...roadmapWeeksQuery(course?.curriculumId ?? "0"), enabled: !!course });
  const [action, setAction] = useState<ScheduleAction>("REST_ADD");
  const [targetDate, setTargetDate] = useState(seoulToday);
  const [reason, setReason] = useState<RestReason>("PERSONAL");
  const [dailyMinutes, setDailyMinutes] = useState<string | null>(null);
  const [restWeekdays, setRestWeekdays] = useState<string[] | null>(null);
  const [previewState, setPreview] = useState<SchedulePreviewDto | null | undefined>(undefined);
  const [notice, setNotice] = useState<string | null>(null);
  const [changeWarning, setChangeWarning] = useState<string | null>(null);
  const warningRequest = useRef(0);
  const pendingSwitch = useRef<{ templateId: string; startDate: string; action: ReturnType<typeof createMutationAction<{ templateId: string; startDate: string }>> } | null>(null);
  const preview = previewState === undefined && course ? restorePendingPreview(course.curriculumId, course.scheduleRevision) : (previewState ?? null);

  const resetDraft = () => {
    warningRequest.current++;
    setPreview(null);
    if (course) clearPendingPreview(course.curriculumId);
    previewMutation.reset();
    confirmMutation.reset();
    setNotice(null);
    setChangeWarning(null);
  };
  const refresh = async (message = "최신 일정을 불러왔어요. 변경 내용을 다시 미리보기해 주세요.") => {
    warningRequest.current++;
    setPreview(null);
    if (course) clearPendingPreview(course.curriculumId);
    previewMutation.reset();
    confirmMutation.reset();
    setChangeWarning(null);
    await queryClient.invalidateQueries({ queryKey: roadmapKeys.all });
    setNotice(message);
  };
  const explainFailedConfirm = async (snapshot: SchedulePreviewDto | null) => {
    const request = ++warningRequest.current;
    setPreview(null);
    if (course) clearPendingPreview(course.curriculumId);
    previewMutation.reset();
    confirmMutation.reset();
    setNotice(null);
    setChangeWarning("미리보기 후 일정이 바뀌어 변경이 반영되지 않았어요. 확인 중입니다.");
    await queryClient.invalidateQueries({ queryKey: roadmapKeys.all });
    let message = "미리보기 후 일정이 바뀌어 변경이 반영되지 않았어요. 다시 미리보기해 주세요.";
    if (course && snapshot?.action === "REST_ADD" && snapshot.targetDate) {
      try {
        const day = (await api.request({
          path: `/api/v1/learning-days/${snapshot.targetDate}?curriculumId=${course.curriculumId}`,
          decode: value => decodeEnvelope(value, decodeLearningDay).data,
        })).data;
        if (day?.status === "IN_PROGRESS" || day?.status === "COMPLETED")
          message = `${formatDate(snapshot.targetDate)} 학습을 이미 시작해 휴식일로 바꿀 수 없어요. 변경은 반영되지 않았습니다. 아직 시작하지 않은 날짜를 선택해 주세요.`;
      } catch { /* Keep the confirmed schedule-conflict explanation when the day cannot be loaded. */ }
    }
    if (warningRequest.current === request) setChangeWarning(message);
  };
  const previewMutation = useMutation({ mutationFn: async () => {
    if (!course) throw new Error("과정이 없습니다.");
    const baseDate = action === "REST_ADD" || action === "REST_REMOVE" ? targetDate : seoulToday();
    const chosenMinutes = dailyMinutes ?? String(course.studyPolicy.dailyStudyMinutes);
    const chosenRestDays = restWeekdays ?? course.studyPolicy.restWeekdays;
    const body = { strategy: "SHIFT", action, baseDate, date: action === "REST_ADD" || action === "REST_REMOVE" ? targetDate : null, reason: action === "REST_ADD" ? reason : null, studyPolicy: action === "POLICY_APPLY" ? { dailyStudyMinutes: Number(chosenMinutes), restDaysPerWeek: chosenRestDays.length, restWeekdays: chosenRestDays } : null };
    return (await api.request({ path: `/api/v1/curriculums/${course.curriculumId}/reschedule-preview`, method: "POST", body, etag: curriculumQuery.data?.etag ?? etagFor(course.scheduleRevision), idempotencyKey: crypto.randomUUID(), decode: decodePreviewEnvelope })).data;
  }, onSuccess: value => { confirmMutation.reset(); setPreview(value); savePendingPreview(value); setNotice(null); }, onError: error => { if (needsScheduleRefresh(error)) void refresh("일정이 바뀌어 미리보기를 다시 계산해야 해요. 최신 일정을 불러왔습니다."); } });
  const confirmMutation = useMutation({ mutationFn: async () => {
    if (!course || !preview) throw new Error("미리보기가 없습니다.");
    const request = confirmRequest(preview);
    return (await api.request({ ...request, etag: etagFor(preview.scheduleRevision), idempotencyKey: crypto.randomUUID(), decode: decodeResultEnvelope })).data;
  }, onSuccess: async result => {
    if (course) clearPendingPreview(course.curriculumId);
    setPreview(null);
    const appliedNotice = preview?.action === "REST_REMOVE"
      ? `${formatDate(preview.targetDate!)} 휴식일을 해제했어요. 이제 학습일입니다.`
      : preview?.action === "REST_ADD" && preview.targetDate && preview.reason
        ? `${formatDate(preview.targetDate)}에 ${reasonLabels[preview.reason]} 휴식일을 추가했어요. 새 종료일은 ${formatDate(result.newEndDate)}입니다.`
        : `일정을 반영했어요. 새 종료일은 ${formatDate(result.newEndDate)}입니다.`;
    setNotice(appliedNotice);
    await queryClient.invalidateQueries({ queryKey: roadmapKeys.all });
  }, onError: error => { if (needsScheduleRefresh(error)) void explainFailedConfirm(preview); } });
  const restartMutation = useMutation({ mutationFn: async (startDate: string) => {
    if (!course) throw new Error("과정이 없습니다.");
    return (await api.request({ path: `/api/v1/curriculums/${course.curriculumId}/restart`, method: "POST",
      body: { startDate }, etag: curriculumQuery.data?.etag ?? etagFor(course.scheduleRevision),
      idempotencyKey: crypto.randomUUID(), decode: decodeCurriculumEnvelope })).data;
  }, onSuccess: async next => {
    if (!next) throw new Error("새 학습 과정이 응답에 없습니다.");
    if (course) clearPendingPreview(course.curriculumId);
    setPreview(null);
    setNotice(`${formatDate(next.startDate)}부터 첫날 학습을 다시 시작합니다. 맞는 AI 문제는 새 답안으로 다시 풀 수 있어요.`);
    await queryClient.invalidateQueries();
  } });
  const switchMutation = useMutation({ mutationFn: async (action: ReturnType<typeof createMutationAction<{ templateId: string; startDate: string }>>) => {
    if (!course) throw new Error("과정이 없습니다.");
    return (await api.request({ path: `/api/v1/curriculums/${course.curriculumId}/switch`, method: "POST",
      body: action.getPayload(), etag: curriculumQuery.data?.etag ?? etagFor(course.scheduleRevision),
      idempotencyKey: action.idempotencyKey, decode: decodeCurriculumEnvelope })).data;
  }, onSuccess: async next => {
    if (!next) throw new Error("새 학습 과정이 응답에 없습니다.");
    if (course) clearPendingPreview(course.curriculumId);
    pendingSwitch.current = null;
    setPreview(null);
    setNotice(`${next.title} 과정으로 바꿨어요. 과정 시작일은 ${formatDate(next.startDate)}이고, 실제 학습일은 쉬는 요일을 건너뛰어 배치했어요. 이전 과정 기록은 보관돼 있어요.`);
    await queryClient.invalidateQueries();
  } });
  const availableTemplates = templatesQuery.data?.filter(item => item.templateId !== course?.templateId) ?? [];
  const submitSwitch = (templateId: string, startDate: string) => {
    const saved = pendingSwitch.current;
    const action = saved?.templateId === templateId && saved.startDate === startDate
      ? saved.action : createMutationAction({ templateId, startDate });
    pendingSwitch.current = { templateId, startDate, action };
    switchMutation.mutate(action);
  };

  if (curriculumQuery.isPending) return <StatePanel state="loading" />;
  if (curriculumQuery.isError) return <StatePanel state="error" onRefresh={() => void curriculumQuery.refetch()} />;
  if (!course) return <section className="app-card overflow-hidden"><div className="grid gap-6 bg-gradient-to-br from-white via-blue-50/60 to-violet-50 p-7 dark:from-[#17243a] dark:via-[#1b2940] dark:to-[#231f3b] sm:p-10 lg:grid-cols-[1fr_auto] lg:items-center"><div><span className="icon-tile bg-blue-600 text-white shadow-lg shadow-blue-200"><CalendarDays size={22}/></span><p className="mt-6 text-sm font-bold text-blue-700">학습 로드맵</p><h1 className="mt-2 text-3xl font-extrabold tracking-[-0.035em] text-slate-950">첫 학습 계획을 만들면<br/>성장 경로가 펼쳐져요</h1><p className="mt-4 max-w-xl text-sm leading-7 text-slate-600">목표 과정과 학습 리듬을 선택하면 학습 단계와 휴식일을 실제 일정으로 구성합니다.</p></div><Link href="/onboarding" className="inline-flex min-h-12 w-fit items-center justify-center gap-2 rounded-2xl bg-blue-600 px-6 text-sm font-extrabold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-700">학습 계획 만들기<ArrowRight size={17}/></Link></div></section>;
  if (monthsQuery.isPending || weeksQuery.isPending) return <StatePanel state="loading" />;
  if (monthsQuery.isError || weeksQuery.isError) return <StatePanel state="error" onRefresh={() => void refresh()} />;
  const roadmap = toRoadmapViewModel(course, monthsQuery.data ?? [], weeksQuery.data ?? []);
  const isPreviewSizedPlan = (course.durationMonths ?? 6) === 6 && course.totalWeeks < 20;
  const mutationError = previewMutation.error ?? confirmMutation.error;
  const alreadyRemoved = mutationError instanceof ApiError && mutationError.error?.code === "VALIDATION_FAILED" && action === "REST_REMOVE";
  const lockedDay = mutationError instanceof ApiError && mutationError.error?.code === "RESCHEDULE_LOCKED_DAY";

  return <div className="space-y-7">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div className="flex min-w-0 items-center gap-4"><IllustrationIcon name="roadmap" size="lg" eager/><div className="min-w-0"><p className="text-sm font-bold text-blue-700">학습 로드맵</p><h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-950 sm:text-3xl">{roadmap.title}</h1><p className="mt-2 text-sm text-slate-600">{roadmap.period} · {roadmap.policy}</p><p className="mt-1 text-xs font-semibold text-slate-500">배정된 학습일 {course.totalLearningDays}일 · 학습 단계 {roadmap.months.length}개</p></div></div><div className="w-fit rounded-2xl bg-blue-50 px-4 py-3 dark:bg-blue-900/40"><div className="flex items-center gap-2"><p className="text-xs font-bold text-blue-700 dark:text-blue-200">배정된 학습 완료율</p><HelpHint label="배정된 학습 완료율" summary="지금 배정된 필수 학습을 얼마나 마쳤는지 보여줘요." items={[
      { term: "계산 기준", description: "완료한 필수 세션 ÷ 현재 배정된 필수 세션" },
      { term: "별도 지표", description: "문제 정답률과 시험 점수는 포함하지 않아요." },
    ]} note="전체 과정이 아직 배정되지 않았다면, 이 수치는 현재 제공된 학습만 기준으로 해요." align="right" /></div><p className="mt-1 text-xl font-extrabold text-blue-950 dark:text-blue-100">{roadmap.completion}</p></div></header>
    {isPreviewSizedPlan && <p role="status" className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-sm leading-6 text-amber-950">현재 제공된 학습은 {course.totalLearningDays}일 분량입니다. 전체 6개월 과정은 아직 배정되지 않았으며, 완료율은 지금 배정된 학습만 기준으로 표시됩니다.</p>}
    {notice && <p role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm font-bold text-emerald-800">{notice}</p>}
    {changeWarning && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-5 py-4 text-sm font-bold text-rose-800 dark:text-rose-100">{changeWarning}</p>}
    <section aria-labelledby="roadmap-months" className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
      <div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-violet-100 text-violet-700"><CalendarDays size={20}/></span><div><h2 id="roadmap-months" className="font-extrabold text-slate-950">단계별 학습 일정</h2><p className="text-xs text-slate-500">단계를 열면 날짜별 학습과 세션을 확인할 수 있어요.</p></div></div>
      <div className="mt-5 grid gap-3">{roadmap.months.map(month => <RoadmapMonth key={month.monthNo} month={month} curriculumId={course.curriculumId} initiallyOpen={month.monthNo === 1} />)}</div>
    </section>
    <section aria-labelledby="schedule-change" className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7"><div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-amber-100 text-amber-800"><Coffee size={20}/></span><div><div className="flex items-center gap-2"><h2 id="schedule-change" className="font-extrabold text-slate-950">휴식과 일정 조정</h2><HelpHint label="휴식과 일정 조정" summary="앞으로의 휴식일과 학습 배치를 조정해요." items={[
      { term: "미리보기", description: "이동할 학습일과 새 종료일을 계산해 보여줘요. 아직 저장되지 않아요." },
      { term: "일정 확정", description: "내용을 확인한 뒤 눌러야 실제 일정에 반영돼요." },
    ]} note="이미 시작했거나 완료한 학습은 이동하지 않아요." /></div><p className="text-xs text-slate-500">미리보기에서는 저장하지 않으며, 확정 전 이동 범위를 확인할 수 있어요.</p></div></div>
      <div className="mt-5 grid gap-4 md:grid-cols-2"><label className="grid gap-2 text-sm font-bold text-slate-700">변경 종류<select value={action} onChange={e => { setAction(e.target.value as ScheduleAction); resetDraft(); }} className="min-h-11 rounded-xl border border-slate-300 bg-white px-3"><option value="REST_ADD">휴식일 추가</option><option value="REST_REMOVE">휴식일 해제</option><option value="SHIFT_BACKLOG">밀린 일정 다시 배치</option><option value="POLICY_APPLY">학습 정책 변경</option></select></label>
      {(action === "REST_ADD" || action === "REST_REMOVE") && <label className="grid gap-2 text-sm font-bold text-slate-700">대상 날짜<input type="date" min={seoulToday()} value={targetDate} onChange={e => { setTargetDate(e.target.value); resetDraft(); }} className="min-h-11 rounded-xl border border-slate-300 px-3" /></label>}
      {action === "REST_ADD" && <label className="grid gap-2 text-sm font-bold text-slate-700">휴식 사유<select value={reason} onChange={e => { setReason(e.target.value as RestReason); resetDraft(); }} className="min-h-11 rounded-xl border border-slate-300 bg-white px-3">{Object.entries(reasonLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>}
      {action === "POLICY_APPLY" && <><label className="grid gap-2 text-sm font-bold text-slate-700">하루 학습 시간(분)<input type="number" min="1" max="1440" value={dailyMinutes ?? String(course.studyPolicy.dailyStudyMinutes)} onChange={e => { setDailyMinutes(e.target.value); resetDraft(); }} className="min-h-11 rounded-xl border border-slate-300 px-3" /></label><fieldset className="md:col-span-2"><legend className="text-sm font-bold text-slate-700">정기 휴식 요일</legend><div className="mt-2 flex flex-wrap gap-2">{[["MONDAY","월"],["TUESDAY","화"],["WEDNESDAY","수"],["THURSDAY","목"],["FRIDAY","금"],["SATURDAY","토"],["SUNDAY","일"]].map(([value,label]) => <label key={value} className="flex min-h-11 items-center gap-2 rounded-xl border border-slate-200 px-3 text-sm"><input type="checkbox" checked={(restWeekdays ?? course.studyPolicy.restWeekdays).includes(value)} onChange={() => { setRestWeekdays(current => { const days = current ?? course.studyPolicy.restWeekdays; return days.includes(value) ? days.filter(day => day !== value) : [...days, value]; }); resetDraft(); }}/>{label}</label>)}</div></fieldset></>}
      </div><Button className="mt-5" disabled={previewMutation.isPending || confirmMutation.isPending || (action === "POLICY_APPLY" && (!Number(dailyMinutes ?? course.studyPolicy.dailyStudyMinutes) || (restWeekdays ?? course.studyPolicy.restWeekdays).length > 6))} onClick={() => { resetDraft(); previewMutation.mutate(); }}><CalendarClock size={17}/>{previewMutation.isPending ? "계산 중…" : "변경 결과 미리보기"}</Button>
      {mutationError && <div role="alert" className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:text-rose-100"><strong>{alreadyRemoved ? "이 날짜는 해제할 휴식일이 아니에요." : lockedDay ? "이미 시작한 학습일이에요." : mutationError instanceof ApiError && (mutationError.status === 409 || mutationError.status === 412) ? "일정이 달라졌어요." : "변경안을 처리하지 못했어요."}</strong><p className="mt-1">{alreadyRemoved ? "이미 학습일로 바뀌었거나 휴식일이 아닌 날짜입니다. 최신 일정을 확인하고 다른 휴식일을 선택해 주세요." : lockedDay ? "시작했거나 완료한 학습은 휴식일로 옮길 수 없어요. 아직 시작하지 않은 날짜를 선택해 주세요." : mutationError instanceof ApiError && mutationError.error?.message ? mutationError.error.message : "최신 일정을 불러온 뒤 다시 미리보기해 주세요."}</p><Button variant="outline" className="mt-3" onClick={() => void refresh()}><RefreshCw size={16}/>최신 일정 불러오기</Button></div>}
      {preview && <PreviewCard preview={preview} busy={confirmMutation.isPending} onCancel={() => { clearPendingPreview(course.curriculumId); setPreview(null); }} onConfirm={() => confirmMutation.mutate()} />}
    </section>
    {templatesQuery.isError && <div role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-800">다른 학습 과정 목록을 불러오지 못했어요. <Button variant="outline" onClick={() => void templatesQuery.refetch()}>다시 불러오기</Button></div>}
    {availableTemplates.length > 0 && <SwitchCard course={course} templates={availableTemplates} busy={switchMutation.isPending}
      error={switchMutation.error} onSwitch={submitSwitch} />}
    {templatesQuery.data?.some(item => item.templateId === course.templateId) && <RestartCard course={course} busy={restartMutation.isPending} error={restartMutation.error}
      onRestart={startDate => restartMutation.mutate(startDate)} />}
  </div>;
}

type RoadmapMonthView = ReturnType<typeof toRoadmapViewModel>["months"][number];
type RoadmapWeekView = RoadmapMonthView["weeks"][number];

function RoadmapMonth({ month, curriculumId, initiallyOpen }: { month: RoadmapMonthView; curriculumId: string; initiallyOpen: boolean }) {
  const [open, setOpen] = useState(initiallyOpen);
  return <details className="group min-w-0 rounded-2xl border border-slate-200 p-4" open={open} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary className="flex min-h-11 cursor-pointer list-none items-center gap-3">
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-blue-600 text-sm font-extrabold text-white">{month.monthNo}</span>
      <span className="min-w-0 flex-1"><span className="block text-sm font-bold text-slate-900">{month.title}</span><span className="text-xs text-slate-500">{month.period} · {month.completedSessionCount}/{month.requiredSessionCount} 세션</span></span>
      <strong className="shrink-0 text-sm text-blue-700">{month.progressLabel}</strong><ChevronDown className="shrink-0 transition group-open:rotate-180" size={18}/>
    </summary>
    {open && <div className="mt-3 grid gap-3 border-t border-slate-100 pt-3">
      {month.weeks.length === 1
        ? <WeekDays curriculumId={curriculumId} week={month.weeks[0]} />
        : month.weeks.map((week, index) => <RoadmapWeek key={week.weekNo} curriculumId={curriculumId} week={week} initiallyOpen={index === 0} />)}
    </div>}
  </details>;
}

function RoadmapWeek({ week, curriculumId, initiallyOpen }: { week: RoadmapWeekView; curriculumId: string; initiallyOpen: boolean }) {
  const [open, setOpen] = useState(initiallyOpen);
  return <details className="group/week min-w-0 rounded-xl border border-slate-200 bg-slate-50 p-3" open={open} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary className="flex min-h-11 cursor-pointer list-none items-center gap-3">
      <span className="min-w-0 flex-1"><span className="block text-sm font-bold text-slate-900">{week.title}</span><span className="text-xs text-slate-500">{week.period}</span></span>
      <strong className="shrink-0 text-xs text-slate-600">{week.progressLabel}</strong><ChevronDown className="shrink-0 transition group-open/week:rotate-180" size={16}/>
    </summary>
    {open && <div className="mt-2 border-t border-slate-200 pt-3"><WeekDays curriculumId={curriculumId} week={week} /></div>}
  </details>;
}

function WeekDays({ week, curriculumId }: { week: RoadmapWeekView; curriculumId: string }) {
  const detail = useQuery(roadmapWeekQuery(curriculumId, week.weekNo));
  if (detail.isPending) return <p role="status" className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">날짜별 학습을 불러오는 중이에요.</p>;
  if (detail.isError) return <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">날짜별 학습을 불러오지 못했어요. <button type="button" className="font-bold underline" onClick={() => void detail.refetch()}>다시 시도</button></div>;
  if (detail.data.learningDays.length === 0) return <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">이 주차에 배정된 학습일이 없어요.</p>;
  return <ol className="grid gap-3">{detail.data.learningDays.map(day => <li key={day.date} className="min-w-0 rounded-xl bg-slate-50 p-4 dark:bg-[#1b2638]">
    <div className="flex flex-wrap items-start justify-between gap-2"><div className="min-w-0"><p className="text-xs font-bold text-blue-700 dark:text-blue-300">{formatDate(day.date)}</p><h3 className="mt-1 text-sm font-extrabold text-slate-900">{day.title}</h3></div><span className="rounded-full bg-white px-2.5 py-1 text-xs font-bold text-slate-600 dark:bg-[#24344c] dark:text-slate-200">{day.status === "COMPLETED" ? "완료" : day.status === "IN_PROGRESS" ? "학습 중" : "시작 전"} · {day.completedSessionCount}/{day.totalSessionCount}</span></div>
    {day.description && <p className="mt-2 text-xs leading-5 text-slate-600">{day.description}</p>}
    <ul className="mt-3 grid gap-2 sm:grid-cols-2">{day.sessions.map(session => <li key={session.sessionId}><Link href={`/workspace/learning/${session.sessionId}`} className="flex min-h-11 items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-blue-700 hover:border-blue-300 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-600 dark:bg-[#17243a] dark:text-blue-200"><span className="min-w-0">{session.title}</span><span className="shrink-0">{session.status === "COMPLETED" ? "완료" : `${session.estimatedMinutes}분`} →</span></Link></li>)}</ul>
  </li>)}</ol>;
}

function SwitchCard({ course, templates, busy, error, onSwitch }: { course: CurriculumDto; templates: CurriculumTemplateDto[]; busy: boolean; error: Error | null; onSwitch: (templateId: string, date: string) => void }) {
  const today = seoulToday();
  const latest = maxStartDate(today);
  const [templateId, setTemplateId] = useState(templates[0]?.templateId ?? "");
  const [startDate, setStartDate] = useState(today);
  const [confirmed, setConfirmed] = useState(false);
  const chosen = templates.find(item => item.templateId === templateId) ?? templates[0];
  return <section aria-labelledby="switch-course" className="rounded-3xl border border-blue-200 bg-blue-50/60 p-5 dark:bg-[#17243a] sm:p-7">
    <h2 id="switch-course" className="text-lg font-extrabold text-slate-950">다른 학습 과정으로 바꾸기</h2>
    <p className="mt-2 text-sm leading-6 text-slate-700">현재 과정을 종료하고 배포된 학습 과정의 첫날부터 시작합니다. 기존 진도·답안·AI 사용 기록은 이전 과정에 보관됩니다. 새 과정에는 이전 답안을 가져오지 않아요.</p>
    <p className="mt-2 text-xs text-slate-600">현재 과정: {course.title} · 배정된 학습일 {course.totalLearningDays}일</p>
    <div className="mt-5 grid gap-4 sm:grid-cols-2"><label className="grid gap-2 text-sm font-bold text-slate-800">새 학습 과정
      <select value={chosen?.templateId ?? ""} onChange={event => { setTemplateId(event.target.value); setConfirmed(false); }} className="min-h-11 rounded-xl border border-blue-300 bg-white px-3 font-normal">
        {templates.map(item => <option key={item.templateId} value={item.templateId}>{item.title}</option>)}
      </select>
    </label><label className="grid gap-2 text-sm font-bold text-slate-800">새 시작일
      <input type="date" min={today} max={latest} value={startDate} onChange={event => { setStartDate(event.target.value); setConfirmed(false); }} className="min-h-11 rounded-xl border border-blue-300 bg-white px-3 font-normal" />
    </label></div>
    {chosen && <p className="mt-3 text-sm text-slate-700">{chosen.durationMonths}개월 · {chosen.totalLearningDays}학습일 · 필수 세션 {chosen.totalRequiredSessions}개. 하루 학습 시간과 쉬는 요일은 현재 설정을 유지합니다.</p>}
    <label className="mt-4 flex items-start gap-3 text-sm text-slate-700"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} className="mt-1" />현재 과정을 종료하고 선택한 과정의 1일차부터 시작할게요.</label>
    <Button className="mt-4" disabled={busy || !confirmed || !chosen || !startDate || startDate < today || startDate > latest}
      onClick={() => onSwitch(chosen.templateId, startDate)}>{busy ? "새 과정 만드는 중…" : "선택한 과정으로 바꾸기"}</Button>
    {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-800">{error instanceof ApiError && error.status === 412 ? "과정 상태가 바뀌었어요. 새로고침한 뒤 다시 확인해 주세요." : "전환 결과를 확인하지 못했어요. 현재 과정을 새로고침해 확인하거나 같은 조건으로 다시 시도해 주세요."}</p>}
  </section>;
}

function RestartCard({ course, busy, error, onRestart }: { course: CurriculumDto; busy: boolean; error: Error | null; onRestart: (date: string) => void }) {
  const today = seoulToday();
  const latest = new Date(Date.parse(`${today}T00:00:00Z`) + 90 * 86400000).toISOString().slice(0, 10);
  const [startDate, setStartDate] = useState(today);
  const [confirmed, setConfirmed] = useState(false);
  return <section aria-labelledby="restart-course" className="rounded-3xl border border-amber-200 bg-amber-50/60 p-5 dark:bg-[#28261e] sm:p-7">
    <h2 id="restart-course" className="text-lg font-extrabold text-slate-950">첫날부터 다시 시작</h2>
    <p className="mt-2 text-sm leading-6 text-slate-700">현재 과정의 진도·답안·시험 기록을 새 과정에 가져오지 않고, 선택한 날짜에 1일차부터 다시 시작합니다. 기존 기록은 이전 과정에 보관되고, 같은 학습에 맞는 AI 생성 문제는 새 답안으로 다시 풀 수 있어요.</p>
    <p className="mt-2 text-xs text-slate-600">현재 과정: {course.title} · 시작일 {formatDate(course.startDate)}</p>
    <label className="mt-5 grid max-w-xs gap-2 text-sm font-bold text-slate-800">새 시작일
      <input type="date" min={today} max={latest} value={startDate} onChange={event => setStartDate(event.target.value)} className="min-h-11 rounded-xl border border-amber-300 bg-white px-3 font-normal" />
    </label>
    <label className="mt-4 flex items-start gap-3 text-sm text-slate-700"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} className="mt-1" />현재 과정의 진행 상태를 끝내고 새 과정에서 첫날부터 시작할게요.</label>
    <Button className="mt-4" disabled={busy || !confirmed || !startDate || startDate < today || startDate > latest}
      onClick={() => onRestart(startDate)}>{busy ? "새 일정 만드는 중…" : "첫날부터 다시 시작"}</Button>
    {error && <div role="alert" className="mt-4 text-sm font-semibold text-rose-800">{error instanceof ApiError && error.status === 412 ? "일정이 변경됐어요. 페이지를 새로고침한 뒤 다시 시도해 주세요." : "다시 시작 결과를 확인하지 못했어요. 최신 일정을 새로고침해 확인해 주세요."}</div>}
  </section>;
}

function PreviewCard({ preview, busy, onCancel, onConfirm }: { preview: SchedulePreviewDto; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <aside aria-label="일정 변경 미리보기" className="mt-5 rounded-2xl border-2 border-blue-200 bg-blue-50 p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold text-blue-700">확정 전 미리보기 · {actionLabels[preview.action]}</p><h3 className="mt-1 text-lg font-extrabold text-blue-950">{preview.affectedLearningDayCount}개 학습일이 이동해요</h3></div><span className="rounded-full bg-white px-3 py-1 text-xs font-bold text-blue-700">{new Date(preview.expiresAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}까지</span></div>
    {preview.targetDate && <p className="mt-3 text-sm font-bold text-blue-900">대상 날짜: {formatDate(preview.targetDate)}{preview.action === "REST_ADD" && preview.reason ? ` · 사유: ${reasonLabels[preview.reason]}` : ""}</p>}
    <dl className="mt-4 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-slate-500">기존 종료일</dt><dd className="font-bold text-slate-900">{formatDate(preview.previousEndDate)}</dd></div><div><dt className="text-slate-500">새 종료일</dt><dd className="font-bold text-slate-900">{formatDate(preview.newEndDate)}</dd></div></dl>
    {preview.moves.length > 0 && <ul className="mt-4 max-h-36 space-y-1 overflow-auto rounded-xl bg-white p-3 text-xs text-slate-600">{preview.moves.slice(0, 20).map(move => <li key={move.learningDayId}>{formatDate(move.previousDate)} → {formatDate(move.newDate)}</li>)}</ul>}
    {preview.warnings.length > 0 && <ul className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">{preview.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
    <p className="mt-4 text-xs leading-5 text-slate-600">시작했거나 완료한 학습은 이동하지 않습니다. 확정 후에는 되돌리기 대신 새 변경안을 만들어야 해요.</p>
    <div className="mt-4 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><Button variant="outline" onClick={onCancel} disabled={busy}>취소</Button><Button onClick={onConfirm} disabled={busy}>{busy ? "반영 중…" : "이대로 일정 확정"}</Button></div>
  </aside>;
}
