"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, Loader2, Mic, Plus, ShieldAlert } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { careerKeys, claimsQuery, confirmFact, createClaim, createFact, factsQuery, interviewClaim, patchClaim, reviewClaim, updateFact, type CareerFact, type FactInput, type ResumeClaim } from "./api";

const panel = "rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7";
const input = "min-h-10 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-[#151f2f] dark:text-slate-100";
const blank: FactInput = { projectName: "", clientName: "", periodFrom: "", periodTo: "", roleText: "", techText: "", confirmedScope: "", cautionText: "" };
const statusLabel: Record<string, string> = { NEEDS_FIX: "수정 필요", NEEDS_EVIDENCE: "근거 확인 필요", OK: "양호" };

export function CareerScreen() {
  const facts = useQuery(factsQuery);
  const claims = useQuery(claimsQuery);
  const [adding, setAdding] = useState(false);
  if (facts.isPending || claims.isPending) return <StatePanel state="loading"/>;
  if (facts.isError || claims.isError) return <StatePanel state="error" onRefresh={() => { void facts.refetch(); void claims.refetch(); }}/>;
  return <div className="mx-auto grid max-w-4xl gap-5 pb-10">
    <header>
      <p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">경력·이력서</p>
      <h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">경력 사실과 이력서 검증</h1>
      <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">확인한 경력 사실만 AI 면접 과제에 쓰여요. 이력서 파일, 연락처, 연봉 같은 개인정보는 올리거나 저장하지 않아요.</p>
    </header>
    <section className={panel}>
      <div className="flex items-center justify-between gap-3"><h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">경력 사실</h2><Button variant="outline" onClick={() => setAdding(true)}><Plus size={16}/>추가</Button></div>
      <p className="mt-1 text-sm text-slate-500">후보는 실제 담당 범위를 적고 &quot;확인&quot;해야 AI에 전달돼요. 확인한 항목을 고치면 다시 후보가 돼요.</p>
      {adding && <FactForm initial={blank} onDone={() => setAdding(false)}/>}
      <ul className="mt-4 grid gap-3">{facts.data.map(fact => <FactItem key={fact.factId} fact={fact}/>)}</ul>
      {facts.data.length === 0 && !adding && <p className="mt-3 text-sm text-slate-500">아직 경력 사실이 없어요.</p>}
    </section>
    <ClaimsPanel claims={claims.data} facts={facts.data}/>
  </div>;
}

function FactItem({ fact }: { fact: CareerFact }) {
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const confirm = useMutation({ mutationFn: () => confirmFact(fact.factId, fact.revision), onSuccess: () => client.invalidateQueries({ queryKey: careerKeys.all }) });
  if (editing) return <li><FactForm initial={fact} fact={fact} onDone={() => setEditing(false)}/></li>;
  return <li className="rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <strong className="text-sm text-slate-900 dark:text-slate-100">{fact.projectName}{fact.clientName && <span className="ml-1 font-semibold text-slate-500">· {fact.clientName}</span>}</strong>
      <span className={`rounded-full px-2.5 py-1 text-xs font-black ${fact.status === "CONFIRMED" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{fact.status === "CONFIRMED" ? "확인됨" : "후보"}</span>
    </div>
    <p className="mt-1 text-xs text-slate-500">{fact.periodFrom} ~ {fact.periodTo ?? "진행 중"} · {fact.techText || "기술 미기재"}</p>
    {fact.roleText && <p className="mt-2 text-sm text-slate-700 dark:text-slate-200">{fact.roleText}</p>}
    <p className="mt-2 text-xs leading-5 text-slate-600 dark:text-slate-300"><b>확인한 담당 범위</b> {fact.confirmedScope || "아직 적지 않았어요"}</p>
    {fact.cautionText && <p className="mt-1 text-xs text-amber-700">주의: {fact.cautionText}</p>}
    <div className="mt-3 flex gap-2"><Button variant="outline" onClick={() => setEditing(true)}>고치기</Button>{fact.status === "CANDIDATE" && <Button disabled={!fact.confirmedScope.trim() || confirm.isPending} onClick={() => confirm.mutate()}><BadgeCheck size={16}/>확인</Button>}</div>
    {confirm.error && <p role="alert" className="mt-2 text-xs text-rose-600">{confirm.error.message}</p>}
  </li>;
}

function FactForm({ initial, fact, onDone }: { initial: FactInput; fact?: CareerFact; onDone: () => void }) {
  const client = useQueryClient();
  const [value, setValue] = useState<FactInput>({ ...initial, clientName: initial.clientName ?? "", periodTo: initial.periodTo ?? "" });
  const save = useMutation({ mutationFn: () => fact ? updateFact(fact.factId, fact.revision, value) : createFact(value), onSuccess: async () => { await client.invalidateQueries({ queryKey: careerKeys.all }); onDone(); } });
  const field = (key: keyof FactInput, label: string, placeholder = "", multiline = false) => <label className="grid gap-1 text-xs font-bold text-slate-500">{label}
    {multiline ? <textarea className={`${input} min-h-20 py-2`} placeholder={placeholder} value={value[key] ?? ""} onChange={event => setValue({ ...value, [key]: event.target.value })}/>
      : <input className={input} placeholder={placeholder} value={value[key] ?? ""} onChange={event => setValue({ ...value, [key]: event.target.value })}/>}</label>;
  return <div className="mt-4 grid gap-3 rounded-2xl border border-blue-200 p-4 dark:border-blue-900">
    <div className="grid gap-3 sm:grid-cols-2">{field("projectName", "프로젝트")}{field("clientName", "고객사·기관")}{field("periodFrom", "시작 (YYYY-MM)", "2020-12")}{field("periodTo", "종료 (YYYY-MM, 진행 중이면 비움)", "2021-10")}</div>
    {field("roleText", "역할")}{field("techText", "사용 기술")}{field("confirmedScope", "확인한 담당 범위 (AI에 전달됨)", "직접 담당한 화면·배치·연계와 결과", true)}{field("cautionText", "주의할 점", "예: 수치 미확인, 코어 전체 담당 아님", true)}
    <div className="flex gap-2"><Button disabled={!value.projectName.trim() || !/^\d{4}-\d{2}$/.test(value.periodFrom) || save.isPending} onClick={() => save.mutate()}>저장</Button><Button variant="outline" onClick={onDone}>취소</Button></div>
    {save.error && <p role="alert" className="text-xs text-rose-600">{save.error.message}</p>}
  </div>;
}

function ClaimsPanel({ claims, facts }: { claims: ResumeClaim[]; facts: CareerFact[] }) {
  const client = useQueryClient();
  const [text, setText] = useState("");
  const [factId, setFactId] = useState("");
  const create = useMutation({ mutationFn: () => createClaim(factId || null, text), onSuccess: async () => { setText(""); await client.invalidateQueries({ queryKey: careerKeys.all }); } });
  return <section className={panel}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">이력서 성과 문장 검증</h2>
    <p className="mt-1 text-sm text-slate-500">문장 하나씩 붙여 넣으면 AI가 면접관 시각으로 수치·근거를 점검하고, 압박형 면접 과제로 연습할 수 있어요.</p>
    <div className="mt-4 grid gap-2">
      <select aria-label="관련 경력" className={input} value={factId} onChange={event => setFactId(event.target.value)}><option value="">관련 경력 선택 (선택)</option>{facts.map(fact => <option key={fact.factId} value={fact.factId}>{fact.projectName}</option>)}</select>
      <textarea aria-label="성과 문장" className={`${input} min-h-20 py-2`} maxLength={2000} placeholder="예: 서비스 단 정렬로 조회 시간을 330ms(0.66초)로 줄였습니다." value={text} onChange={event => setText(event.target.value)}/>
      <Button className="justify-self-start" disabled={!text.trim() || create.isPending} onClick={() => create.mutate()}><Plus size={16}/>문장 추가</Button>
      {create.error && <p role="alert" className="text-xs text-rose-600">{create.error.message}</p>}
    </div>
    <ul className="mt-5 grid gap-3">{claims.map(claim => <ClaimItem key={claim.claimId} claim={claim}/>)}</ul>
  </section>;
}

function ClaimItem({ claim }: { claim: ResumeClaim }) {
  const client = useQueryClient();
  const router = useRouter();
  const refresh = () => client.invalidateQueries({ queryKey: careerKeys.all });
  const review = useMutation({ mutationFn: () => reviewClaim(claim.claimId), onSuccess: refresh });
  const interview = useMutation({ mutationFn: () => interviewClaim(claim.claimId), onSuccess: task => router.push(task.status === "PREPARING" ? "/workspace" : `/tasks/${task.taskId}`) });
  const confirmMetric = useMutation({ mutationFn: () => patchClaim(claim.claimId, claim.revision, { metricConfirmed: !claim.metricConfirmed }), onSuccess: refresh });
  const result = claim.review && "status" in claim.review ? claim.review : null;
  const error = review.error ?? interview.error ?? confirmMetric.error;
  return <li className="rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
    <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{claim.claimText}</p>
    <div className="mt-2 flex flex-wrap gap-2 text-xs font-bold">
      {claim.reviewStatus && <span className={`rounded-full px-2.5 py-1 ${claim.reviewStatus === "OK" ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>{statusLabel[claim.reviewStatus]}</span>}
      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">{claim.metricConfirmed ? "수치 확인함" : "수치 미확인"}</span>
    </div>
    {claim.reviewPending && <p className="mt-2 flex items-center gap-2 text-xs text-slate-500"><Loader2 className="animate-spin" size={14}/>점검 중</p>}
    {claim.review && "errorCode" in claim.review && <p className="mt-2 text-xs text-rose-600">점검하지 못했어요 ({claim.review.errorCode}).</p>}
    {result && <div className="mt-3 grid gap-2 text-sm">
      {result.issues.map(issue => <p key={issue.detail} className="flex gap-2 text-amber-800 dark:text-amber-200"><ShieldAlert size={16} className="mt-0.5 shrink-0"/>{issue.detail}</p>)}
      {result.likelyQuestions.length > 0 && <div><p className="text-xs font-extrabold text-slate-500">예상 질문</p><ul className="mt-1 list-disc pl-5 text-slate-700 dark:text-slate-200">{result.likelyQuestions.map(question => <li key={question}>{question}</li>)}</ul></div>}
      {result.suggestedRevision && <p className="rounded-xl bg-slate-50 p-3 text-slate-700 dark:bg-[#151f2f] dark:text-slate-200"><b className="text-xs text-slate-500">수정 제안</b><br/>{result.suggestedRevision}</p>}
    </div>}
    <div className="mt-3 flex flex-wrap gap-2">
      <Button variant="outline" disabled={claim.reviewPending || review.isPending} onClick={() => review.mutate()}>{result ? "다시 점검" : "AI 사전 점검"}</Button>
      <Button disabled={interview.isPending} onClick={() => interview.mutate()}><Mic size={16}/>압박형 면접 연습</Button>
      <Button variant="outline" disabled={confirmMetric.isPending} onClick={() => confirmMetric.mutate()}>{claim.metricConfirmed ? "수치 확인 취소" : "수치 확인함"}</Button>
    </div>
    {error && <p role="alert" className="mt-2 text-xs text-rose-600">{error.message}</p>}
  </li>;
}
