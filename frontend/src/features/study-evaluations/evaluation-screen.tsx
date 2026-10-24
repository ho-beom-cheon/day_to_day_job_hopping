"use client";
import { useEffect,useRef,useState } from "react";
import { useMutation,useQuery,useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { QuestionContent } from "@/components/question-content";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { createEvaluation,evaluationAction,getEvaluation,updatePart,type Evaluation,type Part } from "./api";

export function EvaluationScreen({id}:{id:string}){
  const client=useQueryClient(),router=useRouter();
  const query=useQuery({queryKey:["study-evaluation",id],queryFn:()=>getEvaluation(id),refetchInterval:q=>q.state.data?.parts.some(p=>["GENERATING","GRADING"].includes(p.status))?3000:false});
  const retake=useMutation({mutationFn:createEvaluation,onSuccess:e=>router.push(`/tests/study/${e.id}`)});
  const update=(e:Evaluation)=>{client.setQueryData(["study-evaluation",id],e);void client.invalidateQueries({queryKey:["study-evaluations"]});};
  if(query.isPending)return <p className="p-6">평가를 불러오고 있어요.</p>;
  if(query.isError)return <ApiErrorFeedback viewModel={toApiErrorViewModel(query.error)} onRefresh={()=>void query.refetch()}/>;
  const e=query.data;
  return <div className="min-w-0 space-y-5"><Link href="/tests" className="text-sm font-bold text-blue-700">← 시험 목록</Link><header><p className="text-sm font-bold text-blue-600">완료한 학습 기반 평가</p><h1 className="mt-2 text-2xl font-black text-slate-950">{e.title}</h1><p className="mt-3 text-sm leading-6 text-slate-600">{e.objectiveCount}개 목표 · {e.partCount}회차 · {e.submittedPartCount}회차 제출<br/>생성한 문제를 시작할 때 제한 시간이 시작돼요. 제출 후 정답과 해설을 확인할 수 있어요.</p></header>
    <details className="rounded-2xl border border-slate-200 bg-white p-4"><summary className="cursor-pointer font-bold text-slate-800">평가에 포함된 학습 목표 {e.objectiveCount}개</summary><ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-slate-600">{e.objectives.map(o=><li key={o.id}>[{o.subject}{o.extra?" · 추가 학습":""}] {o.description}</li>)}</ul></details>
    {e.score!=null&&<p className="rounded-2xl bg-emerald-50 p-5 text-xl font-black text-slate-950">전체 평가 점수 {e.score}점 · {e.objectiveCount}문항 채점 완료</p>}
    {e.parts.map(part=><EvaluationPart key={part.id} evaluationId={id} part={part} onUpdate={update}/>)}
    <p className="text-xs leading-6 text-slate-500">AI 생성·자동 검증 자료예요. 원문 근거와 해설을 함께 확인하세요. 서술형 채점이 보류되면 점수를 확정하지 않아요. 이미 시작한 평가의 범위는 유지되며 이후 학습은 시험 목록에서 새 평가로 이어져요.</p>
    {e.submittedPartCount===e.partCount&&<Button variant="outline" disabled={retake.isPending} onClick={()=>retake.mutate(evaluationAction(e.kind,e.key,e.id))}>같은 범위의 새 문제로 재응시</Button>}
    {retake.isError&&<ApiErrorFeedback viewModel={toApiErrorViewModel(retake.error)} onRefresh={()=>retake.variables&&retake.mutate(retake.variables)}/>}
  </div>;
}
function EvaluationPart({evaluationId,part,onUpdate}:{evaluationId:string;part:Part;onUpdate:(e:Evaluation)=>void}){
  const [answers,setAnswers]=useState(part.answers);const draft=useRef(answers);
  const saved=useRef(JSON.stringify(part.answers));const revision=useRef(part.revision);
  useEffect(()=>{draft.current=answers;},[answers]);
  useEffect(()=>{revision.current=part.revision;},[part.revision]);
  const [now,setNow]=useState(()=>Date.parse(part.serverNow));
  const submitted=!!part.result;const active=part.status==="ACTIVE"||part.status==="EXPIRED";
  const busy=useRef(false);const [message,setMessage]=useState("");
  const mutation=useMutation({mutationFn:({action,values,version}:{action:"generate"|"start"|"grade"|"save"|"submit";values?:Record<string,string>;version?:number})=>updatePart(evaluationId,part.partNo,action,values,version),onSuccess:(e,args)=>{
    const current=e.parts.find(p=>p.id===part.id)!;revision.current=current.revision;
    if(args.action==="save"||args.action==="submit"){saved.current=JSON.stringify(args.values);setMessage(args.action==="save"?"답안 저장 완료":"답안을 제출했어요.");}
    onUpdate(e);
  },onSettled:()=>{busy.current=false;}});
  const run=(action:"generate"|"start"|"grade"|"save"|"submit")=>{
    if(busy.current)return;busy.current=true;
    mutation.mutate({action,values:action==="save"||action==="submit"?{...draft.current}:undefined,version:action==="save"||action==="submit"?revision.current:undefined});
  };
  const runRef=useRef(run);useEffect(()=>{runRef.current=run;});
  const left=part.expiresAt?Math.max(0,Math.ceil((Date.parse(part.expiresAt)-now)/1000)):0;
  useEffect(()=>{if(!active)return;const receivedAt=Date.now(),serverTime=Date.parse(part.serverNow);const timer=setInterval(()=>setNow(serverTime+Date.now()-receivedAt),1000);return()=>clearInterval(timer);},[active,part.serverNow]);
  useEffect(()=>{if(!active)return;const timer=setInterval(()=>{if(JSON.stringify(draft.current)!==saved.current)runRef.current("save");},5000);return()=>clearInterval(timer);},[active]);
  const expiredAttempted=useRef(false);
  useEffect(()=>{if(active&&left===0&&!busy.current&&!expiredAttempted.current){expiredAttempted.current=true;runRef.current("submit");}},[active,left,now]);
  useEffect(()=>{const leave=(event:BeforeUnloadEvent)=>{if(active&&JSON.stringify(draft.current)!==saved.current){event.preventDefault();event.returnValue="";}};window.addEventListener("beforeunload",leave);return()=>window.removeEventListener("beforeunload",leave);},[active]);
  const gradeAttempted=useRef(false);
  useEffect(()=>{if(part.status==="HOLD"&&!part.gradingRequested&&!gradeAttempted.current){gradeAttempted.current=true;runRef.current("grade");}},[part.status,part.gradingRequested]);
  return <section aria-label={`${part.partNo}회차 평가`} className="min-w-0 rounded-3xl border border-slate-200 bg-white p-5 sm:p-7">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-black text-slate-950">{part.partNo}회차 · {part.plan.slots.length}문항</h2><span className="text-sm font-bold text-blue-700">{active?`남은 시간 ${Math.floor(left/60)}분 ${left%60}초`:`예상 ${Math.ceil(part.plan.assessmentSeconds/60)}분`}</span></div>
    {["PENDING","FAILED"].includes(part.status)&&<><p className="mt-3 text-sm text-slate-600">{part.status==="FAILED"?"문제를 생성하지 못했어요. 저장된 평가 범위로 다시 시도할 수 있어요.":"이 회차에 필요한 문제를 생성해요. 생성 중에는 시간이 차감되지 않아요."}</p><Button className="mt-4" disabled={mutation.isPending} onClick={()=>run("generate")}>{part.status==="FAILED"?"문제 생성 다시 시도":"이 회차 문제 생성"}</Button></>}
    {part.status==="GENERATING"&&<p role="status" className="mt-4 text-sm text-blue-700">문제와 원문 근거를 확인하고 있어요. 화면을 나가도 작업은 계속돼요.</p>}
    {part.status==="READY"&&<Button className="mt-4" disabled={mutation.isPending} onClick={()=>run("start")}>문제 풀이 시작</Button>}
    {part.questions?.map((q,index)=>{const grade=part.result?.results.find(r=>r.questionId===q.id);const review=part.review?.find(r=>r.id===q.id);return <article key={q.id} className="mt-6 min-w-0 border-t border-slate-200 pt-5"><p className="mb-3 text-sm font-bold text-blue-700">{index+1}. {q.type==="MCQ"?"객관식":q.type==="INTERVIEW"?"면접 답변":"단답"}</p><QuestionContent body={q.prompt}/>
      {q.type==="MCQ"?<fieldset disabled={submitted||left===0} className="mt-4 grid min-w-0 gap-2"><legend className="sr-only">{index+1}번 정답 선택</legend>{q.choices.map((choice,i)=><label key={i} className="flex min-w-0 gap-3 rounded-xl border border-slate-200 p-3"><input type="radio" name={`${part.id}-${q.id}`} value={i+1} checked={(submitted?part.answers:answers)[q.id]===String(i+1)} onChange={()=>setAnswers(v=>({...v,[q.id]:String(i+1)}))} className="mt-1 shrink-0"/><div className="min-w-0"><QuestionContent body={`${i+1}. ${choice}`} compact/></div></label>)}</fieldset>:<label className="mt-4 block text-sm font-bold text-slate-700">내 답변<textarea aria-label={`${index+1}번 답변`} value={(submitted?part.answers:answers)[q.id]??""} disabled={submitted||left===0} maxLength={12000} onChange={event=>setAnswers(v=>({...v,[q.id]:event.target.value}))} className="mt-2 min-h-36 w-full resize-y rounded-xl border border-slate-300 p-3 font-normal leading-7 text-slate-900"/></label>}
      {grade&&<div className="mt-4 min-w-0 rounded-2xl bg-slate-50 p-4"><p className="font-bold text-slate-900">{grade.status==="HOLD"?"채점 보류":`${grade.earnedPoints} / 10점`}{q.type==="MCQ"&&review?` · 정답 ${review.answer}번`:""}</p>{grade.holdReason&&<p className="mt-2 text-sm text-amber-800">{grade.holdReason}</p>}<h3 className="mt-4 text-sm font-bold">모범 답변</h3><QuestionContent body={grade.correctAnswer}/><h3 className="mt-4 text-sm font-bold">해설</h3><QuestionContent body={grade.explanation}/>{grade.feedback&&<><h3 className="mt-4 text-sm font-bold">내 답변 피드백</h3><QuestionContent body={grade.feedback}/></>}{grade.improvedAnswer&&<QuestionContent body={grade.improvedAnswer}/>}<details className="mt-4 text-sm"><summary className="cursor-pointer font-bold">원문 근거{review?.wrongAnswerExplanations.length?"와 오답 해설":""}</summary>{review?.sourceEvidence.map((s,i)=><div key={i} className="mt-3"><p className="font-bold">학습 자료 {s.sourceId}</p><QuestionContent body={s.excerpt}/></div>)}{review?.wrongAnswerExplanations.map((text,i)=><div key={i} className="mt-3"><QuestionContent body={text}/></div>)}</details></div>}
    </article>;})}
    {active&&<div className="mt-5 flex flex-wrap gap-3"><Button variant="outline" disabled={mutation.isPending||left===0} onClick={()=>run("save")}>답안 저장</Button><Button disabled={mutation.isPending} onClick={()=>run("submit")}>{left===0?"저장된 답안 제출":"제출하고 정답·해설 보기"}</Button></div>}
    {message&&<p role="status" className="mt-3 text-sm text-blue-700">{message}</p>}
    {part.status==="GRADING"&&<p role="status" className="mt-5 text-sm font-bold text-blue-700">서술형 답안을 채점하고 있어요. 정답·해설을 보며 복습할 수 있어요.</p>}
    {part.status==="HOLD"&&<Button className="mt-5" variant="outline" disabled={mutation.isPending} onClick={()=>run("grade")}>보류된 서술형 채점 다시 요청</Button>}
    {part.status==="GRADED"&&<p className="mt-5 text-lg font-black text-slate-950">이 회차 점수 {part.result?.score}점</p>}
    {part.errorCode&&<p className="mt-3 text-xs text-amber-800">{part.errorCode==="AI_PROVIDER_QUOTA_EXHAUSTED"?"OpenAI API 잔액을 확인해 주세요.":"AI 처리 결과를 확정하지 못했어요. 다시 시도해 주세요."}</p>}
    {mutation.isError&&<ApiErrorFeedback viewModel={toApiErrorViewModel(mutation.error)} onRefresh={()=>{if(mutation.variables?.action==="save"||mutation.variables?.action==="submit")void getEvaluation(evaluationId).then(e=>{revision.current=e.parts.find(p=>p.id===part.id)!.revision;onUpdate(e);});else if(mutation.variables)run(mutation.variables.action);}}/>}
  </section>;
}
