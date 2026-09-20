import { readId, type Id } from "@/types/id";

type RecordValue = Record<string, unknown>;

export type ApiMeta = { traceId: string; serverTime: string };
export type ApiEnvelope<T> = { data: T; meta: ApiMeta };
export type ApiErrorPayload = {
  code: string;
  message: string;
  details: unknown[];
  retryable: boolean;
  retryAfterSeconds: number | null;
};

export type CurrentUserDto = {
  userId: Id;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  role: "USER" | "ADMIN";
  curriculumId: Id | null;
  revision: number;
  joinedAt: string;
  updatedAt: string;
};

export type CsrfDto = { headerName: "X-CSRF-TOKEN"; token: string };

function record(value: unknown, label: string): RecordValue {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
}

function string(value: unknown, label: string): string {
  if (typeof value !== "string") throw new TypeError(`${label} must be a string`);
  return value;
}

function nullableString(value: unknown, label: string): string | null {
  return value === null ? null : string(value, label);
}

function integer(value: unknown, label: string): number {
  if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`);
  return value as number;
}

export function decodeEnvelope<T>(value: unknown, decodeData: (data: unknown) => T): ApiEnvelope<T> {
  const root = record(value, "response");
  if (root.success !== true || root.error !== null) throw new TypeError("Expected a successful API envelope");
  const meta = record(root.meta, "meta");
  return { data: decodeData(root.data), meta: { traceId: string(meta.traceId, "meta.traceId"), serverTime: string(meta.serverTime, "meta.serverTime") } };
}

export function decodeErrorPayload(value: unknown): ApiErrorPayload | null {
  try {
    const root = record(value, "response");
    if (root.success !== false || root.data !== null) return null;
    const error = record(root.error, "error");
    const details = Array.isArray(error.details) ? error.details : [];
    return {
      code: string(error.code, "error.code"), message: string(error.message, "error.message"), details,
      retryable: error.retryable === true,
      retryAfterSeconds: error.retryAfterSeconds === null ? null : integer(error.retryAfterSeconds, "error.retryAfterSeconds"),
    };
  } catch { return null; }
}

export function decodeCurrentUser(value: unknown): CurrentUserDto {
  const user = record(value, "current user");
  const role = string(user.role, "role");
  if (role !== "USER" && role !== "ADMIN") throw new TypeError("role is not supported");
  return {
    userId: readId(user.userId), email: string(user.email, "email"), nickname: string(user.nickname, "nickname"),
    profileImageUrl: nullableString(user.profileImageUrl, "profileImageUrl"), role,
    curriculumId: user.curriculumId === null ? null : readId(user.curriculumId), revision: integer(user.revision, "revision"),
    joinedAt: string(user.joinedAt, "joinedAt"), updatedAt: string(user.updatedAt, "updatedAt"),
  };
}

export function decodeCsrf(value: unknown): CsrfDto {
  const csrf = record(value, "csrf");
  if (csrf.headerName !== "X-CSRF-TOKEN") throw new TypeError("Unexpected CSRF header");
  return { headerName: "X-CSRF-TOKEN", token: string(csrf.token, "token") };
}
