import { SessionGate } from "@/features/session/session-gate";
import { LearningCalendarScreen } from "@/features/calendar/learning-calendar-screen";

export default function CalendarPage() {
  return <SessionGate><LearningCalendarScreen /></SessionGate>;
}
