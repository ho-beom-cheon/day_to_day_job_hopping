"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiErrorFeedback } from "@/components/api-error-feedback";
import { Button } from "@/components/ui/button";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { reportPracticeQuestion } from "./practice-api";

export function AiQuestionReport({ jobId, questionNo, reported }: {
  jobId: string; questionNo: number; reported: boolean;
}) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const report = useMutation({
    mutationFn: () => reportPracticeQuestion(jobId, questionNo, reason),
    onSuccess: async () => {
      setOpen(false);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["ai-practice-set", jobId] }),
        client.invalidateQueries({ queryKey: ["ai-practice-run"] }),
        client.invalidateQueries({ queryKey: ["ai-wrong-notes"] }),
        client.invalidateQueries({ queryKey: ["ai-exam-history"] }),
        client.invalidateQueries({ queryKey: ["saved-ai-sets"] }),
      ]);
    },
  });

  if (reported) return <p className="mt-4 rounded-xl bg-amber-50 p-3 text-sm font-bold text-amber-900" role="status">
    이 문항은 오류로 표시됐어요. 정답과 해설을 학습 근거로 사용하지 마세요. 이후 다시 학습과 연습 시험에서 제외됩니다.
  </p>;

  return <div className="mt-4 border-t border-slate-200 pt-4">
    <p className="text-xs leading-5 text-slate-600">AI가 만든 정답과 해설은 검수 전입니다. 학습 내용과 다르면 오류를 알려 주세요.</p>
    {!open ? <Button variant="outline" className="mt-3" onClick={() => setOpen(true)}>정답·해설 오류 알리기</Button>
      : <div className="mt-3 grid gap-3">
        <label className="text-sm font-bold text-slate-800" htmlFor={`report-${jobId}-${questionNo}`}>어떤 부분이 잘못됐나요?</label>
        <textarea id={`report-${jobId}-${questionNo}`} value={reason} onChange={event => setReason(event.target.value)}
          maxLength={1000} rows={3} className="w-full rounded-xl border border-slate-300 p-3 text-sm text-slate-900"
          placeholder="예: 본문에서는 A라고 설명하는데 정답은 B로 되어 있어요." />
        <div className="flex gap-2">
          <Button disabled={!reason.trim() || report.isPending} onClick={() => report.mutate()}>{report.isPending ? "표시 중…" : "오류로 표시"}</Button>
          <Button variant="outline" disabled={report.isPending} onClick={() => setOpen(false)}>취소</Button>
        </div>
        {report.isError && <ApiErrorFeedback viewModel={toApiErrorViewModel(report.error)} onRefresh={() => report.reset()} />}
      </div>}
  </div>;
}
