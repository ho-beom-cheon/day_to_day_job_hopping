"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck, ChevronRight } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { NotificationItem } from "./contract";
import { markAllNotificationsRead, markNotificationRead, notificationKeys, notificationsQuery } from "./queries";

export function NotificationPopover({ open, onToggle, onNavigate }: { open: boolean; onToggle: () => void; onNavigate: () => void }) {
  const client = useQueryClient();
  const router = useRouter();
  const query = useQuery(notificationsQuery({ page: 0, size: 3 }));
  const invalidate = () => client.invalidateQueries({ queryKey: notificationKeys.all });
  const markRead = useMutation({ mutationFn: markNotificationRead, onSuccess: () => { void invalidate(); } });
  const markAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: invalidate });
  const page = query.data?.page;
  const openItem = async (item: NotificationItem) => {
    try {
      if (!item.read) await markRead.mutateAsync(item.notificationId);
      onNavigate();
      router.push(item.targetPath);
    } catch { /* Leave the popover open and show the read error. */ }
  };
  return <>
    <button type="button" aria-label="알림 센터" aria-expanded={open} onClick={onToggle} className="relative grid size-10 place-items-center rounded-full text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800">
      <Bell size={20}/>{page && page.unreadCount > 0 && <span className="absolute right-0.5 top-0.5 grid min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[9px] font-black leading-4 text-white">{page.unreadCount > 99 ? "99+" : page.unreadCount}</span>}
    </button>
    {open && <section aria-label="알림 센터" className="absolute right-0 top-[calc(100%+10px)] w-[min(380px,calc(100vw-32px))] rounded-2xl border border-slate-200 bg-white p-3 shadow-xl dark:border-slate-700 dark:bg-[#111b2a]">
      <div className="flex items-center justify-between gap-3 px-2 py-2"><div><p className="font-black text-[#0a1837] dark:text-slate-100">알림 센터</p><p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{page ? `읽지 않은 알림 ${page.unreadCount}개` : "학습 소식을 불러오는 중"}</p></div>{page && page.unreadCount > 0 && <button type="button" disabled={markAll.isPending} onClick={() => markAll.mutate(query.data?.serverTime ?? new Date(Date.now() - 1000).toISOString())} className="flex min-h-9 items-center gap-1.5 rounded-xl px-2 text-xs font-extrabold text-blue-700 hover:bg-blue-50 disabled:opacity-50 dark:text-blue-300 dark:hover:bg-blue-500/10"><CheckCheck size={16}/>모두 읽음</button>}</div>
      <div className="mt-2 grid gap-1">{query.isPending ? <p className="px-3 py-6 text-center text-sm text-slate-500">알림을 불러오고 있어요.</p> : query.isError ? <p role="alert" className="px-3 py-6 text-center text-sm text-rose-600">알림을 불러오지 못했어요.</p> : page?.content.length ? page.content.map(item => <button key={item.notificationId} type="button" disabled={markRead.isPending} onClick={() => void openItem(item)} className="flex min-h-14 w-full items-start gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-blue-50 disabled:opacity-60 dark:hover:bg-blue-500/10"><span className={`mt-1 size-2 shrink-0 rounded-full ${item.read ? "bg-slate-300 dark:bg-slate-600" : "bg-blue-600"}`}/><span className="min-w-0 flex-1"><strong className="block truncate text-sm text-slate-800 dark:text-slate-100">{item.title}</strong><span className="mt-0.5 line-clamp-2 block text-xs leading-5 text-slate-500 dark:text-slate-400">{item.message}</span></span><ChevronRight size={15} className="mt-1 shrink-0 text-slate-400"/></button>) : <p className="px-3 py-6 text-center text-sm text-slate-500">새 알림이 없어요.</p>}</div>
      {markRead.isError && <p role="alert" className="px-3 py-2 text-xs font-semibold text-rose-600">읽음 상태를 저장하지 못했습니다. 다시 시도해 주세요.</p>}
      <Link href="/notifications" onClick={onNavigate} className="mt-2 flex min-h-10 items-center justify-center rounded-xl border-t border-slate-100 text-xs font-black text-blue-700 hover:bg-blue-50 dark:border-slate-700 dark:text-blue-300 dark:hover:bg-blue-500/10">전체 알림 보기</Link>
    </section>}
  </>;
}
