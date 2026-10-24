"use client";

import { useState, useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellOff, MonitorSmartphone, Smartphone } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { NotificationSettings, PushConfig, PushSubscription } from "./contract";
import { deletePushSubscription, integrationKeys, pushSubscriptionsQuery, registerPushSubscription, updateSettings } from "./queries";
import { browserPushSupported, currentDeviceId, currentDeviceName, subscribeBrowser, unsubscribeBrowser } from "./web-push";

export function WebPushCard({ config, settings }: { config: PushConfig; settings: NotificationSettings }) {
  const client = useQueryClient();
  const subscriptions = useQuery(pushSubscriptionsQuery);
  const supported = useSyncExternalStore(noBrowserEvents, browserPushSupported, () => null);
  const deviceId = useSyncExternalStore(noBrowserEvents, currentDeviceId, () => null);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = () => client.invalidateQueries({ queryKey: integrationKeys.all });
  const connect = useMutation({
    mutationFn: async () => {
      if (!config.publicKey) throw new Error("서버 푸시 키가 준비되지 않았습니다.");
      const id = currentDeviceId();
      if (!id) throw new Error("브라우저 기기 ID를 만들 수 없습니다.");
      const subscription = await subscribeBrowser(config.publicKey);
      await registerPushSubscription({ deviceId: id, deviceName: currentDeviceName(), subscription });
      if (!settings.pushEnabled) await updateSettings({ body: { pushEnabled: true }, revision: settings.revision });
    },
    onMutate: () => setMessage(null),
    onSuccess: async () => { setMessage("이 브라우저를 웹 푸시에 연결했습니다."); await refresh(); },
  });
  const disconnect = useMutation({
    mutationFn: async (subscription: PushSubscription) => {
      await deletePushSubscription(subscription.subscriptionId);
      if (subscription.deviceId === currentDeviceId()) await unsubscribeBrowser();
    },
    onMutate: () => setMessage(null),
    onSuccess: async () => { setMessage("선택한 브라우저 연결을 해제했습니다."); await refresh(); },
  });
  const toggleDelivery = useMutation({
    mutationFn: () => updateSettings({ body: { pushEnabled: !settings.pushEnabled }, revision: settings.revision }),
    onMutate: () => setMessage(null),
    onSuccess: async () => { setMessage(settings.pushEnabled ? "웹 푸시 전송을 멈췄습니다." : "웹 푸시 전송을 켰습니다."); await refresh(); },
  });

  const active = subscriptions.data?.filter((subscription) => subscription.active) ?? [];
  const current = active.find((subscription) => subscription.deviceId === deviceId);
  const error = connect.error ?? disconnect.error ?? toggleDelivery.error ?? (subscriptions.error as Error | null);
  const busy = connect.isPending || disconnect.isPending || toggleDelivery.isPending;
  const status = !config.enabled ? "서버 준비 필요" : settings.pushEnabled ? `${active.length}대 발송 중` : `${active.length}대 연결됨`;

  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6">
    <div className="flex items-center justify-between gap-3">
      <span className="flex items-center gap-3 text-lg font-black text-[#0a1837] dark:text-slate-50"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Smartphone size={20}/></span>웹 푸시</span>
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{status}</span>
    </div>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">권한 허용은 이 브라우저에서 연결 버튼을 눌렀을 때만 요청합니다. 구독 주소와 인증키는 서버에서 암호화해 보관합니다.</p>
    {!config.enabled && <div className="mt-4 rounded-2xl bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-800 dark:bg-amber-950/50 dark:text-amber-200">VAPID 키와 구독 암호화 키를 서버에 설정하면 연결할 수 있습니다.</div>}
    {supported === false && <div className="mt-4 rounded-2xl bg-slate-100 px-4 py-3 text-sm font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-200">이 브라우저 또는 현재 접속 방식은 웹 푸시를 지원하지 않습니다.</div>}
    <div className="mt-5 grid gap-2 sm:grid-cols-2">
      <Button disabled={!config.enabled || supported !== true || Boolean(current) || busy} onClick={() => connect.mutate()}><MonitorSmartphone size={17}/>{current ? "이 브라우저 연결됨" : "이 브라우저 연결"}</Button>
      <Button variant="outline" disabled={!config.enabled || active.length === 0 || busy} onClick={() => toggleDelivery.mutate()}>{settings.pushEnabled ? "전체 전송 끄기" : "전체 전송 켜기"}</Button>
    </div>
    {subscriptions.isPending ? <p className="mt-5 text-sm text-slate-500">연결된 브라우저를 확인하는 중입니다.</p>
      : active.length > 0 ? <ul className="mt-5 grid gap-2" aria-label="연결된 브라우저">
        {active.map((subscription) => <li key={subscription.subscriptionId} className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 px-4 py-3 dark:border-slate-700">
          <span className="min-w-0"><strong className="block truncate text-sm text-slate-900 dark:text-slate-100">{subscription.deviceName}</strong><span className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">{subscription.deviceId === deviceId ? "현재 브라우저" : "다른 브라우저"}</span></span>
          <Button className="min-h-9 shrink-0 px-3" variant="outline" disabled={busy} onClick={() => disconnect.mutate(subscription)}><BellOff size={15}/>해제</Button>
        </li>)}
      </ul> : <p className="mt-5 text-sm text-slate-500 dark:text-slate-400">연결된 브라우저가 없습니다.</p>}
    {message && <p role="status" className="mt-4 text-sm font-semibold text-emerald-700 dark:text-emerald-300">{message}</p>}
    {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600 dark:text-rose-300">{error instanceof Error ? error.message : "웹 푸시 작업을 완료하지 못했습니다."}</p>}
  </article>;
}

const noBrowserEvents = () => () => {};
