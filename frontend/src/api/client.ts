export type ErrorKind = "authentication" | "forbidden" | "conflict" | "stale" | "validation" | "precondition" | "rate-limit" | "server" | "request";

export function errorKind(status: number): ErrorKind {
  switch (status) {
    case 401: return "authentication";
    case 403: return "forbidden";
    case 409: return "conflict";
    case 412: return "stale";
    case 422: return "validation";
    case 428: return "precondition";
    case 429: return "rate-limit";
    default: return status >= 500 ? "server" : "request";
  }
}

export class ApiError extends Error {
  readonly kind: ErrorKind;
  constructor(readonly status: number, readonly payload: unknown, readonly traceId: string | null) {
    super(`API request failed (${status})`);
    this.name = "ApiError";
    this.kind = errorKind(status);
  }
}

type RequestOptions<T> = {
  path: `/api/v1/${string}`;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  etag?: string;
  idempotencyKey?: string;
  decode: (payload: unknown) => T;
};

/** Low-level transport only; canonical DTO/error decoders and routes await GAP-001. */
export function createApiClient(fetcher: typeof fetch = fetch) {
  let csrfToken: string | undefined;
  return {
    setCsrfToken(token: string | undefined) { csrfToken = token; },
    async request<T>(options: RequestOptions<T>): Promise<{ data: T; etag: string | null }> {
      // Prevent tokens/cookies from being sent outside the same-origin API prefix.
      const url = new URL(options.path, "https://same-origin.invalid");
      if (url.origin !== "https://same-origin.invalid" || !url.pathname.startsWith("/api/v1/")) {
        throw new TypeError("Expected a same-origin /api/v1/ path");
      }
      const method = options.method ?? "GET";
      const headers = new Headers({ Accept: "application/json" });
      if (options.body !== undefined) headers.set("Content-Type", "application/json");
      if (options.etag !== undefined) headers.set("If-Match", options.etag);
      if (options.idempotencyKey !== undefined) headers.set("Idempotency-Key", options.idempotencyKey);
      if (method !== "GET" && csrfToken) headers.set("X-CSRF-TOKEN", csrfToken);
      const response = await fetcher(url.pathname + url.search, {
        method, headers, credentials: "same-origin", cache: "no-store", redirect: "error",
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
      const text = await response.text();
      let payload: unknown = undefined;
      if (text) {
        try { payload = JSON.parse(text); }
        catch { payload = text; }
      }
      if (!response.ok) throw new ApiError(response.status, payload, response.headers.get("X-Trace-Id"));
      return { data: options.decode(payload), etag: response.headers.get("ETag") };
    },
  };
}

/** One logical action: the serialized payload and key survive every network retry. */
export function createMutationAction<T>(payload: T, key: string = crypto.randomUUID()) {
  const snapshot = JSON.stringify(payload);
  return Object.freeze({
    idempotencyKey: key,
    getPayload: (): T => JSON.parse(snapshot) as T,
  });
}
