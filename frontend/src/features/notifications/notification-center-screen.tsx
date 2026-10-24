"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Check, CheckCheck, ChevronLeft, ChevronRight, CircleAlert, Clock3 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import type { NotificationItem, NotificationType } from "./contract";
import { markAllNotificationsRead, markNotificationRead, notificationKeys, notificationsQuery } from "./queries";

const labels: Record<NotificationType, string> = {
  STUDY_REMINDER: "학습", TEST_REMINDER: "시험", STREAK_REMINDER: "연속 학습",
  WEEKLY_REPORT: "주간 기록", MONTHLY_REPORT: "월간 기록", INTEGRATION_FAILURE: "연동",
};

export function NotificationCenterScreen() {
  const client = useQueryClient();
  const router = useRouter();
  const [read, setRead] = useState<boolean | undefined>(undefined);
  const [pageNumber, setPageNumber] = useState(0);
  const query = useQuery(notificationsQuery({ read, page: pageNumber, size: 10 }));
  const invalidate = () => client.invalidateQueries({ queryKey: notificationKeys.all });
  const markRead = useMutation({ mutationFn: markNotificationRead, onSuccess: () => { void invalidate(); } });
  const markAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidate });
  const select = (value: boolean | undefined) => { setRead(value); setPageNumber(0); };
  const open = async (item: NotificationItem) => {
    try {
      if (!item.read) await markRead.mutateAsync(item.notificationId);
      router.push(item.targetPath);
    } catch { /* Keep the notification visible so the failed read can be retried. */ }
  };
  if (query.isPending) return <StatePanel state="loading"/>;
  if (query.isError) return <StatePanel state="error" onRefresh={() => void query.refetch()}/>;
  const result = query.data.page;
  return <div className="mx-auto grid max-w-5xl gap-6 pb-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">NOTIFICATION CENTER</p><h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">알림 센터</h1><p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-400">학습 일정과 완료 기록을 한곳에서 확인하세요. 읽음 상태는 모든 기기에 저장됩니다.</p></div><Button variant="outline" disabled={result.unreadCount === 0 || markAll.isPending} onClick={() => markAll.mutate(query.data.serverTime)} className="dark:border-slate-600 dark:bg-[#172235] dark:text-slate-100"><CheckCheck size={17}/>{markAll.isPending ? "처리 중" : `모두 읽음 (${result.unreadCount})`}</Button></header>
    <section className="rounded-[28px] border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-[#111b2a] sm:p-6">
      <div className="flex flex-wrap gap-2" role="group" aria-label="알림 읽음 필터"><Filter active={read === undefined} onClick={() => select(undefined)}>전체</Filter><Filter active={read === false} onClick={() => select(false)}>읽지 않음</Filter><Filter active={read === true} onClick={() => select(true)}>읽음</Filter></div>
      <div className="mt-5 grid gap-3">{result.content.length ? result.content.map(item => <NotificationCard key={item.notificationId} item={item} pending={markRead.isPending} onOpen={() => void open(item)} onRead={() => markRead.mutate(item.notificationId)}/>) : <div className="rounded-2xl bg-slate-50 px-5 py-12 text-center dark:bg-[#162234]"><BellRing className="mx-auto text-slate-300 dark:text-slate-600" size={34}/><p className="mt-4 text-sm font-black text-slate-700 dark:text-slate-200">조건에 맞는 알림이 없어요.</p><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">새 학습 소식이 생기면 여기에 표시됩니다.</p></div>}</div>
      {result.totalPages > 1 && <nav aria-label="알림 페이지" className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4 dark:border-slate-700"><Button variant="outline" disabled={pageNumber === 0} onClick={() => setPageNumber(value => value - 1)}><ChevronLeft size={16}/>이전</Button><span className="text-xs font-bold text-slate-500">{pageNumber + 1} / {result.totalPages}</span><Button variant="outline" disabled={pageNumber + 1 >= result.totalPages} onClick={() => setPageNumber(value => value + 1)}>다음<ChevronRight size={16}/></Button></nav>}
    </section>
    {(markRead.isError || markAll.isError) && <p role="alert" className="text-sm font-bold text-rose-600">읽음 상태를 저장하지 못했습니다. 최신 목록을 다시 불러와 주세요.</p>}
  </div>;
}

function Filter({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return <button type="button" aria-pressed={active} onClick={onClick} className={`min-h-10 rounded-xl px-4 text-sm font-black transition ${active ? "bg-blue-600 text-white shadow-sm" : "bg-slate-100 text-slate-600 hover:bg-blue-50 hover:text-blue-700 dark:bg-slate-800 dark:text-slate-300"}`}>{children}</button>;
}

function NotificationCard({ item, pending, onOpen, onRead }: { item: NotificationItem; pending: boolean; onOpen: () => void; onRead: () => void }) {
  const WarningIcon = item.type === "INTEGRATION_FAILURE" ? CircleAlert : BellRing;
  return <article className={`grid gap-4 rounded-2xl border p-4 transition sm:grid-cols-[auto_1fr_auto] sm:items-center ${item.read ? "border-slate-200 bg-white dark:border-slate-700 dark:bg-[#111b2a]" : "border-blue-200 bg-blue-50/60 dark:border-blue-500/30 dark:bg-blue-500/10"}`}>
    <span className={`grid size-11 place-items-center rounded-2xl ${item.type === "INTEGRATION_FAILURE" ? "bg-rose-100 text-rose-600 dark:bg-rose-500/10 dark:text-rose-300" : "bg-white text-blue-600 shadow-sm dark:bg-[#0d1624] dark:text-blue-300"}`}><WarningIcon size={20}/></span>
    <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-black text-slate-600 dark:bg-slate-700 dark:text-slate-300">{labels[item.type]}</span>{!item.read && <span className="rounded-full bg-blue-600 px-2.5 py-1 text-[10px] font-black text-white">새 알림</span>}</div><h2 className="mt-2 text-base font-black text-[#0a1837] dark:text-slate-100">{item.title}</h2><p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-400">{item.message}</p><p className="mt-2 flex items-center gap-1.5 text-xs font-semibold text-slate-400"><Clock3 size={14}/>{formatTime(item.createdAt)}</p></div>
    <div className="flex gap-2 sm:flex-col"><Button className="flex-1 sm:flex-none" disabled={pending} onClick={onOpen}>확인하기<ChevronRight size={16}/></Button>{!item.read && <Button variant="outline" className="flex-1 sm:flex-none dark:border-slate-600 dark:bg-[#172235] dark:text-slate-100" disabled={pending} onClick={onRead}><Check size={16}/>읽음</Button>}</div>
  </article>;
}

const formatter = new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit" });
function formatTime(value: string) { return formatter.format(new Date(value)); }
