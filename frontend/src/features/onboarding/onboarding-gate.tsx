"use client";
import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { StatePanel } from "@/components/ui/state-panel";
import { currentSessionQuery } from "@/features/session/queries";
import { OnboardingScreen } from "./onboarding-screen";

export function OnboardingGate() {
  const session=useQuery(currentSessionQuery);
  if (session.isPending) return <main className="mx-auto max-w-xl px-5 py-24"><StatePanel state="loading"/></main>;
  if (session.error instanceof ApiError && session.error.status===401) return <main className="mx-auto flex min-h-screen max-w-xl items-center px-5 py-16"><section className="w-full rounded-3xl border border-slate-200 bg-white p-7 shadow-sm"><h1 className="text-2xl font-extrabold text-slate-950">로그인 세션이 만료되었어요</h1><p className="mt-3 text-sm leading-6 text-slate-600">과정을 만들려면 Google 계정으로 다시 로그인해 주세요.</p><a className="mt-6 inline-flex min-h-11 w-full items-center justify-center rounded-xl bg-blue-600 px-5 text-sm font-semibold text-white sm:w-auto" href="/oauth2/authorization/google">Google로 다시 로그인</a></section></main>;
  if (session.isError) return <main className="mx-auto max-w-xl px-5 py-24"><StatePanel state="error" onRefresh={()=>void session.refetch()}/></main>;
  return <OnboardingScreen session={session.data}/>;
}
