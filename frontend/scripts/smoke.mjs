import { verifyRuntime } from "./verify-runtime.mjs";

await verifyRuntime({
  backendHealthUrl: process.env.BACKEND_HEALTH_URL ?? "http://backend:9090/actuator/health",
  nginxOrigin: process.env.NGINX_ORIGIN ?? "http://nginx",
  devAuthBypass: process.env.DEV_AUTH_BYPASS === "true",
});
console.log("PASS: Frontend runtime → Backend health (DB UP); Nginx → Frontend/API; management not exposed.");
