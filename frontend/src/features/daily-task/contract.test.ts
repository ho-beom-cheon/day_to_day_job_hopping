import { describe, expect, it } from "vitest";
import { decodeTask, decodeToday, failureMessage } from "./contract";

const competency = { code: "A1", title: "트랜잭션 경계와 전파", areaTitle: "트랜잭션·정합성", priority: "P1" };
const baseTask = {
  taskId: "9223372036854775807", status: "FEEDBACK", format: "CASE", timeMode: "STANDARD", gradingMode: "AI", followupLimit: 2,
  competency, planReason: "A1 · 우선순위 P1", failureCode: null,
  task: { title: "이체 재시도", situation: "상황", material: "로그", question: "원인은?", estimatedMinutes: 20, modelAnswerPoints: ["프록시"] },
  sheet: { version: 1, reviewLabel: "Claude · 사용자 미검수", markdown: "## 판단 기준" },
  turns: [
    { turnNo: 1, kind: "MAIN", question: "원인은?", answer: "self-invocation", submitted: true, pending: false, feedback: "좋아요", checkPoints: null, checkPointsMet: null, scoreDelta: null },
    { turnNo: 2, kind: "FOLLOWUP", question: "검증은요?", answer: null, submitted: false, pending: false, feedback: null, checkPoints: null, checkPointsMet: null, scoreDelta: null },
  ],
  rubric: [{ criterionId: "R1", description: "원인", points: 100, earnedPoints: 60, reason: "a" }],
  result: { status: "GRADED", baseScore: 60, finalScore: 60, feedback: "f", missingPoints: ["검증"], improvedAnswer: "i", holdReason: "", scoreExcluded: false },
  cardDraft: { title: "t", summary: "s", interviewLine: "l", reviewPrompt: "q" }, card: null, usefulness: null, pending: false, revision: 3,
};

describe("daily task contract", () => {
  it("keeps large ids as strings and hides follow-up check points until answered", () => {
    const task = decodeTask(baseTask);
    expect(task.taskId).toBe("9223372036854775807");
    expect(task.turns[1].checkPoints).toBeNull();
    expect(task.result?.finalScore).toBe(60);
    expect(task.task?.modelAnswerPoints).toEqual(["프록시"]);
  });

  it("accepts a preparing task without material and rejects unknown states", () => {
    const preparing = decodeTask({ ...baseTask, status: "PREPARING", task: null, sheet: null, turns: [], rubric: null, result: null, cardDraft: null });
    expect(preparing.task).toBeNull();
    expect(() => decodeTask({ ...baseTask, status: "ARCHIVED" })).toThrow();
  });

  it("decodes today with an empty slot and budget numbers", () => {
    const today = decodeToday({ task: null, week: { target: 4, completed: 1 }, ai: { enabled: true, limitKrw: 15000, remainingKrw: 14990.5 }, recentCards: [] });
    expect(today.task).toBeNull();
    expect(today.ai.remainingKrw).toBe(14990.5);
  });

  it("explains failure codes in Korean", () => {
    expect(failureMessage("AI_BUDGET_EXCEEDED")).toContain("자가 채점");
    expect(failureMessage(null)).toBeNull();
    expect(failureMessage("SOMETHING_NEW")).toContain("SOMETHING_NEW");
  });
});
