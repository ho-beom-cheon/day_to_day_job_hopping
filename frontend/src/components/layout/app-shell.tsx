"use client";
import { useQueryClient } from "@tanstack/react-query";
import { Bell, BriefcaseBusiness, ChevronDown, CircleHelp, Gauge, Home, LogOut, MoreHorizontal, Search, Settings, UserRound, X, type LucideIcon } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { logout } from "@/features/session/queries";
import type { SessionViewModel } from "@/features/session/model";
import { NotificationPopover } from "@/features/notifications/notification-popover";
import { BrandLogo } from "@/components/brand-logo";
import { cn } from "@/lib/utils";

const navigation: ReadonlyArray<{ label: string; href: string; icon: LucideIcon; pending: boolean }> = [
  { label: "대시보드", href: "/workspace", icon: Home, pending: false },
  { label: "경력·이력서", href: "/career", icon: BriefcaseBusiness, pending: false },
  { label: "알림", href: "/notifications", icon: Bell, pending: false },
  { label: "AI 사용량", href: "/settings/ai-usage", icon: Gauge, pending: false },
];

const mobileNavigation: ReadonlyArray<{ label: string; href: string; icon: LucideIcon }> = [
  { label: "홈", href: "/workspace", icon: Home },
  { label: "경력", href: "/career", icon: BriefcaseBusiness },
  { label: "알림", href: "/notifications", icon: Bell },
];

const searchablePages = [
  ...navigation.filter(item => !item.pending),
  { label: "설정", href: "/settings", icon: Settings },
  { label: "알림 및 외부 연동", href: "/settings/integrations", icon: Bell },
] as const;

export function AppShell({ session, children }: { session: SessionViewModel; children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [moreOpen, setMoreOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [signingOut, setSigningOut] = useState(false);
  const headerRef = useRef<HTMLDivElement>(null);
  const active = (href: string) => pathname === href || (href === "/workspace" ? pathname.startsWith("/tasks/") : pathname.startsWith(`${href}/`));
  const results = searchablePages.filter(item => item.label.toLocaleLowerCase("ko").includes(query.trim().toLocaleLowerCase("ko")));
  const closeHeaderMenus = () => { setSearchOpen(false); setNotificationsOpen(false); setProfileOpen(false); };
  const openOnly = (menu: "search" | "notifications" | "profile") => {
    setSearchOpen(menu === "search");
    setNotificationsOpen(menu === "notifications");
    setProfileOpen(menu === "profile");
  };
  const moveTo = (href: string) => { closeHeaderMenus(); setQuery(""); router.push(href); };
  const submitSearch = (event: FormEvent) => { event.preventDefault(); if (results[0]) moveTo(results[0].href); };
  const signOut = async () => {
    setSigningOut(true);
    try { await logout(); queryClient.clear(); router.replace("/login"); router.refresh(); }
    finally { setSigningOut(false); }
  };

  useEffect(() => {
    const closeOnOutside = (event: PointerEvent) => { if (!headerRef.current?.contains(event.target as Node)) closeHeaderMenus(); };
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === "Escape") closeHeaderMenus(); };
    document.addEventListener("pointerdown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => { document.removeEventListener("pointerdown", closeOnOutside); document.removeEventListener("keydown", closeOnEscape); };
  }, []);
  return <div className="theme-app min-h-screen lg:grid lg:grid-cols-[188px_minmax(0,1fr)]">
    <aside className="hidden border-r border-blue-100/70 bg-white px-4 py-6 lg:sticky lg:top-0 lg:flex lg:h-screen lg:flex-col">
      <Link href="/workspace" className="rounded-xl px-2 py-1" aria-label="Daily Leap 홈"><BrandLogo/></Link>
      <nav aria-label="주요 메뉴" className="mt-6 grid gap-1">
        {navigation.map(({label,href,icon:Icon,pending}) => pending
          ? <span key={label} aria-disabled="true" title="준비 중" className="flex min-h-10 items-center gap-3 rounded-xl px-3 text-sm font-semibold text-slate-400"><Icon size={19}/>{label}</span>
          : <Link key={label} href={href} aria-current={active(href)?"page":undefined} className={cn("flex min-h-10 items-center gap-3 rounded-xl px-3 text-sm font-bold",active(href)?"bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300":"text-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800")}><Icon size={19}/>{label}</Link>)}
      </nav>
      <nav aria-label="지원 메뉴" className="mt-auto grid gap-1 border-t border-slate-100 pt-5"><Link href="/settings" className="flex min-h-10 items-center gap-3 px-3 text-sm font-semibold text-slate-500 hover:text-blue-700"><Settings size={18}/>설정</Link><span className="flex min-h-10 items-center gap-3 px-3 text-sm font-semibold text-slate-500"><CircleHelp size={18}/>고객센터</span></nav>
      <blockquote className="mt-6 px-3 text-xs leading-5 font-semibold text-slate-500">“꾸준한 오늘이,<br/>더 좋은 내일을 만든다.”<footer className="mt-1 text-[10px] text-slate-400">— Daily Leap</footer></blockquote>
    </aside>
    <div className="min-w-0 pb-20 lg:pb-0">
      <header ref={headerRef} className="sticky top-0 z-10 flex h-[64px] items-center justify-between border-b border-blue-100/70 bg-white/95 px-5 backdrop-blur lg:px-8">
        <Link href="/workspace" className="lg:hidden" aria-label="Daily Leap 홈"><BrandLogo compact/></Link>
        <form onSubmit={submitSearch} className="relative hidden w-full max-w-lg lg:block">
          <label className="flex min-h-11 items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 text-slate-500 transition focus-within:border-blue-400 focus-within:bg-white focus-within:ring-4 focus-within:ring-blue-100"><Search size={18}/><span className="sr-only">메뉴 검색</span><input value={query} onFocus={() => openOnly("search")} onChange={event => { setQuery(event.target.value); openOnly("search"); }} placeholder="무엇을 공부하고 싶으신가요?" className="min-w-0 flex-1 bg-transparent text-sm font-semibold text-slate-800 outline-none placeholder:text-slate-400"/></label>
          {searchOpen && <section aria-label="검색 결과" className="absolute left-0 right-0 top-[calc(100%+10px)] overflow-hidden rounded-2xl border border-slate-200 bg-white p-2 shadow-xl"><p className="px-3 py-2 text-[11px] font-extrabold text-slate-400">이동할 화면</p>{results.length ? results.map(({label,href,icon:Icon}) => <button key={href} type="button" onClick={() => moveTo(href)} className="flex min-h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-sm font-bold text-slate-700 hover:bg-blue-50 hover:text-blue-700"><Icon size={18}/>{label}</button>) : <p className="px-3 py-5 text-center text-sm text-slate-500">일치하는 메뉴가 없어요.</p>}</section>}
        </form>
        <div className="relative flex items-center gap-2 sm:gap-3">
          <NotificationPopover open={notificationsOpen} onToggle={() => notificationsOpen ? setNotificationsOpen(false) : openOnly("notifications")} onNavigate={closeHeaderMenus}/>
          <button type="button" aria-label="계정 메뉴" aria-expanded={profileOpen} onClick={() => openOnly("profile")} className="flex min-h-11 items-center gap-2 rounded-2xl px-1.5 hover:bg-slate-50 dark:hover:bg-slate-800 sm:px-2"><span className="relative size-9 overflow-hidden rounded-full border-2 border-white bg-blue-50 shadow-sm"><Image src={session.profileImageUrl || "/assets/dashboard/hobeom-hero.png"} alt={`${session.displayName} 프로필`} fill sizes="36px" className="object-cover"/></span><span className="hidden text-sm font-black text-[#0a1837] sm:block">{session.displayName}님</span><ChevronDown size={17} className={`hidden text-slate-500 transition sm:block ${profileOpen ? "rotate-180" : ""}`}/></button>
          {profileOpen && <section aria-label="계정 메뉴" className="absolute right-0 top-[calc(100%+10px)] w-64 rounded-2xl border border-slate-200 bg-white p-3 shadow-xl"><div className="border-b border-slate-100 px-3 pb-3 pt-1"><p className="truncate text-sm font-black text-[#0a1837]">{session.displayName}</p><p className="mt-1 truncate text-xs text-slate-500">{session.email}</p></div><div className="mt-2 grid gap-1"><HeaderLink href="/settings#profile" icon={UserRound} title="프로필 설정" onNavigate={closeHeaderMenus}/><HeaderLink href="/settings" icon={Settings} title="환경 설정" onNavigate={closeHeaderMenus}/><button type="button" disabled={signingOut} onClick={() => void signOut()} className="flex min-h-11 items-center gap-3 rounded-xl px-3 text-left text-sm font-bold text-rose-600 hover:bg-rose-50 disabled:opacity-50"><LogOut size={18}/>{signingOut ? "로그아웃 중" : "로그아웃"}</button></div></section>}
        </div>
      </header>
      <main className="mx-auto w-full max-w-[1480px] px-4 py-6 sm:px-6 lg:px-8 xl:py-8">{children}</main>
    </div>
    {moreOpen && <div className="fixed inset-0 z-30 lg:hidden" role="presentation"><button type="button" aria-label="더보기 메뉴 닫기" onClick={() => setMoreOpen(false)} className="absolute inset-0 bg-slate-950/30 backdrop-blur-[2px]"/><section id="mobile-more-menu" role="dialog" aria-modal="true" aria-labelledby="more-menu-title" className="absolute inset-x-0 bottom-0 rounded-t-[28px] bg-white px-5 pb-[calc(5.5rem+env(safe-area-inset-bottom))] pt-5 shadow-2xl"><div className="flex items-center justify-between"><div><p className="text-xs font-bold text-blue-600">전체 메뉴</p><h2 id="more-menu-title" className="mt-1 text-xl font-black text-[#0a1837]">더보기</h2></div><button type="button" aria-label="닫기" onClick={() => setMoreOpen(false)} className="grid size-10 place-items-center rounded-full bg-slate-100 text-slate-600"><X size={20}/></button></div><div className="mt-5 grid grid-cols-3 gap-3">{navigation.slice(1).map(({label,href,icon:Icon,pending}) => pending ? <span key={label} aria-disabled="true" className="flex min-h-20 flex-col items-center justify-center gap-2 rounded-2xl bg-slate-50 text-xs font-bold text-slate-400"><Icon size={21}/>{label}<span className="text-[9px] font-semibold">준비 중</span></span> : <Link key={label} href={href} onClick={() => setMoreOpen(false)} className="flex min-h-20 flex-col items-center justify-center gap-2 rounded-2xl bg-blue-50 text-xs font-bold text-blue-700"><Icon size={21}/>{label}</Link>)}</div><div className="mt-4 grid grid-cols-2 gap-3 border-t border-slate-100 pt-4"><Link href="/settings" onClick={() => setMoreOpen(false)} className="flex min-h-11 items-center justify-center gap-2 rounded-xl bg-slate-50 text-sm font-bold text-slate-600"><Settings size={18}/>설정</Link><span className="flex min-h-11 items-center justify-center gap-2 rounded-xl bg-slate-50 text-sm font-bold text-slate-600"><CircleHelp size={18}/>고객센터</span></div></section></div>}
    <nav aria-label="모바일 주요 메뉴" className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-4 border-t border-slate-200 bg-white px-[max(0.5rem,env(safe-area-inset-left))] pb-[env(safe-area-inset-bottom)] lg:hidden">
      {mobileNavigation.map(({label,href,icon:Icon}) => <Link key={label} href={href} aria-current={active(href)?"page":undefined} className={cn("flex min-h-16 flex-col items-center justify-center gap-1 text-[11px] font-bold",active(href)?"text-blue-700":"text-slate-500")}><Icon size={19}/>{label}</Link>)}
      <button type="button" aria-expanded={moreOpen} aria-controls="mobile-more-menu" onClick={() => setMoreOpen(true)} className={cn("flex min-h-16 flex-col items-center justify-center gap-1 text-[11px] font-bold",moreOpen?"text-blue-700":"text-slate-500")}><MoreHorizontal size={19}/>더보기</button>
    </nav>
  </div>;
}

function HeaderLink({ href, icon: Icon, title, detail, onNavigate }: { href: string; icon: LucideIcon; title: string; detail?: string; onNavigate: () => void }) {
  return <Link href={href} onClick={onNavigate} className="flex min-h-11 items-center gap-3 rounded-xl px-3 py-2 text-slate-700 hover:bg-blue-50 hover:text-blue-700"><span className="grid size-9 shrink-0 place-items-center rounded-xl bg-blue-50 text-blue-600"><Icon size={17}/></span><span className="min-w-0"><strong className="block text-sm">{title}</strong>{detail && <span className="mt-0.5 block text-xs font-medium text-slate-500">{detail}</span>}</span></Link>;
}
