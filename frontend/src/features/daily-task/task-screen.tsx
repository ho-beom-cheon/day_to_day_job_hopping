"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, Check, CircleAlert, Flag, Loader2, MessageSquareText, Send, Shuffle, Star } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { QuestionContent } from "@/components/question-content";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import type { Id } from "@/types/id";
import { failureMessage, formatLabel, type DailyTask, type Turn } from "./contract";
import { finishTask, reportIssue, saveCard, saveDraft, selfGrade, sendFeedback, startTask, submitTurn, swapTask, taskKeys, taskQuery } from "./queries";

const panel = "rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7";
const textarea = "min-h-40 w-full rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-900 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-[#151f2f] dark:text-slate-100";

export function TaskScreen({ taskId }: { taskId: Id }) {
  const query = useQuery(taskQuery(taskId));
  if (query.isPending) return <StatePanel state="loading"/>;
  if (query.isError) return <StatePanel state="error" onRefresh={() => void query.refetch()}/>;
  return <TaskBody task={query.data}/>;
}

function useTaskMutation<T>(taskId: Id, run: (value: T) => Promise<DailyTask>) {
  const client = useQueryClient();
  return useMutation({ mutationFn: run, onSuccess: task => { client.setQueryData(taskKeys.task(taskId), task); void client.invalidateQueries({ queryKey: taskKeys.today() }); } });
}

function TaskBody({ task }: { task: DailyTask }) {
  const router = useRouter();
  const [sheetOpened, setSheetOpened] = useState(false);
  const start = useTaskMutation(task.taskId, () => startTask(task.taskId));
  const swap = useMutation({ mutationFn: () => swapTask(task.taskId), onSuccess: next => router.replace(`/tasks/${next.taskId}`) });
  const finish = useTaskMutation(task.taskId, () => finishTask(task.taskId));
  const main = task.turns.find(turn => turn.turnNo === 1);
  const followups = task.turns.filter(turn => turn.turnNo > 1);
  const notice = failureMessage(task.failureCode);
  const canFinish = task.status === "FEEDBACK" && task.result !== null && !task.pending;

  if (task.status === "PREPARING") return <section className={panel}><p className="flex items-center gap-2 text-sm font-semibold text-slate-600 dark:text-slate-300"><Loader2 className="animate-spin" size={18}/>과제를 만들고 있어요. 이 화면은 자동으로 새로고침돼요.</p></section>;
  if (!task.task) return <section className={panel}><p className="text-sm text-rose-600">{notice ?? "과제를 불러오지 못했어요."}</p><Link href="/workspace" className="mt-3 inline-block text-sm font-bold text-blue-700 underline">홈으로</Link></section>;

  return <div className="mx-auto grid max-w-4xl gap-5 pb-10">
    <header className={panel}>
      <div className="flex flex-wrap items-center gap-2 text-xs font-extrabold">
        <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300">{task.competency.code} · {task.competency.title}</span>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">{formatLabel[task.format]}</span>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-slate-600 dark:bg-slate-800 dark:text-slate-300">작성 약 {task.task.estimatedMinutes}분 · 꼬리질문 최대 {task.followupLimit}회</span>
        {task.gradingMode === "SELF" && <span className="rounded-full bg-amber-50 px-2.5 py-1 text-amber-700 dark:bg-amber-950 dark:text-amber-200">자가 채점</span>}
      </div>
      <h1 className="mt-3 text-2xl font-black tracking-[-.03em] text-[#0a1837] dark:text-slate-50">{task.task.title}</h1>
      <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{task.planReason}</p>
      {notice && <p role="status" className="mt-3 flex items-center gap-2 rounded-xl bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800 dark:bg-amber-950/50 dark:text-amber-200"><CircleAlert size={16}/>{notice}</p>}
    </header>

    <section className={panel}>
      <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">상황</h2>
      <div className="mt-3"><QuestionContent body={task.task.situation}/></div>
      {task.task.material && <div className="mt-4 rounded-2xl bg-slate-50 p-4 dark:bg-[#151f2f]"><QuestionContent body={task.task.material}/></div>}
      <div className="mt-6 rounded-2xl border-l-4 border-blue-500 bg-blue-50 px-5 py-4 dark:border-blue-400 dark:bg-blue-500/10">
        <h2 className="text-sm font-extrabold text-blue-700 dark:text-blue-300">질문</h2>
        <div className="mt-1 text-[#0a1837] dark:text-slate-100"><QuestionContent body={task.task.question}/></div>
      </div>
      {task.sheet && <details className="mt-6 rounded-2xl border border-slate-200 p-4 dark:border-slate-700" onToggle={event => { if ((event.target as HTMLDetailsElement).open) setSheetOpened(true); }}>
        <summary className="flex cursor-pointer items-center gap-2 text-sm font-bold text-slate-700 dark:text-slate-200"><BookOpen size={16}/>역량 시트 참고 (v{task.sheet.version} · {task.sheet.reviewLabel})</summary>
        <p className="mt-2 text-xs text-slate-500">펼쳐 보고 답하면 &quot;참고하며 답함&quot;으로 기록돼요.</p>
        <div className="mt-3"><QuestionContent body={task.sheet.markdown}/></div>
      </details>}
      <ReportIssue task={task}/>
    </section>

    {task.status === "READY" && <section className={panel}>
      <p className="text-sm text-slate-600 dark:text-slate-300">준비되면 시작하세요. 답은 자동으로 임시 저장돼요.</p>
      <div className="mt-4 flex flex-wrap gap-2"><Button disabled={start.isPending} onClick={() => start.mutate(undefined)}>시작하기</Button>{task.status === "READY" && <Button variant="outline" disabled={swap.isPending} onClick={() => swap.mutate()}><Shuffle size={16}/>다른 역량으로 바꾸기</Button>}</div>
    </section>}

    {main && <AnswerTurn task={task} turn={main} label="내 답" sheetOpened={sheetOpened} rows={10}
      hint={task.format === "INTERVIEW" ? "면접에서 말하듯 3~6문장이면 충분해요. 더 깊은 부분은 꼬리질문에서 이어서 물어봐요." : "질문이 묻는 것만 핵심 위주로 써 보세요. 빠진 부분은 꼬리질문에서 이어서 물어봐요."}/>}

    {task.status === "GRADING" && !followups.some(turn => turn.submitted) && <section className={panel}><p className="flex items-center gap-2 text-sm font-semibold text-slate-600 dark:text-slate-300"><Loader2 className="animate-spin" size={18}/>AI가 루브릭으로 채점하고 있어요.</p></section>}

    {task.gradingMode === "SELF" && task.status === "FEEDBACK" && !task.result && <SelfGrade task={task}/>}
    {task.result && <ResultPanel task={task}/>}

    {followups.map(turn => <AnswerTurn key={turn.turnNo} task={task} turn={turn} label={`꼬리질문 ${turn.turnNo - 1}`} sheetOpened={sheetOpened} rows={5}/>)}

    {task.result && <CardEditor task={task}/>}

    {(canFinish || task.status === "COMPLETED") && <section className={panel}>
      {task.status === "COMPLETED"
        ? <p className="flex items-center gap-2 text-sm font-bold text-emerald-700 dark:text-emerald-300"><Check size={18}/>과제를 마쳤어요. 카드와 평가는 계속 고칠 수 있어요.</p>
        : <><p className="text-sm text-slate-600 dark:text-slate-300">꼬리질문은 언제든 여기까지로 끝낼 수 있어요.</p><Button className="mt-3" disabled={finish.isPending} onClick={() => finish.mutate(undefined)}><Check size={17}/>과제 마치기</Button></>}
      <Usefulness task={task}/>
      <Link href="/workspace" className="mt-4 inline-block text-sm font-bold text-blue-700 underline dark:text-blue-300">홈으로</Link>
    </section>}
    {(start.error || finish.error || swap.error) && <p role="alert" className="text-sm font-semibold text-rose-600">{(start.error ?? finish.error ?? swap.error)?.message}</p>}
  </div>;
}

function AnswerTurn({ task, turn, label, hint, sheetOpened, rows }: { task: DailyTask; turn: Turn; label: string; hint?: string; sheetOpened: boolean; rows: number }) {
  const editable = !turn.submitted && (turn.turnNo === 1 ? task.status === "IN_PROGRESS" : task.status === "FEEDBACK" && turn.turnNo === Math.max(...task.turns.map(t => t.turnNo)));
  const [answer, setAnswer] = useState(turn.answer ?? "");
  const saved = useRef(turn.answer ?? "");
  const submit = useTaskMutation(task.taskId, () => submitTurn(task.taskId, turn.turnNo, answer, sheetOpened));
  useEffect(() => {
    if (!editable || answer === saved.current) return;
    const timer = setTimeout(() => { void saveDraft(task.taskId, turn.turnNo, answer, sheetOpened).then(() => { saved.current = answer; }); }, 1500);
    return () => clearTimeout(timer);
  }, [answer, editable, sheetOpened, task.taskId, turn.turnNo]);
  if (turn.turnNo > 1 && !turn.question) return null;
  return <section className={panel}>
    <h2 className="flex items-center gap-2 text-lg font-black text-[#0a1837] dark:text-slate-50">{turn.turnNo > 1 && <MessageSquareText size={19} className="text-blue-600"/>}{label}</h2>
    {turn.turnNo > 1 && <p className="mt-3 rounded-2xl bg-blue-50 px-4 py-3 text-sm font-semibold leading-6 text-blue-900 dark:bg-blue-500/10 dark:text-blue-100">{turn.question}</p>}
    {hint && editable && <p className="mt-2 text-xs text-slate-500">{hint}</p>}
    {editable ? <>
      <textarea aria-label={label} className={`${textarea} mt-3`} rows={rows} maxLength={8000} value={answer} onChange={event => setAnswer(event.target.value)}/>
      <div className="mt-3 flex items-center justify-between gap-3"><span className="text-xs text-slate-400">{answer.length.toLocaleString("ko-KR")} / 8,000자</span><Button disabled={!answer.trim() || submit.isPending} onClick={() => submit.mutate(undefined)}>{submit.isPending ? <Loader2 className="animate-spin" size={16}/> : <Send size={16}/>}제출</Button></div>
      {submit.error && <p role="alert" className="mt-2 text-sm text-rose-600">{submit.error.message}</p>}
    </> : turn.answer ? <p className="mt-3 whitespace-pre-wrap rounded-2xl bg-slate-50 p-4 text-sm leading-6 text-slate-800 dark:bg-[#151f2f] dark:text-slate-200">{turn.answer}</p> : null}
    {turn.turnNo > 1 && turn.pending && <p className="mt-3 flex items-center gap-2 text-sm text-slate-500"><Loader2 className="animate-spin" size={16}/>답을 확인하고 있어요.</p>}
    {turn.turnNo > 1 && turn.feedback && <div className="mt-3 rounded-2xl border border-slate-200 p-4 text-sm leading-6 dark:border-slate-700">
      <p className="text-slate-700 dark:text-slate-200">{turn.feedback}</p>
      {turn.scoreDelta !== null && <p className="mt-2 text-xs font-bold text-blue-700 dark:text-blue-300">점수 보정 {turn.scoreDelta > 0 ? "+" : ""}{turn.scoreDelta}</p>}
      {turn.checkPoints && <ul className="mt-2 grid gap-1 text-xs text-slate-600 dark:text-slate-300">{turn.checkPoints.map(point => <li key={point} className="flex items-center gap-2">{turn.checkPointsMet?.includes(point) ? <Check size={14} className="text-emerald-600"/> : <span className="inline-block size-3.5 rounded-full border border-slate-300"/>}{point}</li>)}</ul>}
    </div>}
  </section>;
}

function ResultPanel({ task }: { task: DailyTask }) {
  const result = task.result!;
  return <section className={panel}>
    <div className="flex flex-wrap items-end justify-between gap-3">
      <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">피드백</h2>
      {result.status === "HOLD" ? <span className="rounded-full bg-amber-50 px-3 py-1 text-sm font-bold text-amber-700">판단 보류</span>
        : <span className="text-3xl font-black text-blue-700 dark:text-blue-300">{result.finalScore}<span className="text-base text-slate-400">/100</span>{result.finalScore !== result.baseScore && <span className="ml-2 text-xs font-bold text-slate-500">본 답 {result.baseScore}</span>}</span>}
    </div>
    {result.status === "HOLD" && <p className="mt-3 text-sm text-amber-800 dark:text-amber-200">{result.holdReason}</p>}
    {task.rubric && <ul className="mt-4 grid gap-2">{task.rubric.map(criterion => <li key={criterion.criterionId} className="rounded-2xl border border-slate-200 px-4 py-3 text-sm dark:border-slate-700">
      <div className="flex items-start justify-between gap-3"><strong className="text-slate-900 dark:text-slate-100">{criterion.description}</strong><span className="shrink-0 font-black text-slate-700 dark:text-slate-200">{criterion.earnedPoints ?? "-"} / {criterion.points}</span></div>
      {criterion.reason && <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{criterion.reason}</p>}
    </li>)}</ul>}
    {result.feedback && <p className="mt-4 text-sm leading-6 text-slate-700 dark:text-slate-200">{result.feedback}</p>}
    {result.missingPoints.length > 0 && <div className="mt-3"><p className="text-xs font-extrabold text-slate-500">놓친 점</p><ul className="mt-1 list-disc pl-5 text-sm text-slate-700 dark:text-slate-200">{result.missingPoints.map(point => <li key={point}>{point}</li>)}</ul></div>}
    {task.task?.modelAnswerPoints && task.task.modelAnswerPoints.length > 0 && <div className="mt-3"><p className="text-xs font-extrabold text-slate-500">좋은 답의 요소</p><ul className="mt-1 list-disc pl-5 text-sm text-slate-700 dark:text-slate-200">{task.task.modelAnswerPoints.map(point => <li key={point}>{point}</li>)}</ul></div>}
    {result.improvedAnswer && <details className="mt-3"><summary className="cursor-pointer text-sm font-bold text-blue-700 dark:text-blue-300">개선 답안 보기</summary><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-700 dark:text-slate-200">{result.improvedAnswer}</p></details>}
  </section>;
}

function SelfGrade({ task }: { task: DailyTask }) {
  const [met, setMet] = useState<string[]>([]);
  const grade = useTaskMutation(task.taskId, () => selfGrade(task.taskId, met));
  return <section className={panel}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">자가 채점</h2>
    <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">좋은 답의 요소와 비교해 충족한 기준을 체크하세요. 점수는 서버가 계산해요.</p>
    {task.task?.modelAnswerPoints && <ul className="mt-3 list-disc pl-5 text-sm text-slate-700 dark:text-slate-200">{task.task.modelAnswerPoints.map(point => <li key={point}>{point}</li>)}</ul>}
    <ul className="mt-4 grid gap-2">{task.rubric?.map(criterion => <li key={criterion.criterionId}><label className="flex items-start gap-3 rounded-2xl border border-slate-200 px-4 py-3 text-sm dark:border-slate-700"><input type="checkbox" className="mt-1" checked={met.includes(criterion.criterionId)} onChange={event => setMet(current => event.target.checked ? [...current, criterion.criterionId] : current.filter(id => id !== criterion.criterionId))}/><span className="flex-1 text-slate-800 dark:text-slate-200">{criterion.description}</span><span className="font-bold text-slate-500">{criterion.points}점</span></label></li>)}</ul>
    <Button className="mt-4" disabled={grade.isPending} onClick={() => grade.mutate(undefined)}>채점 저장</Button>
    {grade.error && <p role="alert" className="mt-2 text-sm text-rose-600">{grade.error.message}</p>}
  </section>;
}

function CardEditor({ task }: { task: DailyTask }) {
  const initial = task.card ?? (task.cardDraft ? { title: task.cardDraft.title, body: task.cardDraft.summary, interviewLine: task.cardDraft.interviewLine, reviewPrompt: task.cardDraft.reviewPrompt } : { title: "", body: "", interviewLine: "", reviewPrompt: "" });
  const [card, setCard] = useState(initial);
  const save = useTaskMutation(task.taskId, () => saveCard(task.taskId, card));
  const field = (key: keyof typeof card, label: string, multiline = false, max = 500) => <label className="grid gap-1 text-xs font-extrabold text-slate-500">{label}
    {multiline ? <textarea className={`${textarea} min-h-28`} maxLength={max} value={card[key]} onChange={event => setCard({ ...card, [key]: event.target.value })}/>
      : <input className="min-h-11 rounded-2xl border border-slate-200 bg-slate-50 px-4 text-sm font-semibold text-slate-900 outline-none focus:border-blue-500 dark:border-slate-700 dark:bg-[#151f2f] dark:text-slate-100" maxLength={max} value={card[key]} onChange={event => setCard({ ...card, [key]: event.target.value })}/>}</label>;
  return <section className={panel}>
    <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">핵심 카드</h2>
    <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">{task.card ? "저장한 카드예요. 언제든 고칠 수 있어요." : task.cardDraft ? "AI 초안을 내 말로 고쳐 저장하세요." : "오늘 배운 점을 내 말로 정리하세요."}</p>
    <div className="mt-4 grid gap-3">{field("title", "제목", false, 200)}{field("body", "3~5줄 요약", true, 4000)}{field("interviewLine", "면접에서 말할 한 문장")}{field("reviewPrompt", "복습 질문")}</div>
    <Button className="mt-4" disabled={save.isPending || !card.title.trim() || !card.body.trim() || !card.interviewLine.trim() || !card.reviewPrompt.trim()} onClick={() => save.mutate(undefined)}>카드 저장</Button>
    {save.isSuccess && <span className="ml-3 text-xs font-bold text-emerald-600">저장했어요</span>}
    {save.error && <p role="alert" className="mt-2 text-sm text-rose-600">{save.error.message}</p>}
  </section>;
}

function Usefulness({ task }: { task: DailyTask }) {
  const [score, setScore] = useState(task.usefulness ?? 0);
  const [note, setNote] = useState("");
  const send = useTaskMutation(task.taskId, () => sendFeedback(task.taskId, score, note));
  return <div className="mt-5 border-t border-slate-200 pt-5 dark:border-slate-700">
    <p className="text-sm font-bold text-slate-800 dark:text-slate-100">오늘 과제가 도움이 됐나요?</p>
    <div className="mt-2 flex gap-1">{[1, 2, 3, 4, 5].map(value => <button key={value} type="button" aria-label={`${value}점`} aria-pressed={score === value} onClick={() => setScore(value)} className="rounded-lg p-1"><Star size={24} className={value <= score ? "fill-amber-400 text-amber-400" : "text-slate-300"}/></button>)}</div>
    <input className="mt-2 min-h-10 w-full rounded-xl border border-slate-200 bg-transparent px-3 text-sm dark:border-slate-700" maxLength={1000} placeholder="한 줄 메모 (선택)" value={note} onChange={event => setNote(event.target.value)}/>
    <Button className="mt-2" variant="outline" disabled={score === 0 || send.isPending} onClick={() => send.mutate(undefined)}>평가 보내기</Button>
    {send.isSuccess && <span className="ml-3 text-xs font-bold text-emerald-600">고마워요</span>}
  </div>;
}

function ReportIssue({ task }: { task: DailyTask }) {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<"MATERIAL" | "FEEDBACK" | "FOLLOWUP">("MATERIAL");
  const [reason, setReason] = useState("FACT_ERROR");
  const [note, setNote] = useState("");
  const [exclude, setExclude] = useState(false);
  const report = useTaskMutation(task.taskId, () => reportIssue(task.taskId, { target, reason, note, excludeScore: exclude }));
  if (!open) return <button type="button" onClick={() => setOpen(true)} className="mt-4 flex items-center gap-1 text-xs font-bold text-slate-500 hover:text-rose-600"><Flag size={14}/>이상해요</button>;
  return <div className="mt-4 grid gap-2 rounded-2xl border border-rose-200 p-4 text-sm dark:border-rose-900">
    <p className="font-bold text-slate-800 dark:text-slate-100">무엇이 이상한가요? 신고한 과제는 다시 출제되지 않아요.</p>
    <div className="grid gap-2 sm:grid-cols-2">
      <select aria-label="신고 대상" className="min-h-10 rounded-xl border border-slate-200 bg-transparent px-3 dark:border-slate-700" value={target} onChange={event => setTarget(event.target.value as typeof target)}><option value="MATERIAL">과제 자료</option><option value="FEEDBACK">채점·피드백</option><option value="FOLLOWUP">꼬리질문</option></select>
      <select aria-label="신고 사유" className="min-h-10 rounded-xl border border-slate-200 bg-transparent px-3 dark:border-slate-700" value={reason} onChange={event => setReason(event.target.value)}><option value="FACT_ERROR">사실 오류</option><option value="GRADING_ERROR">채점 이상</option><option value="AMBIGUOUS">문제가 모호함</option><option value="OUTSIDE_SHEET">시트 밖 내용</option><option value="OTHER">기타</option></select>
    </div>
    <input className="min-h-10 rounded-xl border border-slate-200 bg-transparent px-3 dark:border-slate-700" maxLength={1000} placeholder="메모 (선택)" value={note} onChange={event => setNote(event.target.value)}/>
    <label className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300"><input type="checkbox" checked={exclude} onChange={event => setExclude(event.target.checked)}/>이 과제 점수를 수준 추정에서 빼기</label>
    <div className="flex gap-2"><Button variant="outline" disabled={report.isPending} onClick={() => report.mutate(undefined)}>신고</Button><Button variant="outline" onClick={() => setOpen(false)}>닫기</Button></div>
    {report.isSuccess && <p className="text-xs font-bold text-emerald-600">신고했어요. 이 과제는 더 이상 출제되지 않아요.</p>}
  </div>;
}
