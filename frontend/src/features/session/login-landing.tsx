"use client";
import { ArrowRight, MessageSquareText, NotebookPen, ShieldCheck, Sparkles, Target } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/brand-logo";
import { currentSessionQuery } from "./queries";

export function LoginLanding() {
  const session = useQuery(currentSessionQuery);
  const anonymous = session.error instanceof ApiError && session.error.status === 401;
  return <main className="theme-app mx-auto min-h-screen max-w-6xl break-keep px-5 py-7 sm:px-10 sm:py-10">
    <header className="flex items-center justify-between border-b border-blue-100 pb-6 dark:border-slate-700">
      <Link href="/" aria-label="Daily Leap 홈"><BrandLogo/></Link>
      <span className="hidden rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300 sm:inline-flex sm:items-center sm:gap-1.5"><ShieldCheck size={14}/>개인 학습 기록 보호</span>
    </header>
    <section className="mt-8 grid overflow-hidden rounded-[2rem] border border-white bg-gradient-to-br from-blue-100 via-white to-violet-100 shadow-sm dark:border-slate-700 dark:from-[#1d304d] dark:via-[#19263d] dark:to-[#241f38] lg:grid-cols-[1.35fr_0.65fr]">
      <div className="p-7 sm:p-12 lg:p-14"><p className="flex items-center gap-2 text-sm font-semibold text-blue-700"><Sparkles size={16}/>작은 습관이 만드는 큰 변화</p><h1 className="mt-5 text-4xl leading-tight font-extrabold tracking-[-0.04em] text-slate-950 sm:text-6xl">오늘의 배움이,<br/>내일의 가능성으로.</h1><p className="mt-6 max-w-lg text-sm leading-7 text-slate-600 sm:text-base">금융권 백엔드 면접에 필요한 역량을 매일 30~60분, 과제 하나로 직접 판단하고 답해요. AI가 내 답을 보고 꼬리질문으로 파고들어요.</p>
        <div className="mt-5 max-w-lg rounded-2xl border border-blue-200 bg-white/80 p-4 text-sm leading-6 text-slate-700 dark:border-slate-600 dark:bg-[#1b2940] dark:text-slate-200">
          <p className="font-bold text-slate-950 dark:text-slate-100">날짜에 쫓기지 않는 주간 목표</p>
          <p className="mt-1">빠진 날이 있어도 할 일이 쌓이지 않아요. 내 답과 핵심 카드는 면접 답변집으로 남아요.</p>
        </div>
        <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-center">
          {session.data ? <Button asChild className="w-full sm:w-auto"><Link href="/workspace">오늘 과제로<ArrowRight size={16}/></Link></Button> : <Button asChild={!session.isPending} className="w-full sm:w-auto" disabled={session.isPending}>{session.isPending ? "로그인 상태 확인 중" : <a href="/oauth2/authorization/google">Google로 계속하기<ArrowRight size={16}/></a>}</Button>}
          {session.isError && !anonymous && <button type="button" onClick={() => void session.refetch()} className="min-h-11 text-sm font-semibold text-slate-600 underline-offset-4 hover:underline">연결 다시 확인</button>}
        </div>
        {anonymous && <p className="mt-4 text-xs leading-5 text-slate-500">허용된 Google 계정으로만 로그인할 수 있어요.</p>}
        {session.isError && !anonymous && <p role="alert" className="mt-4 text-xs leading-5 text-rose-600">로그인 상태를 확인하지 못했어요. 잠시 후 다시 연결해 주세요.</p>}
      </div>
      <div className="grid gap-3 border-t border-white/80 bg-white/45 p-6 dark:border-slate-700 dark:bg-[#121c2c] sm:grid-cols-3 lg:grid-cols-1 lg:border-t-0 lg:border-l lg:p-8">
        {[{icon:Target,title:"약한 역량부터",text:"역량맵에서 지금 필요한 과제를 골라 드려요."},{icon:MessageSquareText,title:"파고드는 꼬리질문",text:"면접관처럼 내 답의 빈틈을 묻어요."},{icon:NotebookPen,title:"남는 답변집",text:"고쳐 쓴 핵심 카드가 면접 준비 자료가 돼요."}].map(({icon:Icon,title,text})=><article key={title} className="rounded-2xl border border-white bg-white/85 p-5 dark:border-slate-700 dark:bg-[#1b2940]"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-[#253957]"><Icon size={19}/></span><h2 className="mt-4 text-sm font-extrabold text-slate-900">{title}</h2><p className="mt-2 text-xs leading-5 text-slate-500">{text}</p></article>)}
      </div>
    </section>
    <footer className="py-8 text-center text-xs text-slate-400">서두르지 않아도 괜찮아요. 꾸준함이 실력이 됩니다.</footer>
  </main>;
}
