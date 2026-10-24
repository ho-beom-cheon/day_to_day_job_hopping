import { QueryClient,QueryClientProvider } from "@tanstack/react-query";
import { fireEvent,render,screen,waitFor } from "@testing-library/react";
import { beforeEach,describe,expect,it,vi } from "vitest";
import { EvaluationSection } from "./evaluation-section";
import { createEvaluation,listEvaluations,type EvaluationList } from "./api";
vi.mock("./api",()=>({listEvaluations:vi.fn(),createEvaluation:vi.fn(),evaluationAction:vi.fn(()=>({kind:"DAILY"}))}));
vi.mock("next/navigation",()=>({useRouter:()=>({push:vi.fn()})}));
const listing=():EvaluationList=>({sessionMinutes:60,scopes:[{kind:"DAILY",key:"2026-09-27",title:"2026-09-27 일간 평가",objectiveCount:11,newObjectiveCount:11,sourceCount:5,extraSourceCount:5,available:true,replacement:"",sourceReady:true,sourceIssues:[],partCount:3}],evaluations:[]});
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});render(<QueryClientProvider client={client}><EvaluationSection/></QueryClientProvider>);}
beforeEach(()=>vi.clearAllMocks());
describe("assessment source readiness",()=>{
  it("allows the completed five sources and eleven goals to prepare immediately",async()=>{
    vi.mocked(listEvaluations).mockResolvedValue(listing());mount();
    fireEvent.click(await screen.findByRole("button",{name:"이 범위로 평가 준비"}));
    await waitFor(()=>expect(createEvaluation).toHaveBeenCalled());
    expect(screen.getByText("학습 목표 11개")).toBeInTheDocument();
  });
  it("identifies a source problem without telling the learner to study again, and refreshes after repair",async()=>{
    const blocked=listing();blocked.scopes[0].sourceReady=false;blocked.scopes[0].sourceIssues=[{sourceId:"v3",title:"SQL과 데이터 모델링",reasons:["검수 기록 연결 필요"]}];
    vi.mocked(listEvaluations).mockResolvedValueOnce(blocked).mockResolvedValue(listing());mount();
    expect(await screen.findByText(/SQL과 데이터 모델링 · 검수 기록 연결 필요/)).toBeInTheDocument();
    expect(screen.getByText(/추가로 문제를 풀거나 다시 학습할 필요는 없어요/)).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:"이 범위로 평가 준비"})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"상태 다시 확인"}));
    expect(await screen.findByRole("button",{name:"이 범위로 평가 준비"})).toBeEnabled();
  });
});
