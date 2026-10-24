import { LearningSessionScreen } from "@/features/today-learning/session-screen";

export default async function LearningSessionPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params;
  return <LearningSessionScreen sessionId={sessionId} />;
}
