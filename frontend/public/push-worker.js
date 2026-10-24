self.addEventListener("push", (event) => {
  let payload = {};
  try { payload = event.data ? event.data.json() : {}; } catch { payload = {}; }
  const title = typeof payload.title === "string" ? payload.title : "Daily Leap";
  const body = typeof payload.body === "string" ? payload.body : "새 알림이 도착했어요.";
  const targetPath = typeof payload.targetPath === "string" && payload.targetPath.startsWith("/") && !payload.targetPath.startsWith("//")
    ? payload.targetPath : "/notifications";
  const tag = typeof payload.tag === "string" ? payload.tag : undefined;
  event.waitUntil(self.registration.showNotification(title, {
    body, tag, renotify: false, icon: "/icon.svg", data: { targetPath },
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const candidate = event.notification.data?.targetPath;
  const targetPath = typeof candidate === "string" && candidate.startsWith("/") && !candidate.startsWith("//")
    ? candidate : "/notifications";
  event.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(async (windows) => {
    const existing = windows.find((client) => new URL(client.url).origin === self.location.origin);
    if (existing) {
      await existing.navigate(targetPath);
      return existing.focus();
    }
    return self.clients.openWindow(targetPath);
  }));
});
