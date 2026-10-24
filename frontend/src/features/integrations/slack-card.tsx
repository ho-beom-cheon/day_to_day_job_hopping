"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { History, MessageSquare, Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { SlackConnection, SlackDelivery, SlackDeliveryStatus } from "./contract";
import { integrationKeys, sendSlackTest, slackDeliveriesQuery, updateSlack } from "./queries";

import { BrowserConnectionControls } from "./browser-connection-controls";

const DEFAULT_EVENTS = ["STUDY_REMINDER", "WEEKLY_REPORT", "INTEGRATION_FAILURE"];

export function SlackCard({ connection }: { connection: SlackConnection }) {
  const client = useQueryClient();
  const deliveries = useQuery(slackDeliveriesQuery);
  const [message, setMessage] = useState<string | null>(null);
  const refresh = () => client.invalidateQueries({ queryKey: integrationKeys.all });

  const toggle = useMutation({
    mutationFn: () => updateSlack({
      body: {
        enabled: !connection.enabled,
        eventTypes: connection.eventTypes.length > 0 ? connection.eventTypes : DEFAULT_EVENTS,
      },
      revision: connection.revision,
    }),
    onMutate: () => setMessage(null),
    onSuccess: async () => {
      setMessage(connection.enabled ? "Slack 알림 전송을 멈췄습니다." : "Slack 알림 전송을 켰습니다.");
      await refresh();
    },
  });
  const test = useMutation({
    mutationFn: sendSlackTest,
    onMutate: () => setMessage(null),
    onSuccess: async ({ data }) => {
      setMessage(data.reused ? "동일한 테스트 요청 결과를 다시 확인했습니다." : "고정된 테스트 메시지를 전송 대기열에 등록했습니다.");
      await refresh();
    },
  });

  const error = toggle.error ?? test.error ?? (deliveries.error as Error | null);
  const busy = toggle.isPending || test.isPending;
  const status = !connection.configured ? "연결 필요" : connection.enabled ? "발송 중" : "발송 안 함";

  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6">
    <div className="flex items-center justify-between gap-3">
      <span className="flex items-center gap-3 text-lg font-black text-[#0a1837] dark:text-slate-50">
        <span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><MessageSquare size={20}/></span>
        Slack
      </span>
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{status}</span>
    </div>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">Slack 사이트에서 워크스페이스와 채널을 선택해 연결하세요. 연결 후 알림을 켜거나 테스트 메시지를 보낼 수 있습니다.</p>
    <BrowserConnectionControls provider="slack"/>
    {connection.configured ? <p className="mt-3 text-xs font-bold text-slate-500 dark:text-slate-400">대상 채널: {connection.channelName ?? "서버 지정 채널"}</p>
      : <div className="mt-4 rounded-2xl bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800 dark:bg-amber-950/50 dark:text-amber-200">Slack 연결이 완료되면 알림 전송과 테스트 메시지를 사용할 수 있습니다.</div>}
    <div className="mt-5 grid gap-2 sm:grid-cols-2">
      <Button disabled={!connection.configured || busy} onClick={() => toggle.mutate()}>{connection.enabled ? "Slack 알림 끄기" : "Slack 알림 켜기"}</Button>
      <Button variant="outline" disabled={!connection.configured || busy} onClick={() => test.mutate()}><Send size={17}/>테스트 메시지</Button>
    </div>
    <div className="mt-6 flex items-center justify-between gap-3">
      <h3 className="flex items-center gap-2 text-sm font-black text-slate-900 dark:text-slate-100"><History size={17}/>최근 전송</h3>
      {deliveries.data && <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">총 {deliveries.data.totalElements}건</span>}
    </div>
    {deliveries.isPending ? <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">전송 이력을 확인하는 중입니다.</p>
      : deliveries.data && deliveries.data.content.length > 0 ? <ul className="mt-3 grid gap-2" aria-label="Slack 최근 전송">
        {deliveries.data.content.map((delivery) => <DeliveryRow key={delivery.deliveryId} delivery={delivery}/>)}
      </ul> : <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">아직 전송 이력이 없습니다.</p>}
    {message && <p role="status" className="mt-4 text-sm font-semibold text-emerald-700 dark:text-emerald-300">{message}</p>}
    {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600 dark:text-rose-300">{error instanceof Error ? error.message : "Slack 작업을 완료하지 못했습니다."}</p>}
  </article>;
}

function DeliveryRow({ delivery }: { delivery: SlackDelivery }) {
  return <li className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 px-4 py-3 dark:border-slate-700">
    <span className="min-w-0">
      <strong className="block truncate text-sm text-slate-900 dark:text-slate-100">{eventLabel(delivery.eventType)}</strong>
      <span className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">{formatDate(delivery.createdAt)}{delivery.errorCode ? ` · ${delivery.errorCode}` : ""}</span>
    </span>
    <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-extrabold ${statusStyle(delivery.status)}`}>{statusLabel(delivery.status)}</span>
  </li>;
}

function eventLabel(eventType: string) {
  return ({
    STUDY_REMINDER: "학습 시작 알림",
    WEEKLY_REPORT: "주간 리포트",
    INTEGRATION_FAILURE: "연동 실패 알림",
    SLACK_TEST: "테스트 메시지",
  } as Record<string, string>)[eventType] ?? eventType;
}

function statusLabel(status: SlackDeliveryStatus) {
  return ({ READY: "대기", PROCESSING: "처리 중", SUCCESS: "성공", FAILED: "실패", UNKNOWN: "결과 불명" })[status];
}

function statusStyle(status: SlackDeliveryStatus) {
  if (status === "SUCCESS") return "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200";
  if (status === "FAILED") return "bg-rose-50 text-rose-700 dark:bg-rose-950 dark:text-rose-200";
  if (status === "UNKNOWN") return "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-200";
  return "bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
