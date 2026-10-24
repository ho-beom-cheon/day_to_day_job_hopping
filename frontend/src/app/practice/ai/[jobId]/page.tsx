import { AiPracticeScreen } from "@/features/ai/ai-practice-screen";

export default async function AiPracticePage({ params }: { params: Promise<{ jobId: string }> }) {
  const { jobId } = await params;
  return <AiPracticeScreen jobId={jobId} />;
}
