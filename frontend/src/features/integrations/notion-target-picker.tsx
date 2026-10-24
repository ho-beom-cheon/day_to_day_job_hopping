"use client";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { Button } from "@/components/ui/button";

type Target = { title: string; databaseId: string; dataSourceId: string };
export function decodeAvailableTargets(value: unknown): { content: Target[]; nextCursor: string | null } {
  if (!value || typeof value !== "object") throw new TypeError("Invalid targets");
  const v = value as Record<string, unknown>;
  if (!Array.isArray(v.content) || v.nextCursor !== null && typeof v.nextCursor !== "string") throw new TypeError("Invalid targets page");
  const content = v.content.map(item => {
    if (!item || typeof item !== "object" || typeof item.title !== "string" || typeof item.databaseId !== "string" || typeof item.dataSourceId !== "string") throw new TypeError("Invalid target");
    return { title: item.title, databaseId: item.databaseId, dataSourceId: item.dataSourceId };
  });
  return { content, nextCursor: v.nextCursor as string | null };
}
export function NotionTargetPicker({ onSelect }: { onSelect: (target: Target) => void }) {
  const [query, setQuery] = useState("");
  const [items, setItems] = useState<Target[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [selected, setSelected] = useState("");
  const search = useMutation({
    mutationFn: async (next: string | null) => {
      const params = new URLSearchParams({ query });
      if (next) params.set("cursor", next);
      return (await api.request({ path: `/api/v1/integrations/notion/available-targets?${params}`, decode: v => decodeEnvelope(v, decodeAvailableTargets) })).data.data;
    },
    onSuccess: (result, next) => {
      setItems(old => next ? [...new Map([...old, ...result.content].map(item => [item.dataSourceId, item])).values()] : result.content);
      setCursor(result.nextCursor);
      if (!next) setSelected("");
    },
  });
  return <div className="mt-4 space-y-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/60">
    <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Notion에서 공유한 데이터베이스 선택</p>
    <div className="flex flex-wrap gap-2"><input aria-label="Notion 데이터베이스 이름" maxLength={100} placeholder="이름으로 검색 (비우면 전체)" value={query} onChange={e => { setQuery(e.target.value); setCursor(null); }} className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600"/><Button variant="outline" disabled={search.isPending} onClick={() => search.mutate(null)}>대상 불러오기</Button></div>
    {items.length > 0 && <><select aria-label="Notion 저장 대상" value={selected} onChange={e => setSelected(e.target.value)} className="w-full min-w-0 rounded-xl border border-slate-300 bg-white p-3 text-sm dark:border-slate-600 dark:bg-slate-900"><option value="">데이터베이스를 선택하세요</option>{items.map(item => <option key={item.dataSourceId} value={item.dataSourceId}>{item.title} · {item.dataSourceId.slice(0,8)}</option>)}</select><Button variant="outline" disabled={!selected} onClick={() => { const item = items.find(i => i.dataSourceId === selected); if (item) onSelect(item); }}>6종 기록을 선택한 대상으로 지정</Button></>}
    {cursor && <Button variant="outline" disabled={search.isPending} onClick={() => search.mutate(cursor)}>대상 더 불러오기</Button>}
    {search.isSuccess && items.length === 0 && <p className="text-sm text-slate-600 dark:text-slate-300">공유된 데이터베이스를 찾지 못했습니다. Notion 연결 시 해당 데이터베이스가 있는 페이지를 허용하고 다시 불러오세요.</p>}
    {search.isError && <p role="alert" className="text-sm text-rose-600 dark:text-rose-300">Notion 대상을 불러오지 못했습니다. 연결과 페이지 접근 권한을 확인해 주세요.</p>}
    <p className="text-xs leading-5 text-slate-500 dark:text-slate-400">선택 후 아래의 ‘대상 검증 후 저장’을 눌러 주세요. 하나의 데이터베이스에 6종 기록을 모아도 됩니다.</p>
  </div>;
}
