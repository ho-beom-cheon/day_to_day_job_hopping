import { expect, it, vi } from "vitest";
import { verifyRuntime } from "./verify-runtime.mjs";

const options = {backendHealthUrl:"http://backend:9090/actuator/health", nginxOrigin:"http://nginx"};
function responses() {
  return vi.fn()
    .mockResolvedValueOnce(new Response('{"status":"UP"}'))
    .mockResolvedValueOnce(new Response("데일리 이직"))
    .mockResolvedValueOnce(new Response(null, {status:401, headers:{"X-Trace-Id":"test"}}))
    .mockResolvedValueOnce(new Response(null, {status:404}));
}
it("requires the complete internal-health and proxy chain", async () => {
  const fetcher = responses();
  await verifyRuntime(options, fetcher);
  expect(fetcher.mock.calls.map(([url])=>url)).toEqual([options.backendHealthUrl,"http://nginx/","http://nginx/api/v1/","http://nginx/actuator/health"]);
});
it("fails if the application answers but its database is down", async () => {
  const fetcher=vi.fn().mockResolvedValue(new Response('{"status":"DOWN"}', {status:503}));
  await expect(verifyRuntime(options,fetcher)).rejects.toThrow("not healthy");
  expect(fetcher).toHaveBeenCalledTimes(1);
});
it("fails on a public management endpoint", async () => {
  const fetcher=responses();
  const guarded=async (url, init) => url.endsWith("/actuator/health") && url.startsWith("http://nginx") ? new Response('{"status":"UP"}') : fetcher(url,init);
  await expect(verifyRuntime(options,guarded)).rejects.toThrow("must not be exposed");
});
