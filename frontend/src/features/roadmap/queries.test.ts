import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import { currentCurriculumQuery, roadmapMonthsQuery, roadmapWeeksQuery } from "./queries";

vi.mock("@/api", () => ({ api: { request: vi.fn() } }));
const request = vi.mocked(api.request);

describe("roadmap queries", () => {
  beforeEach(() => request.mockReset());
  it("preserves the current curriculum data and ETag transport shape", async () => {
    request.mockResolvedValueOnce({ data: { curriculumId: "3" }, etag: '"7"' });
    await expect(currentCurriculumQuery.queryFn!({} as never)).resolves.toEqual({ data: { curriculumId: "3" }, etag: '"7"' });
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ path: "/api/v1/curriculums/current" }));
  });
  it("unwraps month and week data after the client decoder boundary", async () => {
    request.mockResolvedValueOnce({ data: [{ monthNo: 1 }], etag: null }).mockResolvedValueOnce({ data: [{ weekNo: 1 }], etag: null });
    await expect(roadmapMonthsQuery("3").queryFn!({} as never)).resolves.toEqual([{ monthNo: 1 }]);
    await expect(roadmapWeeksQuery("3").queryFn!({} as never)).resolves.toEqual([{ weekNo: 1 }]);
  });
});
