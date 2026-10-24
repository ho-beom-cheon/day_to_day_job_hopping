import { SessionGate } from "@/features/session/session-gate";
import { StudyProgramScreen } from "@/features/study-program/study-program-screen";

export default function StudyProgramPage() {
  return <SessionGate><StudyProgramScreen /></SessionGate>;
}
