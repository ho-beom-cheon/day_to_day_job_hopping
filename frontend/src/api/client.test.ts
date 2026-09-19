import { describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient, createMutationAction, errorKind } from "./client";
import { readId } from "@/types/id";

describe("API transport foundation", () => {
  it("preserves bigint IDs and round-trips ETag, CSRF and idempotency headers", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response('{"id":"9223372036854775807"}', {headers: {ETag: '"9"'}}));
    const client = createApiClient(fetcher);
    client.setCsrfToken("csrf-test");
    const action = createMutationAction({id: "9223372036854775807"}, "logical-action");
    const result = await client.request({path: "/api/v1/contract-test-only", method: "POST", body: action.getPayload(), etag: '"8"', idempotencyKey: action.idempotencyKey, decode: value => readId((value as {id: unknown}).id)});
    expect(result).toEqual({data: "9223372036854775807", etag: '"9"'});
    const init = fetcher.mock.calls[0][1]!;
    expect(init.credentials).toBe("same-origin");
    expect(init.cache).toBe("no-store");
    expect(init.redirect).toBe("error");
    const headers = new Headers(init.headers);
    expect(headers.get("If-Match")).toBe('"8"');
    expect(headers.get("X-CSRF-TOKEN")).toBe("csrf-test");
    expect(headers.get("Idempotency-Key")).toBe("logical-action");
    expect(init.body).toBe('{"id":"9223372036854775807"}');
  });

  it.each([[401,"authentication"],[403,"forbidden"],[409,"conflict"],[412,"stale"],[422,"validation"],[428,"precondition"],[429,"rate-limit"],[503,"server"]] as const)("maps HTTP %s without inventing an ErrorEnvelope", async (status, kind) => {
    const payload = { contractShape: "unknown until OpenAPI", code: "PRESERVED" };
    const client = createApiClient(vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(payload), {status, headers: {"X-Trace-Id":"trace-test"}})));
    await expect(client.request({path:"/api/v1/contract-test-only", decode: value => value})).rejects.toMatchObject({status, kind, payload, traceId:"trace-test"});
    expect(errorKind(status)).toBe(kind);
  });

  it("preserves the action key and payload across explicit retries, but uses a new key for new intent", () => {
    const original = {id:"9007199254740993", answer:{text:"first"}};
    const action = createMutationAction(original);
    original.answer.text = "edited";
    action.getPayload().answer.text = "mutated copy";
    expect(action.getPayload().answer.text).toBe("first");
    expect(action.idempotencyKey).not.toBe(createMutationAction(original).idempotencyKey);
  });

  it("reuses the same action on a network failure without silently replaying a mutation", async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValueOnce(new TypeError("offline")).mockResolvedValueOnce(new Response(null, {status:204}));
    const client = createApiClient(fetcher);
    const action = createMutationAction({id:"9"});
    const request = () => client.request({path:"/api/v1/contract-test-only", method:"POST", body:action.getPayload(), idempotencyKey:action.idempotencyKey, decode:() => undefined});
    await expect(request()).rejects.toThrow("offline");
    expect(fetcher).toHaveBeenCalledTimes(1);
    await request();
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get("Idempotency-Key")).toBe(new Headers(fetcher.mock.calls[1][1]?.headers).get("Idempotency-Key"));
  });

  it("clears the in-memory CSRF token after session changes", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => new Response(null, {status:204}));
    const client = createApiClient(fetcher);
    client.setCsrfToken("old");
    client.setCsrfToken(undefined);
    await client.request({path:"/api/v1/contract-test-only", method:"POST", decode:() => undefined});
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).has("X-CSRF-TOKEN")).toBe(false);
  });

  it("keeps status and trace information even when the proxy returns HTML", async () => {
    const client = createApiClient(vi.fn<typeof fetch>().mockResolvedValue(new Response("<html>unavailable</html>", {status:502})));
    await expect(client.request({path:"/api/v1/contract-test-only", decode:value=>value})).rejects.toBeInstanceOf(ApiError);
  });

  it("rejects numeric IDs and a path escaping the API prefix", async () => {
    expect(() => readId(123)).toThrow("JSON string");
    const fetcher = vi.fn<typeof fetch>();
    await expect(createApiClient(fetcher).request({path:"/api/v1/../../outside", decode:value=>value})).rejects.toThrow("same-origin");
    expect(fetcher).not.toHaveBeenCalled();
  });
});
