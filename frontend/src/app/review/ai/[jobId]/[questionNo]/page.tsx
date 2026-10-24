import { AiWrongDetailScreen } from "@/features/ai/ai-wrong-detail-screen";

export default async function AiWrongPage({ params }: { params: Promise<{ jobId: string; questionNo: string }> }) {
  const { jobId, questionNo } = await params;
  return <AiWrongDetailScreen jobId={jobId} questionNo={Number(questionNo)} />;
}
