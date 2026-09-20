import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeCsrf, decodeCurrentUser, decodeEnvelope } from "@/api/contract";

export const sessionKeys = { all: ["session"] as const, current: () => [...sessionKeys.all, "current"] as const };

export const currentSessionQuery = queryOptions({
  queryKey: sessionKeys.current(),
  queryFn: async () => {
    const csrf = await api.request({ path: "/api/v1/auth/csrf", decode: value => decodeEnvelope(value, decodeCsrf).data });
    api.setCsrfToken(csrf.data.token);
    return (await api.request({ path: "/api/v1/users/me", decode: value => decodeEnvelope(value, decodeCurrentUser).data })).data;
  },
  staleTime: 60_000,
});
