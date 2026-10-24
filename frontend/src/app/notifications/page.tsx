import { SessionGate } from "@/features/session/session-gate";
import { NotificationCenterScreen } from "@/features/notifications/notification-center-screen";

export default function NotificationsPage() {
  return <SessionGate><NotificationCenterScreen/></SessionGate>;
}
