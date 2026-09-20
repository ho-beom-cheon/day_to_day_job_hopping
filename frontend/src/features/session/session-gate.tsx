"use client";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import type { ReactNode } from "react";
import { ApiError } from "@/api/client";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { currentSessionQuery } from "./queries";
import { toSessionViewModel } from "./model";

export function SessionGate({ children }: { children: ReactNode }) {
  const query = useQuery(currentSessionQuery);
  if (query.isPending) return <main className="mx-auto max-w-xl px-5 py-24"><StatePanel state="loading" /></main>;
  if (query.error instanceof ApiError && query.error.status === 401) return <main className="mx-auto flex min-h-screen max-w-xl items-center px-5 py-16"><section className="w-full rounded-3xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9"><p className="text-sm font-bold text-blue-700">학습 공간</p><h1 className="mt-3 text-2xl font-extrabold tracking-tight text-slate-950">로그인이 필요해요</h1><p className="mt-3 text-sm leading-6 text-slate-600">학습 기록과 일정을 안전하게 불러오려면 Google 계정으로 로그인해 주세요.</p><Button asChild className="mt-6 w-full sm:w-auto"><a href="/oauth2/authorization/google">Google로 계속하기</a></Button><LinkHome /></section></main>;
  if (query.isError) return <main className="mx-auto max-w-xl px-5 py-24"><StatePanel state="error" onRefresh={() => void query.refetch()} /></main>;
  return <AppShell session={toSessionViewModel(query.data)}>{children}</AppShell>;
}

function LinkHome() { return <Link href="/" className="mt-4 block text-center text-sm font-semibold text-slate-500 underline-offset-4 hover:underline sm:inline-block sm:pl-5">처음 화면으로</Link>; }
