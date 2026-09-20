import { describe, expect, it } from "vitest";
import { decodeCurrentUser, decodeEnvelope, decodeErrorPayload } from "./contract";

describe("OpenAPI envelope decoders", () => {
  it("decodes a current user without coercing string IDs", () => {
    const decoded = decodeEnvelope({success:true,data:{userId:"9223372036854775807",email:"user@example.com",nickname:"학습자",profileImageUrl:null,role:"USER",curriculumId:"7",revision:1,joinedAt:"2026-09-18T19:00:00+09:00",updatedAt:"2026-09-18T19:00:00+09:00"},error:null,meta:{traceId:"0123456789abcdef0123456789abcdef",serverTime:"2026-09-18T22:00:00+09:00"}},decodeCurrentUser);
    expect(decoded.data.userId).toBe("9223372036854775807");
    expect(decoded.data.curriculumId).toBe("7");
  });
  it("rejects malformed success envelopes", () => expect(() => decodeEnvelope({success:true,data:{},error:{},meta:{}}, value => value)).toThrow());
  it("preserves canonical error detail", () => expect(decodeErrorPayload({success:false,data:null,error:{code:"AUTH_REQUIRED",message:"로그인이 필요합니다.",details:[],retryable:false,retryAfterSeconds:null},meta:{}})).toMatchObject({code:"AUTH_REQUIRED",retryable:false}));
});
