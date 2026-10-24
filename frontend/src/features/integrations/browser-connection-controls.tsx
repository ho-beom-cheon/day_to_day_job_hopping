"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link2, Unplug } from "lucide-react";
import { Button } from "@/components/ui/button";
import { authorizeBrowserConnection, browserConnectionQuery, disconnectBrowserConnection, type BrowserProvider } from "./browser-connections";
import { integrationKeys } from "./queries";

export function BrowserConnectionControls({ provider }: { provider: BrowserProvider }) {
  const label = provider === "slack" ? "Slack" : "OpenRouter";
  const client = useQueryClient();
  const connection = useQuery(browserConnectionQuery(provider));
  const [notice, setNotice] = useState<string | null>(null);
  const connect = useMutation({ mutationFn: () => authorizeBrowserConnection(provider),
    onSuccess: ({ data }) => window.location.assign(data.authorizationUrl) });
  const disconnect = useMutation({ mutationFn: () => disconnectBrowserConnection(provider, connection.data!.revision),
    onSuccess: async () => {
      setNotice("앱 연결을 해제하고 대기 작업을 취소했습니다. 이미 전송 중인 작업은 완료될 수 있습니다. 공급자에서도 권한을 없애려면 아래 관리 페이지에서 앱 또는 키를 삭제해 주세요.");
      await Promise.all([client.invalidateQueries({ queryKey: integrationKeys.all }), client.invalidateQueries({ queryKey: ["ai-usage"] })]);
    }, onError: () => void client.invalidateQueries({ queryKey: integrationKeys.all }) });
  const error = connection.error ?? connect.error ?? disconnect.error;
  const busy = connect.isPending || disconnect.isPending;
  const c = connection.data;
  const managementUrl = provider === "slack" ? "https://slack.com/apps/manage" : c?.managementUrl?.match(/^https:\/\/openrouter\.ai\/keys\/[a-f0-9]{64}$/) ? c.managementUrl : "https://openrouter.ai/settings/keys";
  return <div className="mt-4 space-y-3">
    {connection.isPending && <p className="text-sm text-slate-500">연결 상태를 확인하고 있습니다.</p>}
    {c && <p className="text-sm text-slate-600 dark:text-slate-300">{c.connected
      ? `${c.workspaceName || label} 연결됨${c.channelName ? ` · ${c.channelName}` : ""}`
      : c.configured ? `${label} 사이트에서 로그인하고 연결을 승인해 주세요.` : "운영자의 최초 앱 설정이 필요합니다. 설정이 끝나면 연결 버튼을 사용할 수 있습니다."}</p>}
    <div className="flex flex-wrap gap-2">
      <Button disabled={!c?.configured || busy} onClick={() => { setNotice(null); connect.mutate(); }}><Link2 size={16}/>{label} {c?.connected ? "다시 연결" : "연결"}</Button>
      {c?.connected && <Button variant="outline" disabled={busy} onClick={() => disconnect.mutate()}><Unplug size={16}/>연결 해제</Button>}
      {(c?.connected || notice) && <a className="inline-flex min-h-11 items-center text-sm font-bold text-blue-700 underline dark:text-blue-300" href={managementUrl} target="_blank" rel="noreferrer">{label} 권한·{provider === "openrouter" ? "한도" : "앱"} 관리</a>}
    </div>
    {notice && <p role="status" className="text-sm leading-6 text-slate-600 dark:text-slate-300">{notice}</p>}
    {error && <div role="alert" className="text-sm text-rose-600 dark:text-rose-300"><p>연결 작업을 완료하지 못했습니다. 상태를 새로 확인한 뒤 다시 시도해 주세요.</p><Button variant="outline" onClick={() => void connection.refetch()}>상태 새로고침</Button></div>}
  </div>;
}
