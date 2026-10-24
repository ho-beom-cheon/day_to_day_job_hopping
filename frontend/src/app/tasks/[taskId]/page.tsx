import { SessionGate } from "@/features/session/session-gate";
import { TaskScreen } from "@/features/daily-task/task-screen";

export default async function TaskPage({ params }: { params: Promise<{ taskId: string }> }) {
  const { taskId } = await params;
  return <SessionGate><TaskScreen taskId={taskId}/></SessionGate>;
}
