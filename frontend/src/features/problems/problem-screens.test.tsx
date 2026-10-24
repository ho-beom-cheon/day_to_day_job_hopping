import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { PracticeScreen } from "./practice-screen";
import { ReviewDetailScreen } from "./review-detail-screen";
import { ReviewListScreen } from "./review-list-screen";
import { getProblem, getWrongAnswer, getWrongAnswers, saveWrongNote, submitAttempt } from "./api";
import { completeLearningContent, todayLearningKeys } from "@/features/today-learning/queries";

vi.mock("./api", () => ({
  getProblem: vi.fn(), submitAttempt: vi.fn(), getWrongAnswers: vi.fn(), getWrongAnswer: vi.fn(),
  saveWrongNote: vi.fn(), resolveWrong: vi.fn(), reopenWrong: vi.fn(),
}));
vi.mock("@/features/today-learning/queries", async importOriginal => ({
  ...await importOriginal<typeof import("@/features/today-learning/queries")>(),
  completeLearningContent: vi.fn(),
}));

const problem = {
  problemId:"11", contentId:"22", category:"JAVA", type:"MULTIPLE_CHOICE" as const, difficulty:"MEDIUM",
  question:"List 인터페이스의 특징은?", options:[{optionNo:1,text:"순서를 유지한다."},{optionNo:2,text:"키와 값으로 저장한다."}],
  source:"CURATED" as const, reviewStatus:"HUMAN_REVIEWED" as const, previousAttemptCount:0, answered:false,
};
const wrong = {
  wrongAnswerId:"33", problemId:"11", category:"JAVA", question:"List 인터페이스의 특징은?", wrongCount:1,
  status:"UNRESOLVED" as const, lastWrongAt:"2026-09-21T10:00:00+09:00", reviewCount:0, difficulty:"MEDIUM",
  myLastAnswer:"2", correctAnswer:"1", explanation:"List는 입력 순서를 유지합니다.", firstWrongAt:"2026-09-21T10:00:00+09:00",
  resolvedAt:null, resolutionType:null, note:"기존 메모", revision:1, etag:'"1"',
};

function wrapper({children}:{children:ReactNode}) {
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(()=>vi.clearAllMocks());

describe("problem practice screen",()=>{
  it("hides answer and explanation until submission, then reveals grading feedback",async()=>{
    vi.mocked(getProblem).mockResolvedValue(problem);
    vi.mocked(submitAttempt).mockResolvedValue({attemptId:"44",problemId:"11",contentId:"22",answer:"2",correct:false,correctAnswer:"1",explanation:"List는 입력 순서를 유지합니다.",attemptNo:1,wrongAnswerId:"33",wrongAnswerCreated:true,submittedAt:"2026-09-21T10:01:00+09:00"});
    render(<PracticeScreen problemId="11"/>,{wrapper});
    expect(await screen.findByText(problem.question)).toBeInTheDocument();
    expect(screen.queryByText("정답: 1")).not.toBeInTheDocument();
    expect(screen.queryByText("List는 입력 순서를 유지합니다.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", {name:"2. 키와 값으로 저장한다."}));
    fireEvent.click(screen.getByRole("button",{name:"답안 제출"}));
    expect(await screen.findByText("오답이에요")).toBeInTheDocument();
    expect(screen.getByText("정답: 1")).toBeInTheDocument();
    expect(screen.getByText("List는 입력 순서를 유지합니다.")).toBeInTheDocument();
    expect(submitAttempt).toHaveBeenCalledWith("11","2","22");
  });
  it("continues to the next unanswered problem and resets the answer on navigation",async()=>{
    const second={...problem,problemId:"12",question:"다음 확인 문제는?"};
    vi.mocked(getProblem).mockImplementation(async id=>id==="12"?second:problem);
    vi.mocked(submitAttempt).mockResolvedValue({attemptId:"44",problemId:"11",contentId:"22",answer:"1",correct:true,correctAnswer:"1",explanation:"해설",attemptNo:1,wrongAnswerId:null,wrongAnswerCreated:false,submittedAt:"2026-09-21T10:01:00+09:00"});
    const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity},mutations:{retry:false}}});
    client.setQueryData(todayLearningKeys.content("22"),{contentId:"22",contentVersionId:"23",sessionId:"30",title:"학습",type:"PRACTICE",sequence:1,required:true,status:"NOT_STARTED",completionRule:"ALL_PROBLEMS_ATTEMPTED",learningObjective:"목표",body:"본문",keyPoints:[],interviewPoints:[],problems:[{problemId:"11",title:"첫 문제",type:"MULTIPLE_CHOICE"},{problemId:"12",title:"다음 문제",type:"MULTIPLE_CHOICE"}],completedAt:null});
    const view=render(<QueryClientProvider client={client}><PracticeScreen problemId="11"/></QueryClientProvider>);
    expect(await screen.findByText(problem.question)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", {name:"1. 순서를 유지한다."}));
    fireEvent.click(screen.getByRole("button",{name:"답안 제출"}));
    const next=await screen.findByRole("link",{name:"다음 문제 풀기 →"});
    expect(next).toHaveAttribute("href","/practice/12?contentId=22");
    view.rerender(<QueryClientProvider client={client}><PracticeScreen problemId="12"/></QueryClientProvider>);
    expect(await screen.findByText(second.question)).toBeInTheDocument();
    expect(screen.getByRole("button",{name:"답안 제출"})).toBeDisabled();
    expect(screen.queryByText("정답이에요")).not.toBeInTheDocument();
  });
  it("completes a one-question content without returning to the learning page",async()=>{
    vi.mocked(getProblem).mockResolvedValue(problem);
    vi.mocked(submitAttempt).mockResolvedValue({attemptId:"44",problemId:"11",contentId:"22",answer:"1",correct:true,correctAnswer:"1",explanation:"해설",attemptNo:1,wrongAnswerId:null,wrongAnswerCreated:false,submittedAt:"2026-09-21T10:01:00+09:00"});
    vi.mocked(completeLearningContent).mockResolvedValue({contentId:"22",status:"COMPLETED"});
    const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity},mutations:{retry:false}}});
    client.setQueryData(todayLearningKeys.content("22"),{contentId:"22",contentVersionId:"23",sessionId:"30",title:"학습",type:"PRACTICE",sequence:1,required:true,status:"NOT_STARTED",completionRule:"ALL_PROBLEMS_ATTEMPTED",learningObjective:"목표",body:"본문",keyPoints:[],interviewPoints:[],problems:[{problemId:"11",title:"첫 문제",type:"MULTIPLE_CHOICE"}],completedAt:null});
    render(<QueryClientProvider client={client}><PracticeScreen problemId="11"/></QueryClientProvider>);
    expect(await screen.findByText(problem.question)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", {name:"1. 순서를 유지한다."}));
    fireEvent.click(screen.getByRole("button",{name:"답안 제출"}));
    fireEvent.click(await screen.findByRole("button",{name:"확인 문제 완료하기"}));
    expect(await screen.findByText("콘텐츠 완료가 저장됐어요.")).toBeInTheDocument();
    expect(completeLearningContent).toHaveBeenCalledWith("22");
    expect(screen.getByRole("link",{name:"학습 세션 마무리하기 →"})).toHaveAttribute("href","/workspace/learning/30");
  });
});

describe("wrong answer list",()=>{
  it("requests the selected status filter",async()=>{
    vi.mocked(getWrongAnswers).mockResolvedValue({content:[],page:0,size:20,totalElements:0,totalPages:0});
    render(<ReviewListScreen/>,{wrapper});
    await waitFor(()=>expect(getWrongAnswers).toHaveBeenCalledWith(undefined));
    fireEvent.click(screen.getByRole("button",{name:"미해결"}));
    await waitFor(()=>expect(getWrongAnswers).toHaveBeenCalledWith("UNRESOLVED"));
    fireEvent.click(screen.getByRole("button",{name:"해결 완료"}));
    await waitFor(()=>expect(getWrongAnswers).toHaveBeenCalledWith("RESOLVED"));
  });
});

describe("wrong answer conflict recovery",()=>{
  it("resets a stale note mutation and refetches the latest detail",async()=>{
    vi.mocked(getWrongAnswer).mockResolvedValue(wrong);
    vi.mocked(saveWrongNote).mockRejectedValue(new ApiError(412,{success:false,data:null,error:{code:"PRECONDITION_FAILED",message:"오답이 변경되었습니다.",details:[],retryable:false,retryAfterSeconds:null},meta:{traceId:"trace",serverTime:"2026-09-21T10:00:00+09:00"}},"trace"));
    render(<ReviewDetailScreen wrongAnswerId="33"/>,{wrapper});
    expect(await screen.findByDisplayValue("기존 메모")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"메모 저장"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("정보가 변경되었어요");
    fireEvent.click(screen.getByRole("button",{name:"최신 정보 불러오기"}));
    await waitFor(()=>expect(getWrongAnswer).toHaveBeenCalledTimes(2));
    await waitFor(()=>expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(saveWrongNote).toHaveBeenCalledTimes(1);
    expect(saveWrongNote).toHaveBeenCalledWith("33","기존 메모",'"1"');
  });
});
