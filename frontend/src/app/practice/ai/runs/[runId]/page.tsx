import { AiRunScreen } from "@/features/ai/ai-run-screen";

export default async function AiStudyRunPage({ params }: { params: Promise<{ runId: string }> }) {
  const { runId } = await params;
  return <AiRunScreen runId={runId} />;
}
