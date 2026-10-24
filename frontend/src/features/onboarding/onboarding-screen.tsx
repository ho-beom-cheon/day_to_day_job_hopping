"use client";
import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpenCheck, CalendarDays, Check, Clock3, LogOut, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/api";
import { ApiError, createMutationAction } from "@/api/client";
import type { CurrentUserDto } from "@/api/contract";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/brand-logo";
import { HelpHint } from "@/components/ui/help-hint";
import { currentSessionQuery, sessionKeys } from "@/features/session/queries";
import type { CreateCurriculumRequest, Weekday } from "./contract";
import { decodeCurriculumEnvelope } from "./contract";
import { maxStartDate, seoulToday, toggleWeekday, toTemplateViewModel, weekdayOptions } from "./model";
import { curriculumTemplatesQuery } from "./queries";
import { userSettingsQuery } from "@/features/settings/queries";

type Action = ReturnType<typeof createMutationAction<CreateCurriculumRequest>>;

export function OnboardingScreen({ session }: { session: CurrentUserDto }) {
  const router=useRouter(), queryClient=useQueryClient(), templates=useQuery(curriculumTemplatesQuery);
  const [today]=useState(seoulToday), latest=maxStartDate(today);
  const [templateId,setTemplateId]=useState<string|null>(null), [startDate,setStartDate]=useState(today), [minutesOverride,setMinutes]=useState<number|null>(null), [restDaysOverride,setRestDays]=useState<Weekday[]|null>(null);
  const defaults=useQuery({...userSettingsQuery,enabled:!session.curriculumId});
  const minutes=minutesOverride ?? defaults.data?.studyPolicy.dailyStudyMinutes ?? 60;
  const restDays=restDaysOverride ?? defaults.data?.studyPolicy.restWeekdays ?? [];
  const action=useRef<Action|null>(null);
  const mutation=useMutation({mutationFn:async(value:Action)=>(await api.request({path:"/api/v1/curriculums",method:"POST",body:value.getPayload(),idempotencyKey:value.idempotencyKey,decode:decodeCurriculumEnvelope})).data,onSuccess:async curriculum=>{queryClient.setQueryData(currentSessionQuery.queryKey,{...session,curriculumId:curriculum.curriculumId}); await queryClient.invalidateQueries({queryKey:sessionKeys.all}); router.replace("/workspace");}});
  useEffect(()=>{if(session.curriculumId) router.replace("/workspace");},[router,session.curriculumId]);
  const selectedTemplateId=templateId ?? templates.data?.[0]?.templateId ?? null;
  const submit=()=>{if(!selectedTemplateId||!defaults.data||!Number.isInteger(minutes)||minutes<15||minutes>480)return; const next=createMutationAction<CreateCurriculumRequest>({templateId:selectedTemplateId,startDate,studyPolicy:{dailyStudyMinutes:minutes,restDaysPerWeek:restDays.length,restWeekdays:restDays}}); action.current=next; mutation.mutate(next);};
  const retry=()=>{if(action.current) mutation.mutate(action.current);};
  const errorMessage=mutation.error instanceof ApiError ? mutation.error.error?.code==="CURRICULUM_ALREADY_ACTIVE" ? "이미 배정된 과정이 있어요. 최신 사용자 상태를 다시 확인해 주세요." : mutation.error.error?.code==="CONTENT_NOT_READY" || mutation.error.error?.code==="TEMPLATE_NOT_PUBLISHED" ? "선택한 과정은 아직 배정할 수 없어요. 다른 과정을 선택하거나 잠시 후 다시 시도해 주세요." : mutation.error.message : "과정을 만들지 못했어요. 네트워크 연결을 확인해 주세요.";
  if(session.curriculumId) return <main className="mx-auto max-w-xl px-5 py-24"><p className="text-center text-sm text-slate-600">내 학습 공간으로 이동하고 있어요.</p></main>;
  return <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,#dbeafe_0,transparent_32%),radial-gradient(circle_at_top_right,#ede9fe_0,transparent_28%)] px-5 dark:bg-[radial-gradient(circle_at_top_left,#14223a_0,transparent_32%),radial-gradient(circle_at_top_right,#241b3c_0,transparent_28%)] py-6 sm:px-8 sm:py-9">
    <header className="mx-auto flex max-w-6xl items-center justify-between"><Link href="/" aria-label="Daily Leap 홈"><BrandLogo/></Link><span className="hidden min-h-11 items-center gap-2 text-sm font-semibold text-slate-500 sm:flex"><LogOut size={17} className="text-slate-400"/> {session.nickname}</span></header>
    <div className="mx-auto mt-8 grid max-w-6xl gap-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
      <section className="rounded-[2rem] border border-white bg-white/95 p-6 dark:border-slate-700 shadow-xl shadow-blue-950/5 sm:p-9">
        <p className="text-sm font-bold text-blue-700">환영해요, {session.nickname}님</p><h1 className="mt-2 text-3xl font-extrabold tracking-[-0.03em] text-slate-950 sm:text-4xl">나에게 맞는 첫 과정을 만들어요</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">과정과 시작일, 학습 리듬을 정하면 선택한 과정의 학습 일정을 한 번에 배정해요. 시작 후에도 휴식일과 미래 일정은 다시 조정할 수 있어요.</p>
        <div className="mt-8"><div className="flex items-center justify-between"><div className="flex items-center gap-2"><h2 className="font-extrabold text-slate-950">1. 학습 과정 선택</h2><HelpHint label="학습 과정 선택" summary="배포된 학습 과정 중 시작할 과정을 골라요." items={[
          { term: "과정 목록", description: "검토를 마쳐 배포된 과정만 선택할 수 있어요." },
          { term: "선택 후", description: "시작일과 학습 리듬에 맞춰 학습 일정이 만들어져요." },
        ]} note="과정을 만든 뒤에는 로드맵에서 배정된 학습 범위를 확인하세요." /></div>{templates.data && <span className="text-xs text-slate-400">배포된 과정 {templates.data.length}개</span>}</div>
          {templates.isPending && <div className="mt-4 rounded-2xl border border-slate-100 bg-slate-50 p-5 text-sm text-slate-500">과정 목록을 불러오는 중이에요.</div>}
          {templates.isError && <div role="alert" className="mt-4 rounded-2xl border border-rose-100 bg-rose-50 p-5"><p className="text-sm font-bold text-rose-800">과정을 불러오지 못했어요.</p><Button variant="outline" className="mt-3" onClick={()=>void templates.refetch()}>다시 불러오기</Button></div>}
          {templates.data?.length===0 && <div className="mt-4 rounded-2xl border border-amber-100 bg-amber-50 p-5"><p className="text-sm font-bold text-amber-900">지금 선택할 수 있는 과정이 없어요.</p><p className="mt-2 text-xs leading-5 text-amber-800">검토된 학습 콘텐츠가 배포되면 여기에서 시작할 수 있어요. 임시 과정은 자동으로 만들지 않아요.</p></div>}
          <div className="mt-4 grid gap-3">{templates.data?.map(item=>{const vm=toTemplateViewModel(item),selected=selectedTemplateId===item.templateId;return <button type="button" key={item.templateId} onClick={()=>setTemplateId(item.templateId)} aria-pressed={selected} className={`min-h-11 rounded-2xl border p-5 text-left transition ${selected?"border-blue-500 bg-blue-50 ring-2 ring-blue-100":"border-slate-200 bg-white hover:border-blue-200"}`}><div className="flex items-start gap-4"><span className={`grid size-11 shrink-0 place-items-center rounded-xl ${selected?"bg-blue-600 text-white":"bg-slate-100 text-slate-500"}`}>{selected?<Check size={20}/>:<BookOpenCheck size={20}/>}</span><span className="min-w-0"><span className="block font-extrabold text-slate-950">{vm.title}</span><span className="mt-1 block text-xs font-semibold text-blue-700">{vm.durationLabel} · {vm.volumeLabel}</span><span className="mt-2 block text-sm leading-6 text-slate-600">{vm.description}</span></span></div></button>})}</div>
        </div>
        <div className="mt-8 border-t border-slate-100 pt-8"><div className="flex items-center gap-2"><h2 className="font-extrabold text-slate-950">2. 시작일과 학습 리듬</h2><HelpHint label="시작일과 학습 리듬" summary="학습을 시작할 날짜와 매주 반복할 리듬을 정해요." items={[
          { term: "시작일", description: "오늘부터 90일 안에서 첫 학습일을 골라요." },
          { term: "하루 시간", description: "학습일의 기본 권장 시간을 정해요." },
          { term: "쉬는 요일", description: "선택한 요일은 정기 휴식일로 두고 다음 가능한 날에 학습을 배치해요." },
        ]} note="과정을 시작한 뒤 미래 일정은 로드맵에서 미리본 후 조정할 수 있어요." /></div><div className="mt-4 grid gap-5 sm:grid-cols-2"><label className="grid gap-2 text-sm font-bold text-slate-800"><span className="flex items-center gap-2"><CalendarDays size={17} className="text-blue-600"/>시작일</span><input type="date" min={today} max={latest} value={startDate} onChange={event=>setStartDate(event.target.value)} className="min-h-12 rounded-xl border border-slate-200 bg-white px-4 font-normal outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"/><span className="text-xs font-normal text-slate-500">오늘부터 90일 안에서 선택할 수 있어요.</span></label><label className="grid gap-2 text-sm font-bold text-slate-800"><span className="flex items-center gap-2"><Clock3 size={17} className="text-blue-600"/>하루 학습 시간 (분)</span><input type="number" min={15} max={480} step={1} value={Number.isNaN(minutes)?"":minutes} onChange={event=>setMinutes(event.target.valueAsNumber)} className="min-h-12 rounded-xl border border-slate-200 bg-white px-4 font-normal outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"/><span className="text-xs font-normal text-slate-500">학습일의 기본 권장 시간이며 15~480분까지 입력할 수 있어요.</span></label></div>
          <fieldset className="mt-6"><legend className="text-sm font-bold text-slate-800">매주 쉬는 요일 <span className="font-normal text-slate-500">({restDays.length}일)</span></legend><div className="mt-3 grid grid-cols-7 gap-1.5 sm:gap-2">{weekdayOptions.map(day=>{const selected=restDays.includes(day.value);return <button type="button" key={day.value} aria-pressed={selected} onClick={()=>setRestDays(toggleWeekday(restDays,day.value))} className={`min-h-11 rounded-xl border text-sm font-bold ${selected?"border-violet-500 bg-violet-50 text-violet-700":"border-slate-200 bg-white text-slate-500 hover:border-violet-200"}`}>{day.label}</button>})}</div><p className="mt-2 text-xs leading-5 text-slate-500">선택한 요일은 정기 휴식일로 두고, 학습 단위는 다음 가능한 날로 배치해요.</p></fieldset>
        </div>
        {mutation.isError && <div role="alert" className="mt-7 rounded-2xl border border-rose-100 bg-rose-50 p-5"><p className="text-sm font-bold text-rose-800">{errorMessage}</p><div className="mt-3 flex flex-wrap gap-2"><Button variant="outline" onClick={retry}>같은 요청 다시 시도</Button><Button variant="outline" onClick={()=>void queryClient.invalidateQueries({queryKey:sessionKeys.all})}>사용자 상태 새로고침</Button></div></div>}
        {defaults.isPending && <p role="status" className="mt-5 text-sm text-slate-500">저장된 학습 기본값을 불러오는 중이에요.</p>}
        {defaults.isError && <div role="alert" className="mt-5"><p className="text-sm text-rose-700">학습 기본값을 불러오지 못했어요.</p><Button variant="outline" className="mt-2" onClick={()=>void defaults.refetch()}>학습 기본값 다시 불러오기</Button></div>}
        <Button className="mt-8 w-full" disabled={!selectedTemplateId||templates.isPending||mutation.isPending||!startDate||!defaults.data||!Number.isInteger(minutes)||minutes<15||minutes>480} onClick={submit}>{mutation.isPending?"내 학습 일정을 만드는 중":"이 계획으로 시작하기"}</Button>
      </section>
      <aside className="rounded-[2rem] bg-slate-950 p-6 text-white shadow-xl shadow-slate-950/10 sm:p-8 lg:sticky lg:top-8"><p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-300">Your first plan</p><h2 className="mt-4 text-xl font-extrabold">시작 전에 확인해 주세요</h2><ul className="mt-6 grid gap-5 text-sm leading-6 text-slate-300"><li className="flex gap-3"><ShieldCheck className="mt-0.5 shrink-0 text-emerald-400" size={19}/><span><b className="block text-white">검토된 과정만 배정</b>목록 조회 중 콘텐츠를 임의 생성하지 않아요.</span></li><li className="flex gap-3"><CalendarDays className="mt-0.5 shrink-0 text-blue-300" size={19}/><span><b className="block text-white">휴식일을 제외해 자동 배치</b>학습 단위는 가능한 날짜에 하루 하나씩 배정돼요.</span></li><li className="flex gap-3"><Clock3 className="mt-0.5 shrink-0 text-violet-300" size={19}/><span><b className="block text-white">중복 생성 방지</b>처리가 늦어 다시 눌러도 같은 요청으로 안전하게 이어가요.</span></li></ul><div className="mt-7 rounded-2xl bg-white/10 p-4 text-xs leading-5 text-slate-300">과정을 만든 뒤 새로고침하거나 다시 로그인해도 서버에 저장된 배정 상태를 복원합니다.</div></aside>
    </div>
  </main>;
}
