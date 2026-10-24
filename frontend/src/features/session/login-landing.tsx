"use client";
import { ArrowRight, BookOpen, CalendarDays, GraduationCap, ShieldCheck, Sparkles } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/brand-logo";
import { currentSessionQuery } from "./queries";

export function LoginLanding() {
  const session = useQuery(currentSessionQuery);
  const anonymous = session.error instanceof ApiError && session.error.status === 401;
  const destination = session.data?.curriculumId ? "/workspace" : "/onboarding";
  return <main className="theme-app mx-auto min-h-screen max-w-6xl break-keep px-5 py-7 sm:px-10 sm:py-10">
    <header className="flex items-center justify-between border-b border-blue-100 pb-6 dark:border-slate-700">
      <Link href="/" aria-label="Daily Leap 홈"><BrandLogo/></Link>
      <span className="hidden rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300 sm:inline-flex sm:items-center sm:gap-1.5"><ShieldCheck size={14}/>개인 학습 기록 보호</span>
    </header>
    <section className="mt-8 grid overflow-hidden rounded-[2rem] border border-white bg-gradient-to-br from-blue-100 via-white to-violet-100 shadow-sm dark:border-slate-700 dark:from-[#1d304d] dark:via-[#19263d] dark:to-[#241f38] lg:grid-cols-[1.35fr_0.65fr]">
      <div className="p-7 sm:p-12 lg:p-14"><p className="flex items-center gap-2 text-sm font-semibold text-blue-700"><Sparkles size={16}/>작은 습관이 만드는 큰 변화</p><h1 className="mt-5 text-4xl leading-tight font-extrabold tracking-[-0.04em] text-slate-950 sm:text-6xl">오늘의 배움이,<br/>내일의 가능성으로.</h1><p className="mt-6 max-w-lg text-sm leading-7 text-slate-600 sm:text-base">배정된 학습을 내 속도로 차근차근. 개념 학습부터 문제 풀이, 시험과 복습까지 하나의 흐름으로 이어가요.</p>
        <div className="mt-5 max-w-lg rounded-2xl border border-blue-200 bg-white/80 p-4 text-sm leading-6 text-slate-700 dark:border-slate-600 dark:bg-[#1b2940] dark:text-slate-200">
          <p className="font-bold text-slate-950 dark:text-slate-100">과정별 학습 분량을 확인하고 시작해요</p>
          <p className="mt-1">로그인 후 제공 중인 과정의 기간·학습일·필수 세션을 확인할 수 있어요. 시작한 과정의 일정과 진척은 로드맵에서 이어서 살펴보세요.</p>
        </div>
        <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
          {session.data ? <Button asChild className="w-full sm:w-auto"><Link href={destination}>{session.data.curriculumId ? "오늘 학습으로" : "내 과정 만들기"}<ArrowRight size={16}/></Link></Button> : <Button asChild={!session.isPending} className="w-full sm:w-auto" disabled={session.isPending}>{session.isPending ? "로그인 상태 확인 중" : <a href="/oauth2/authorization/google">Google로 계속하기<ArrowRight size={16}/></a>}</Button>}
          {session.isError && !anonymous && <button type="button" onClick={() => void session.refetch()} className="min-h-11 text-sm font-semibold text-slate-600 underline-offset-4 hover:underline">연결 다시 확인</button>}
        </div>
        {anonymous && <p className="mt-4 text-xs leading-5 text-slate-500">허용된 Google 계정으로만 로그인할 수 있어요.</p>}
        {session.isError && !anonymous && <p role="alert" className="mt-4 text-xs leading-5 text-rose-600">로그인 상태를 확인하지 못했어요. 잠시 후 다시 연결해 주세요.</p>}
      </div>
      <div className="grid gap-3 border-t border-white/80 bg-white/45 p-6 dark:border-slate-700 dark:bg-[#121c2c] sm:grid-cols-3 lg:grid-cols-1 lg:border-t-0 lg:border-l lg:p-8">
        {[{icon:CalendarDays,title:"나에게 맞는 일정",text:"학습일과 휴식일을 내 리듬에 맞춰 시작해요."},{icon:BookOpen,title:"매일 이어지는 학습",text:"개념과 문제 풀이를 한 흐름으로 쌓아요."},{icon:GraduationCap,title:"기록으로 보는 성장",text:"완료한 학습과 평가 결과를 안전하게 남겨요."}].map(({icon:Icon,title,text})=><article key={title} className="rounded-2xl border border-white bg-white/85 p-5 dark:border-slate-700 dark:bg-[#1b2940]"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-[#253957]"><Icon size={19}/></span><h2 className="mt-4 text-sm font-extrabold text-slate-900">{title}</h2><p className="mt-2 text-xs leading-5 text-slate-500">{text}</p></article>)}
      </div>
    </section>
    <footer className="py-8 text-center text-xs text-slate-400">서두르지 않아도 괜찮아요. 꾸준함이 실력이 됩니다.</footer>
  </main>;
}
