import { SessionGate } from "@/features/session/session-gate";
import { ExtraLearningScreen } from "@/features/extra-learning/extra-learning-screen";

export default async function ExtraLearningPage({ searchParams }: { searchParams: Promise<{ topic?: string }> }) {
  const { topic } = await searchParams;
  return <SessionGate><ExtraLearningScreen initialTopic={topic}/></SessionGate>;
}
