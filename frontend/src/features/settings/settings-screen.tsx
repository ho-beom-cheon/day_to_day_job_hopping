"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, BookOpen, Check, ChevronRight, Cloud, LogOut, MessageSquare, Moon, Palette, ShieldCheck, Sun, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import type { CurrentUserDto } from "@/api/contract";
import { Button } from "@/components/ui/button";
import { HelpHint, type HelpContent } from "@/components/ui/help-hint";
import { StatePanel } from "@/components/ui/state-panel";
import { useTheme, type Theme } from "@/components/theme-provider";
import { integrationKeys, notionQuery, pushQuery, settingsQuery, slackQuery, updateSlack } from "@/features/integrations/queries";
import type { NotionConnection, NotificationSettings, SlackConnection } from "@/features/integrations/contract";
import { NotificationPreferencesForm } from "@/features/integrations/notification-preferences-form";
import { WebPushCard } from "@/features/integrations/web-push-card";
import { currentSessionQuery, logout, sessionKeys, updateCurrentUser } from "@/features/session/queries";
import { StudyPreferencesForm } from "./study-preferences-form";

export function SettingsScreen() {
  const session = useQuery(currentSessionQuery);
  const settings = useQuery(settingsQuery);
  const notion = useQuery(notionQuery);
  const slack = useQuery(slackQuery);
  const push = useQuery(pushQuery);
  const queries = [session, settings, notion, slack, push];

  if (queries.some(query => query.isPending)) return <StatePanel state="loading" />;
  if (queries.some(query => query.isError)) return <StatePanel state="error" onRefresh={() => void Promise.all(queries.map(query => query.refetch()))} />;

  return <div className="mx-auto max-w-6xl pb-10">
    <header className="relative overflow-hidden rounded-[30px] border border-slate-200 bg-white px-5 py-7 shadow-sm dark:border-slate-700/70 dark:bg-[#101722] sm:px-8 sm:py-9">
      <div className="absolute -right-12 -top-20 size-56 rounded-full bg-blue-100/60 blur-3xl dark:bg-blue-500/10" />
      <div className="relative"><p className="text-xs font-black tracking-[.18em] text-blue-600 dark:text-blue-400">MY WORKSPACE</p><h1 className="mt-2 text-3xl font-black tracking-[-.045em] text-[#0a1837] dark:text-slate-50 sm:text-4xl">설정</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">내 학습 공간과 알림, 연결된 서비스를 한곳에서 관리하세요.</p></div>
    </header>

    <div className="mt-6 grid min-w-0 gap-5">
      <ProfileSection user={session.data!} />
      <AppearanceSection />
      <LearningSection hasCurriculum={session.data!.curriculumId !== null} />
      <NotificationSection value={settings.data!} />
      <div id="web-push" className="scroll-mt-24"><WebPushCard config={push.data!} settings={settings.data!} /></div>
      <IntegrationSection notion={notion.data!} slack={slack.data!} />
      <AccountSection />
    </div>
  </div>;
}

function Section({ id, icon: Icon, title, description, help, children }: { id: string; icon: typeof UserRound; title: string; description: string; help?: HelpContent; children: ReactNode }) {
  return <section id={id} className="scroll-mt-24 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700/70 dark:bg-[#101722] sm:p-7">
    <div className="flex items-start gap-4"><span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300"><Icon size={21}/></span><div><div className="flex items-center gap-2"><h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50 sm:text-xl">{title}</h2>{help && <HelpHint label={title} {...help} />}</div><p className="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">{description}</p></div></div>
    <div className="mt-6">{children}</div>
  </section>;
}

function ProfileSection({ user }: { user: CurrentUserDto }) {
  const client = useQueryClient();
  const [nickname, setNickname] = useState(user.nickname);
  const save = useMutation({ mutationFn: updateCurrentUser, onSuccess: next => client.setQueryData(sessionKeys.current(), next) });
  const valid = nickname.trim().length >= 1 && nickname.trim().length <= 40;
  return <Section id="profile" icon={UserRound} title="계정 및 프로필" description="서비스에 표시되는 이름과 로그인 계정을 확인합니다.">
    <div className="grid gap-5 sm:grid-cols-[auto_1fr] sm:items-start"><div className="grid size-20 place-items-center overflow-hidden rounded-[24px] bg-gradient-to-br from-violet-500 to-blue-600 text-2xl font-black text-white shadow-lg shadow-blue-500/15" title={user.profileImageUrl ? "Google 프로필 이미지가 등록된 계정" : undefined}>{user.nickname.slice(0, 1)}</div><div className="grid gap-4"><label className="grid gap-2"><span className="text-xs font-extrabold text-slate-500 dark:text-slate-400">표시 이름</span><input value={nickname} maxLength={40} onChange={event => setNickname(event.target.value)} className="min-h-12 rounded-2xl border border-slate-200 bg-slate-50 px-4 text-sm font-bold text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-[#182233] dark:text-slate-100 dark:focus:ring-blue-500/10" /></label><div><p className="text-xs font-extrabold text-slate-500 dark:text-slate-400">로그인 이메일</p><p className="mt-2 break-all text-sm font-semibold text-slate-700 dark:text-slate-300">{user.email}</p></div><div className="flex flex-wrap items-center gap-3"><Button disabled={!valid || nickname.trim() === user.nickname || save.isPending} onClick={() => save.mutate({ nickname: nickname.trim(), revision: user.revision })}>{save.isPending ? "저장 중" : "변경 저장"}</Button>{save.isSuccess && <span className="flex items-center gap-1 text-xs font-bold text-emerald-600 dark:text-emerald-400"><Check size={15}/>저장됐어요</span>}{save.isError && <span className="text-xs font-bold text-rose-600">저장하지 못했습니다. 새로고침 후 다시 시도해 주세요.</span>}</div></div></div>
  </Section>;
}

function AppearanceSection() {
  const { theme, setTheme } = useTheme();
  return <Section id="appearance" icon={Palette} title="화면 설정" description="눈에 편한 화면을 선택하면 이 기기에 계속 유지됩니다.">
    <div className="grid gap-3 sm:grid-cols-2"><ThemeOption theme="light" current={theme} setTheme={setTheme} icon={Sun} title="라이트 모드" detail="밝고 선명한 기본 화면"/><ThemeOption theme="dark" current={theme} setTheme={setTheme} icon={Moon} title="다크 모드" detail="깊은 네이비와 은은한 블루"/></div>
  </Section>;
}

function ThemeOption({ theme, current, setTheme, icon: Icon, title, detail }: { theme: Theme; current: Theme; setTheme: (theme: Theme) => void; icon: typeof Sun; title: string; detail: string }) {
  const selected = theme === current;
  return <button type="button" aria-pressed={selected} onClick={() => setTheme(theme)} className={`relative flex min-h-24 items-center gap-4 rounded-2xl border p-4 text-left transition ${selected ? "border-blue-500 bg-blue-50 ring-4 ring-blue-100 dark:bg-blue-500/10 dark:ring-blue-500/10" : "border-slate-200 hover:border-blue-300 dark:border-slate-700 dark:bg-[#151f2f]"}`}><span className={`grid size-12 shrink-0 place-items-center rounded-2xl ${theme === "dark" ? "bg-[#0a101b] text-blue-300 ring-1 ring-slate-700" : "bg-white text-amber-500 shadow-sm"}`}><Icon size={22}/></span><span><strong className="block text-sm text-slate-900 dark:text-slate-100">{title}</strong><span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{detail}</span></span>{selected && <span className="absolute right-3 top-3 grid size-5 place-items-center rounded-full bg-blue-600 text-white"><Check size={13}/></span>}</button>;
}

function LearningSection({ hasCurriculum }: { hasCurriculum: boolean }) {
  return <Section id="learning" icon={BookOpen} title="학습 설정" description="현재 학습 과정과 일정을 확인합니다.">
    <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-[#151f2f] sm:flex-row sm:items-center sm:justify-between"><div><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-black ${hasCurriculum ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300" : "bg-amber-100 text-amber-700"}`}>{hasCurriculum ? "학습 중" : "과정 선택 필요"}</span><p className="mt-3 text-sm font-bold text-slate-900 dark:text-slate-100">{hasCurriculum ? "학습 과정이 연결되어 있어요." : "학습 과정을 먼저 선택해 주세요."}</p><p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">일정 변경은 캘린더에서 휴식일과 보충 일정을 미리 확인한 뒤 적용할 수 있습니다.</p></div><Button asChild variant="outline" className="shrink-0 dark:border-slate-600 dark:bg-[#1b2638] dark:text-slate-200"><Link href={hasCurriculum ? "/roadmap" : "/onboarding"}>과정 확인<ChevronRight size={16}/></Link></Button></div>
    <StudyPreferencesForm />
  </Section>;
}

function NotificationSection({ value }: { value: NotificationSettings }) {
  return <Section id="notifications" icon={BellRing} title="알림 설정" description="중요한 학습 순간만 놓치지 않도록 알려드려요." help={{
    summary: "학습·시험·리포트 알림의 종류와 시간을 저장해요.",
    items: [{ term: "설정 저장", description: "선택한 조건은 계정에 보관돼요." }, { term: "웹 푸시", description: "알림을 받을 기기마다 별도로 연결해야 해요." }],
    note: "정기 알림은 별도로 수신을 켜고 저장한 뒤부터 생성돼요. 기존 설정만으로는 시작되지 않아요.",
  }}>
    <NotificationPreferencesForm value={value}/>
  </Section>;
}

function IntegrationSection({ notion, slack }: { notion: NotionConnection; slack: SlackConnection }) {
  const client = useQueryClient();
  const saveSlack = useMutation({ mutationFn: updateSlack, onSuccess: () => client.invalidateQueries({ queryKey: integrationKeys.all }) });
  return <Section id="integrations" icon={Cloud} title="외부 서비스 연동" description="학습 기록의 원본은 항상 이 앱에 안전하게 보관됩니다." help={{
    summary: "연결한 서비스로 학습 기록이나 알림을 보낼 수 있어요.",
    items: [{ term: "Notion", description: "연결 후 저장 대상을 정하면 기록 동기화에 사용해요." }, { term: "Slack", description: "관리자가 연결한 채널에 선택한 알림을 보내요." }],
    note: "연결 상태와 전송 이력은 '연결 관리'에서 확인하세요. 원본 기록은 이 앱에 남아요.",
  }}>
    <div className="grid gap-3 sm:grid-cols-2"><IntegrationCard icon={Cloud} name="Notion" status={notion.connected ? notion.workspaceName ?? "연결됨" : "연결 필요"} active={notion.connected}><p>{notion.connected ? (notion.targetsConfigured ? "동기화할 대상이 준비되어 있어요." : "동기화 대상을 설정해 주세요.") : "Notion 연결 기능은 서버 설정 후 사용할 수 있어요."}</p><Button asChild variant="outline" className="mt-4 w-full dark:border-slate-600 dark:bg-[#1b2638] dark:text-slate-200"><Link href="/settings/integrations">Notion 연결 관리<ChevronRight size={16}/></Link></Button></IntegrationCard><IntegrationCard icon={MessageSquare} name="Slack" status={slack.configured ? (slack.enabled ? "발송 중" : "꺼짐") : "관리자 설정 필요"} active={slack.configured && slack.enabled}><p>{slack.channelName ? `${slack.channelName} 채널에 학습 소식을 전송합니다.` : "웹훅은 서버의 안전한 비밀 저장소에서 관리합니다."}</p><Button className="mt-4 w-full" disabled={!slack.configured || saveSlack.isPending} onClick={() => saveSlack.mutate({ body: { enabled: !slack.enabled, eventTypes: slack.eventTypes.length ? slack.eventTypes : ["STUDY_REMINDER", "WEEKLY_REPORT", "INTEGRATION_FAILURE"] }, revision: slack.revision })}>{slack.enabled ? "Slack 알림 끄기" : "Slack 알림 켜기"}</Button><Button asChild variant="outline" className="mt-2 w-full"><Link href="/settings/integrations">Slack 전송 이력 및 관리<ChevronRight size={16}/></Link></Button></IntegrationCard></div>
    {saveSlack.isError && <p role="alert" className="mt-4 text-sm text-rose-600">Slack 설정을 저장하지 못했어요. 연결 관리에서 최신 상태를 확인해 주세요.</p>}
  </Section>;
}

function IntegrationCard({ icon: Icon, name, status, active, children }: { icon: typeof Cloud; name: string; status: string; active: boolean; children: ReactNode }) {
  return <article className="rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-[#151f2f]"><div className="flex items-center justify-between gap-3"><span className="flex items-center gap-2 font-black text-slate-900 dark:text-slate-100"><span className="grid size-9 place-items-center rounded-xl bg-white text-blue-600 shadow-sm dark:bg-[#0c1420] dark:text-blue-300"><Icon size={18}/></span>{name}</span><span className={`rounded-full px-2.5 py-1 text-[11px] font-black ${active ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300" : "bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300"}`}>{status}</span></div><div className="mt-4 text-xs leading-5 text-slate-500 dark:text-slate-400">{children}</div></article>;
}

function AccountSection() {
  const router = useRouter();
  const client = useQueryClient();
  const signOut = useMutation({ mutationFn: logout, onSuccess: () => { client.clear(); router.replace("/login"); router.refresh(); } });
  return <Section id="account" icon={ShieldCheck} title="로그아웃 및 계정 관리" description="공용 기기에서는 학습을 마친 뒤 로그아웃해 주세요.">
    <div className="grid gap-3 sm:grid-cols-2"><button type="button" onClick={() => signOut.mutate()} disabled={signOut.isPending} className="flex min-h-16 items-center justify-between rounded-2xl border border-slate-200 px-4 text-left transition hover:border-blue-300 hover:bg-blue-50 disabled:opacity-50 dark:border-slate-700 dark:hover:bg-blue-500/10"><span className="flex items-center gap-3 text-sm font-bold text-slate-800 dark:text-slate-200"><LogOut size={19} className="text-blue-600 dark:text-blue-400"/>{signOut.isPending ? "로그아웃 중" : "로그아웃"}</span><ChevronRight size={17} className="text-slate-400"/></button><div className="flex min-h-16 items-center justify-between rounded-2xl border border-slate-200 px-4 opacity-60 dark:border-slate-700"><span><strong className="block text-sm text-slate-700 dark:text-slate-300">계정 삭제</strong><span className="mt-1 block text-xs text-slate-500">준비 중</span></span><ShieldCheck size={18} className="text-slate-400"/></div></div>{signOut.isError && <p className="mt-4 text-xs font-bold text-rose-600">로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>}
  </Section>;
}
