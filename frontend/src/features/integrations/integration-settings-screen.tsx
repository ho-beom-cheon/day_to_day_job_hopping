"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, CheckCircle2, Cloud, FileText, MessageSquare, RefreshCw, ShieldCheck } from "lucide-react";
import { StatePanel } from "@/components/ui/state-panel";
import { integrationKeys, notionQuery, pushQuery, settingsQuery, slackQuery } from "./queries";
import { NotificationPreferencesForm } from "./notification-preferences-form";
import { SlackCard } from "./slack-card";
import { NotionCard } from "./notion-card";
import { OpenRouterCard } from "./openrouter-card";
import { ConnectionReturnNotice } from "./connection-return-notice";
import { WebPushCard } from "./web-push-card";

export function IntegrationSettingsScreen() {
  const client = useQueryClient();
  const settings = useQuery(settingsQuery);
  const notion = useQuery(notionQuery);
  const slack = useQuery(slackQuery);
  const push = useQuery(pushQuery);
  if ([settings, notion, slack, push].some((query) => query.isPending)) return <StatePanel state="loading"/>;
  if ([settings, notion, slack, push].some((query) => query.isError)) return <StatePanel state="error" onRefresh={() => void client.invalidateQueries({ queryKey: integrationKeys.all })}/>;
  const s = settings.data!, n = notion.data!, sl = slack.data!, pc = push.data!;
  return <div className="mx-auto grid max-w-5xl gap-6 pb-8">
    <header>
      <p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">CONNECTED WORKSPACE</p>
      <h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">알림 및 외부 연동</h1>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300">학습 기록은 앱에 먼저 안전하게 저장됩니다. 외부 전송 실패는 학습 완료에 영향을 주지 않습니다.</p>
    </header>
    <ConnectionReturnNotice/>
    <section className="grid gap-4 sm:grid-cols-3">
      <Summary icon={BellRing} label="앱 알림" value="사용 가능" ok/>
      <Summary icon={FileText} label="Notion" value={n.connected ? "연결됨" : "연결 필요"} ok={n.connected}/>
      <Summary icon={MessageSquare} label="Slack" value={sl.configured ? (sl.enabled ? "발송 중" : "꺼짐") : "연결 필요"} ok={sl.configured && sl.enabled}/>
    </section>
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7">
      <div className="flex items-start justify-between gap-4"><div><h2 className="text-xl font-black text-[#0a1837] dark:text-slate-50">알림 시간</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">한국 시간 기준이며 변경은 모든 기기에 함께 적용됩니다.</p></div><ShieldCheck className="text-emerald-600"/></div>
      <div className="mt-6"><NotificationPreferencesForm value={s}/></div>
    </section>
    <section className="grid gap-5 lg:grid-cols-2">
      <WebPushCard config={pc} settings={s}/>
      <SlackCard connection={sl}/>
      <OpenRouterCard/>
      <Card icon={RefreshCw} title="전송 원칙" status="업무와 분리"><p>부분 실패와 결과 불명은 별도 상태로 남기며 재시도합니다. 외부 장애로 앱에 저장된 학습 기록을 취소하지 않습니다.</p><div className="mt-5 flex items-center gap-2 rounded-xl bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-200"><CheckCircle2 size={18}/>앱 데이터가 원본입니다</div></Card>
      <NotionCard connection={n}/>
    </section>
  </div>;
}

function Summary({ icon: Icon, label, value, ok }: { icon: typeof BellRing; label: string; value: string; ok: boolean }) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900"><div className="flex items-center justify-between"><span className={`grid size-10 place-items-center rounded-xl ${ok ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200" : "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-200"}`}><Icon size={20}/></span><span className={`text-xs font-extrabold ${ok ? "text-emerald-700 dark:text-emerald-300" : "text-amber-700 dark:text-amber-300"}`}>{value}</span></div><p className="mt-3 text-sm font-bold text-slate-700 dark:text-slate-200">{label}</p></div>;
}

function Card({ icon: Icon, title, status, children }: { icon: typeof Cloud; title: string; status: string; children: React.ReactNode }) {
  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6"><div className="flex items-center justify-between gap-3"><span className="flex items-center gap-3 text-lg font-black text-[#0a1837] dark:text-slate-50"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Icon size={20}/></span>{title}</span><span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{status}</span></div><div className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">{children}</div></article>;
}

