import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExtraLearningScreen } from "./extra-learning-screen";
import { decodeExtraEntry, decodeExtraTopic, getExtraHistory, getExtraTopics, startExtra } from "./api";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("./api", async importOriginal => ({ ...await importOriginal<typeof import("./api")>(), getExtraHistory: vi.fn(), getExtraTopics: vi.fn(), startExtra: vi.fn() }));
vi.mock("./ai-lesson-api", () => ({ getLessonJobs: vi.fn(async () => []), createLesson: vi.fn() }));
const topic = { contentVersionId: "9223372036854775806", sourceSessionId: null, title: "JOIN 결과 이해", category: "SQL", problemCount: 10, wrongCount: 2 };
const entry = { sessionId: "9007199254740993", mode: "EXPLORE" as const, title: topic.title, category: "SQL", status: "IN_PROGRESS", courseStatus: "ACTIVE", targetMinutes: 15, recordedMinutes: 0, todayMinutes: 0, scheduledDate: "2026-09-26" };
function show() { return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ExtraLearningScreen/></QueryClientProvider>); }
beforeEach(() => { vi.clearAllMocks(); vi.mocked(getExtraHistory).mockResolvedValue([]); vi.mocked(getExtraTopics).mockResolvedValue([topic]); vi.mocked(startExtra).mockResolvedValue(entry); });
describe("extra learning", () => {
  it("keeps 64-bit ids intact and rejects malformed data", () => {
    expect(decodeExtraTopic(topic).contentVersionId).toBe(topic.contentVersionId);
    expect(decodeExtraEntry(entry).sessionId).toBe(entry.sessionId);
    expect(() => decodeExtraTopic({...topic,contentVersionId:9223372036854775806})).toThrow();
    expect(() => decodeExtraEntry({...entry,recordedMinutes:-1})).toThrow();
  });
  it("starts the selected field and time, with a bounded practice set", async () => {
    show();
    await screen.findByRole("option",{name:/JOIN 결과 이해/});
    fireEvent.change(screen.getByRole("combobox", { name: "공부할 주제" }), { target: { value: `${topic.contentVersionId}:` } });
    fireEvent.click(screen.getByRole("radio",{name:"90분"}));
    expect(screen.getByText(/확인 문제 10개/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:/추가 학습 시작/}));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/workspace/learning/9007199254740993"));
    expect(vi.mocked(startExtra).mock.calls[0][0].getPayload()).toEqual({mode:"EXPLORE",contentVersionId:topic.contentVersionId,sourceSessionId:null,targetMinutes:90});
  });
  it("lets a learner choose one of several topics in the same field without following day order", async () => {
    const other = { ...topic, contentVersionId: "9007199254740994", title: "NULL과 유일 제약", wrongCount: 0 };
    vi.mocked(getExtraTopics).mockResolvedValue([topic, other]);
    show();
    expect(await screen.findByText("선택 가능한 1개 분야 · 2개 주제")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "분야" }), { target: { value: "SQL" } });
    fireEvent.change(screen.getByRole("combobox", { name: "공부할 주제" }), { target: { value: `${other.contentVersionId}:` } });
    fireEvent.click(screen.getByRole("button", { name: /추가 학습 시작/ }));
    await waitFor(() => expect(startExtra).toHaveBeenCalledOnce());
    expect(vi.mocked(startExtra).mock.calls[0][0].getPayload().contentVersionId).toBe(other.contentVersionId);
  });
  it("discloses when only Java has published extra study material", async () => {
    vi.mocked(getExtraTopics).mockResolvedValue([{ ...topic, category: "JAVA", title: "참조와 값 비교" }]);
    show();
    expect(await screen.findByRole("status")).toHaveTextContent("현재 검수된 추가 학습 자료는 Java 분야만 제공해요");
    expect(screen.getByRole("option", { name: "Java · 1개" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "SQL" })).not.toBeInTheDocument();
  });
  it("offers other modes on rest days without inventing today's topics", async () => {
    vi.mocked(getExtraTopics).mockResolvedValue([]);show();
    fireEvent.click(screen.getByRole("radio", { name: /오늘 내용 더 익히기/ }));
    expect(await screen.findByText(/오늘 배정된 학습이 없어요/)).toBeInTheDocument();
    expect(screen.getByRole("radio",{name:/다른 분야 공부하기/})).toBeEnabled();
    expect(screen.queryByRole("button",{name:/추가 학습 시작/})).not.toBeInTheDocument();
  });
  it("restores unfinished work with its estimated duration", async () => {
    vi.mocked(getExtraHistory).mockResolvedValue([entry]);show();
    expect(await screen.findByText("추가 학습 1건 · 완료 0건")).toBeInTheDocument();
    expect(screen.getByRole("link",{name:/이어서 학습/})).toHaveAttribute("href","/workspace/learning/9007199254740993");
  });
  it("labels a previous course as history", async () => {
    vi.mocked(getExtraHistory).mockResolvedValue([{...entry,courseStatus:"CANCELED"}]);show();
    expect(await screen.findByText(/이전 과정/)).toBeInTheDocument();
    expect(screen.getByRole("link",{name:/이전 기록/})).toBeInTheDocument();
  });
  it("filters AI topics and recovers from an empty search without starting hidden topics", async () => {
    vi.mocked(getExtraTopics).mockResolvedValue([topic,{...topic,contentVersionId:"88",title:"MCP 신뢰 경계",category:"AI",stage:"심화",recommendedWeek:20,learningObjective:"도구 결과와 권한을 구분한다."}]);
    show();
    await screen.findByRole("option",{name:/AI 개발 활용 · 1개/});
    fireEvent.change(screen.getByRole("searchbox",{name:"주제 검색"}),{target:{value:"없는주제"}});
    expect(screen.queryByRole("button",{name:/추가 학습 시작/})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"검색·필터 초기화"}));
    fireEvent.change(screen.getByRole("combobox",{name:"학습 단계"}),{target:{value:"심화"}});
    expect(screen.getByRole("option",{name:/MCP 신뢰 경계/})).toBeInTheDocument();
    expect(screen.queryByRole("option",{name:/JOIN 결과 이해/})).not.toBeInTheDocument();
  });
});
