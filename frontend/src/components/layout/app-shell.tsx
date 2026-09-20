"use client";
import { BarChart3, BookOpen, CalendarDays, GraduationCap, Home, NotebookPen, Settings, type LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import type { SessionViewModel } from "@/features/session/model";
import { cn } from "@/lib/utils";

const navigation: ReadonlyArray<{ label: string; href: string; icon: LucideIcon; pending: boolean }> = [
  { label: "오늘", href: "/workspace", icon: Home, pending: false },
  { label: "로드맵", href: "/roadmap", icon: CalendarDays, pending: true },
  { label: "학습", href: "/learning", icon: BookOpen, pending: true },
  { label: "복습", href: "/review", icon: NotebookPen, pending: true },
  { label: "성장", href: "/progress", icon: BarChart3, pending: true },
];

export function AppShell({ session, children }: { session: SessionViewModel; children: ReactNode }) {
  return <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
    <aside className="hidden border-r border-slate-200 bg-white px-5 py-7 lg:flex lg:flex-col">
      <Link href="/workspace" className="flex items-center gap-3 rounded-xl px-2 py-1" aria-label="데일리 이직 홈">
        <span className="grid size-10 place-items-center rounded-xl bg-blue-600 text-white"><GraduationCap size={21} /></span>
        <div><p className="font-extrabold tracking-tight text-slate-950">데일리 이직</p><p className="text-xs text-slate-500">매일 한 걸음</p></div>
      </Link>
      <nav aria-label="주요 메뉴" className="mt-9 grid gap-1">
        {navigation.map(({label,href,icon:Icon,pending}) => pending
          ? <span key={label} aria-disabled="true" className="flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium text-slate-400"><Icon size={19}/>{label}<span className="ml-auto text-[10px]">준비 중</span></span>
          : <Link key={label} href={href} aria-current="page" className="flex min-h-11 items-center gap-3 rounded-xl bg-blue-50 px-3 text-sm font-bold text-blue-700"><Icon size={19}/>{label}</Link>)}
      </nav>
      <div className="mt-auto flex items-center gap-3 rounded-2xl border border-slate-200 p-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-violet-100 font-bold text-violet-700">{session.initials}</span>
        <div className="min-w-0"><p className="truncate text-sm font-bold text-slate-900">{session.displayName}</p><p className="truncate text-xs text-slate-500">{session.email}</p></div>
        <Settings className="ml-auto text-slate-400" size={17}/>
      </div>
    </aside>
    <div className="min-w-0 pb-20 lg:pb-0">
      <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-5 backdrop-blur lg:px-8">
        <Link href="/workspace" className="flex items-center gap-2 font-extrabold lg:hidden"><span className="grid size-9 place-items-center rounded-xl bg-blue-600 text-white"><GraduationCap size={19}/></span>데일리 이직</Link>
        <p className="hidden text-sm font-semibold text-slate-500 lg:block">오늘도 한 걸음씩 성장해요.</p>
        <span className="grid size-9 place-items-center rounded-full bg-violet-100 text-sm font-bold text-violet-700" title={session.displayName}>{session.initials}</span>
      </header>
      <main className="mx-auto w-full max-w-6xl px-5 py-7 sm:px-8 sm:py-9">{children}</main>
    </div>
    <nav aria-label="모바일 주요 메뉴" className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-slate-200 bg-white px-[max(0.5rem,env(safe-area-inset-left))] pb-[env(safe-area-inset-bottom)] lg:hidden">
      {navigation.map(({label,href,icon:Icon,pending}) => pending
        ? <span key={label} aria-disabled="true" className="flex min-h-16 flex-col items-center justify-center gap-1 text-[11px] font-semibold text-slate-400"><Icon size={19}/>{label}</span>
        : <Link key={label} href={href} aria-current="page" className={cn("flex min-h-16 flex-col items-center justify-center gap-1 text-[11px] font-bold text-blue-700")}><Icon size={19}/>{label}</Link>)}
    </nav>
  </div>;
}
