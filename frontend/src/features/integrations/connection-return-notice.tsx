"use client";
import { useSyncExternalStore } from "react";
const subscribe = () => () => {};
const snapshot = () => window.location.search;
const serverSnapshot = () => "";
export function ConnectionReturnNotice() {
  const query = new URLSearchParams(useSyncExternalStore(subscribe, snapshot, serverSnapshot));
  const result = query.get("notion") ?? query.get("slack") ?? query.get("openrouter");
  if (!result) return null;
  return <p role="status" className="rounded-2xl bg-blue-50 p-4 text-sm leading-6 text-blue-900 dark:bg-blue-950 dark:text-blue-100">{
    result === "connected" ? "인증 절차가 끝났습니다. 아래에서 연결 상태를 확인해 주세요. Slack 알림은 연결 후 직접 켤 수 있습니다."
      : result === "login_required" ? "로그인 세션이 만료되었습니다. 로그인 후 같은 사이트 주소에서 연결을 다시 시작해 주세요."
      : "연결이 완료되지 않았습니다. 승인을 취소했거나 인증 시간이 지났을 수 있습니다. 연결 버튼으로 다시 시작해 주세요. 연결 중 설정을 바꿨다면 공급자 관리 페이지에서 사용하지 않는 권한도 확인해 주세요."}</p>;
}
