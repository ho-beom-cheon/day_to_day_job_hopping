import { QueryClient,QueryClientProvider } from "@tanstack/react-query";
import { fireEvent,render,screen,waitFor } from "@testing-library/react";
import { describe,it,expect,vi,beforeEach } from "vitest";
import { EvaluationScreen } from "./evaluation-screen";
import { getEvaluation,updatePart,type Evaluation } from "./api";
vi.mock("./api",()=>({getEvaluation:vi.fn(),updatePart:vi.fn(),createEvaluation:vi.fn(),evaluationAction:vi.fn()}));
vi.mock("next/navigation",()=>({useRouter:()=>({push:vi.fn()})}));
const base=():Evaluation=>({id:"1",kind:"DAILY",key:"2026-09-27",title:"오늘 평가",objectiveCount:1,partCount:1,submittedPartCount:0,createdAt:"2026-09-27",objectives:[{id:"g1",subject:"SQL",description:"트랜잭션 경계를 설명한다",extra:true}],parts:[{id:"3",partNo:1,revision:2,status:"ACTIVE",serverNow:new Date().toISOString(),expiresAt:new Date(Date.now()+120000).toISOString(),answers:{q1:"저장된 답변"},plan:{assessmentSeconds:540,totalSeconds:1800,slots:[{id:"q1"}]},questions:[{id:"q1",type:"INTERVIEW",prompt:"동시 갱신이 있을 때 조건과 수정 근거를 설명해 주세요.",choices:[],objectiveIds:["g1"]}]}]});
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});render(<QueryClientProvider client={client}><EvaluationScreen id="1"/></QueryClientProvider>);}
beforeEach(()=>vi.clearAllMocks());
describe("completed learning evaluation",()=>{
  it("retains unsaved writing on save failure and never reveals answers before submission",async()=>{
    vi.mocked(getEvaluation).mockResolvedValue(base());vi.mocked(updatePart).mockRejectedValue(new Error("temporary"));mount();
    const answer=await screen.findByLabelText("1번 답변");fireEvent.change(answer,{target:{value:"실패해도 보존할 답변"}});fireEvent.click(screen.getByRole("button",{name:"답안 저장"}));
    await waitFor(()=>expect(updatePart).toHaveBeenCalledWith("1",1,"save",{q1:"실패해도 보존할 답변"},2));
    expect(answer).toHaveValue("실패해도 보존할 답변");expect(screen.queryByText("모범 답변")).not.toBeInTheDocument();
  });
  it("submits the current draft and shows the model answer while grading remains on hold",async()=>{
    const initial=base(),submitted=base();submitted.submittedPartCount=1;submitted.parts[0]={...submitted.parts[0],revision:3,status:"HOLD",answers:{q1:"수정한 답변"},gradingRequested:true,result:{status:"HOLD",score:null,results:[{questionId:"q1",status:"HOLD",answer:"수정한 답변",correctAnswer:"조건을 원자적으로 검사합니다.",explanation:"수정된 행 수를 확인해야 합니다.",holdReason:"채점 근거 확인 중"}]}};
    vi.mocked(getEvaluation).mockResolvedValue(initial);vi.mocked(updatePart).mockResolvedValue(submitted);mount();
    fireEvent.change(await screen.findByLabelText("1번 답변"),{target:{value:"수정한 답변"}});fireEvent.click(screen.getByRole("button",{name:"제출하고 정답·해설 보기"}));
    expect(await screen.findByText("조건을 원자적으로 검사합니다.")).toBeInTheDocument();expect(screen.getByLabelText("1번 답변")).toBeDisabled();expect(screen.queryByText(/이 회차 점수/)).not.toBeInTheDocument();
    expect(updatePart).toHaveBeenCalledWith("1",1,"submit",{q1:"수정한 답변"},2);
  });
  it("recovers expired sessions by submitting the persisted draft",async()=>{
    const expired=base();expired.parts[0].status="EXPIRED";expired.parts[0].expiresAt=new Date(Date.now()-1000).toISOString();
    const submitted=structuredClone(expired);submitted.parts[0].status="GRADED";submitted.parts[0].result={status:"GRADED",score:0,results:[]};
    vi.mocked(getEvaluation).mockResolvedValue(expired);vi.mocked(updatePart).mockResolvedValue(submitted);mount();
    await waitFor(()=>expect(updatePart).toHaveBeenCalledWith("1",1,"submit",{q1:"저장된 답변"},2));
    expect(await screen.findByText("이 회차 점수 0점")).toBeInTheDocument();
  });
  it("recalibrates the countdown when a cached ready part starts with a fresh server time",async()=>{
    const ready=base();ready.parts[0].status="READY";ready.parts[0].serverNow=new Date(Date.now()-300000).toISOString();ready.parts[0].expiresAt=undefined;ready.parts[0].questions=undefined;
    const active=base();vi.mocked(getEvaluation).mockResolvedValue(ready);vi.mocked(updatePart).mockResolvedValue(active);mount();
    fireEvent.click(await screen.findByRole("button",{name:"문제 풀이 시작"}));
    await waitFor(()=>expect(screen.getByText(/남은 시간 1분/)).toBeInTheDocument(),{timeout:2500});
  });
});
