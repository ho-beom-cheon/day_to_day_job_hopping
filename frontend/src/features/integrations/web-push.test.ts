import { describe, expect, it } from "vitest";
import { applicationServerKey } from "./web-push";

describe("web push browser helpers", () => {
  it("decodes an unpadded Base64url application server key", () => {
    expect(Array.from(applicationServerKey("AQID-_8"))).toEqual([1, 2, 3, 251, 255]);
  });
});
