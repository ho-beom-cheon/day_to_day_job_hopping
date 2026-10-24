import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import { getExams } from "./api";

vi.mock("@/api", () => ({ api: { request: vi.fn() } }));
describe("exam catalog pagination", () => {
  beforeEach(() => vi.clearAllMocks());
  it("includes final assessments beyond the first page", async () => {
    vi.mocked(api.request).mockResolvedValueOnce({ data: { content: [{ testId: "first" }], totalPages: 2, totalElements: 101 } } as never)
      .mockResolvedValueOnce({ data: { content: [{ testId: "final" }], totalPages: 2, totalElements: 101 } } as never);
    const result = await getExams();
    expect(result.content.map(exam => exam.testId)).toEqual(["first", "final"]);
    expect(api.request).toHaveBeenLastCalledWith(expect.objectContaining({ path: "/api/v1/tests?page=1&size=100" }));
  });
  it("reports a later page failure instead of presenting an incomplete catalog", async () => {
    vi.mocked(api.request).mockResolvedValueOnce({ data: { content: [], totalPages: 2 } } as never).mockRejectedValueOnce(new Error("offline"));
    await expect(getExams()).rejects.toThrow("offline");
  });
});
