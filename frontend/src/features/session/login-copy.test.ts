import { describe, expect, it } from "vitest";
import { loginFailureCopy } from "./login-copy";

describe("login failure copy", () => {
  it("explains an account allow-list rejection without exposing provider details", () => expect(loginFailureCopy("ACCOUNT_NOT_ALLOWED").title).toBe("사용할 수 없는 계정이에요"));
  it("uses a safe fallback for unknown or missing error codes", () => {
    expect(loginFailureCopy("SOMETHING_ELSE")).toEqual(loginFailureCopy(undefined));
    expect(loginFailureCopy(undefined).message).toContain("다시 시도");
  });
});
