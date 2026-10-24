"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Cloud, Link2, Unplug } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { NotionConnection } from "./contract";
import { disconnectNotion, integrationKeys, startNotionAuthorization } from "./queries";
import { notionSetupQuery } from "./browser-connections";

export function NotionCard({ connection }: { connection: NotionConnection }) {
  const client = useQueryClient();
  const setup = useQuery(notionSetupQuery);
  const [notice, setNotice] = useState<string | null>(null);
  const refresh = () => client.invalidateQueries({ queryKey: integrationKeys.all });

  const connect = useMutation({
    mutationFn: startNotionAuthorization,
    onMutate: () => setNotice(null),
    onSuccess: result => window.location.assign(result.data.authorizationUrl),
  });
  const disconnect = useMutation({
    mutationFn: disconnectNotion,
    onMutate: () => setNotice(null),
    onSuccess: async result => { setNotice(result.data.providerRevocationStatus === "PENDING" ? "앱 연결을 해제했습니다. 공급자 토큰 폐기는 백그라운드에서 재시도합니다." : "Notion 연결을 해제했습니다."); await refresh(); },
  });

  const error = connect.error ?? disconnect.error;
  const busy = connect.isPending || disconnect.isPending;
  const status = connection.connected ? "연결됨" : connection.status === "REAUTH_REQUIRED" ? "재연결 필요" : "연결 안 됨";

  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6 lg:col-span-2">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <span className="flex items-center gap-3 text-lg font-black text-[#0a1837] dark:text-slate-50"><span className="grid size-10 place-items-center rounded-xl bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Cloud size={20}/></span>Notion</span>
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{status}</span>
    </div>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">연결은 유지해 두었다가 면접 답변집 내보내기에 사용합니다. 내보내기 기능은 아직 준비 중입니다.</p>

    {!connection.connected ? <div className="mt-5 rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
      <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">{setup.data?.configured ? "Notion 사이트에서 워크스페이스와 공유할 페이지를 선택하세요." : "운영자의 최초 Notion 앱 설정이 필요합니다. 설정이 끝나면 연결할 수 있습니다."}</p>
      <Button className="mt-4 w-full sm:w-auto" disabled={busy || !setup.data?.configured} onClick={() => connect.mutate()}><Link2 size={17}/>{connection.status === "REAUTH_REQUIRED" ? "Notion 다시 연결" : "Notion 연결"}</Button>
    </div> : <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-emerald-50 px-4 py-3 dark:bg-emerald-950/40">
      <span className="text-sm font-bold text-emerald-800 dark:text-emerald-200">{connection.workspaceName ?? "Notion 워크스페이스"} · API {connection.notionVersion}</span>
      <Button className="min-h-9 px-3 py-1.5" variant="outline" disabled={busy} onClick={() => disconnect.mutate()}><Unplug size={15}/>연결 해제</Button>
    </div>}
    {notice && <p role="status" className="mt-4 text-sm font-semibold text-emerald-700 dark:text-emerald-300">{notice}</p>}
    {error && <p role="alert" className="mt-4 text-sm font-semibold text-rose-600 dark:text-rose-300">{error instanceof Error ? error.message : "Notion 작업을 완료하지 못했습니다."}</p>}
  </article>;
}
