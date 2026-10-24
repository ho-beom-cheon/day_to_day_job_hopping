export type BrowserPushSubscription = {
  endpoint: string;
  expirationTime: number | null;
  keys: { p256dh: string; auth: string };
};

const DEVICE_KEY = "daily-career.push-device-id";

export function browserPushSupported() {
  return typeof window !== "undefined" && window.isSecureContext && "serviceWorker" in navigator
    && "PushManager" in window && "Notification" in window;
}

export function currentDeviceId() {
  if (typeof window === "undefined") return null;
  let id = window.localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    window.localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

export function currentDeviceName() {
  if (typeof navigator === "undefined") return "웹 브라우저";
  const browser = navigator.userAgent.includes("Edg/") ? "Edge"
    : navigator.userAgent.includes("Chrome/") ? "Chrome"
      : navigator.userAgent.includes("Firefox/") ? "Firefox"
        : navigator.userAgent.includes("Safari/") ? "Safari" : "웹 브라우저";
  const platform = navigator.userAgent.includes("Windows") ? "Windows"
    : navigator.userAgent.includes("Mac") ? "macOS"
      : navigator.userAgent.includes("Android") ? "Android"
        : navigator.userAgent.includes("iPhone") || navigator.userAgent.includes("iPad") ? "iOS" : "기기";
  return `${platform} ${browser}`.slice(0, 80);
}

export function applicationServerKey(value: string) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  const decoded = atob(padded);
  return Uint8Array.from(decoded, (character) => character.charCodeAt(0));
}

function encode(buffer: ArrayBuffer | null) {
  if (!buffer) throw new Error("브라우저가 푸시 구독 키를 제공하지 않았습니다.");
  let binary = "";
  for (const byte of new Uint8Array(buffer)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function subscribeBrowser(publicKey: string): Promise<BrowserPushSubscription> {
  if (!browserPushSupported()) throw new Error("이 브라우저에서는 웹 푸시를 사용할 수 없습니다.");
  const permission = Notification.permission === "default" ? await Notification.requestPermission() : Notification.permission;
  if (permission !== "granted") throw new Error("브라우저 알림 권한이 허용되지 않았습니다.");
  await navigator.serviceWorker.register("/push-worker.js", { scope: "/" });
  const registration = await navigator.serviceWorker.ready;
  const existing = await registration.pushManager.getSubscription();
  const subscription = existing ?? await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: applicationServerKey(publicKey),
  });
  return {
    endpoint: subscription.endpoint,
    expirationTime: subscription.expirationTime,
    keys: { p256dh: encode(subscription.getKey("p256dh")), auth: encode(subscription.getKey("auth")) },
  };
}

export async function unsubscribeBrowser() {
  if (!browserPushSupported()) return true;
  const registration = await navigator.serviceWorker.getRegistration("/");
  const subscription = await registration?.pushManager.getSubscription();
  return subscription ? subscription.unsubscribe() : true;
}
