import { SessionGate } from "@/features/session/session-gate";
import { LearningHubScreen } from "@/features/today-learning/learning-hub-screen";

export default function LearningPage() {
  return <SessionGate><LearningHubScreen /></SessionGate>;
}
