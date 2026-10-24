import { describe, expect, it } from "vitest";
import { decodeAiRun, decodeExamHistory, decodePracticeJob, decodePracticeQuestion, decodePracticeSet } from "./practice-contract";

const unanswered = { number: 1, prompt: "질문", choices: ["A", "B", "C", "D"],
  submittedAnswer: null, correct: null, correctAnswer: null, explanation: null };

describe("AI practice contract", () => {
  it("keeps the chosen difficulty with its server planned question count", () => {
    const value = { jobId: null, status: "NONE", questionCount: 8, latestQuestionCount: null,
      estimatedMinutes: 45, targetDifficulty: 5, difficultyLevels: [4, 4, 5, 5, 5, 5, 5, 5], qualityRejected: false,
      generationAvailable: true, availableDifficulties: [5], rejectedQuestionCount: 0 };
    expect(decodePracticeJob(value).targetDifficulty).toBe(5);
    expect(decodePracticeJob(value).questionCount).toBe(8);
    expect(decodePracticeJob(value).generationAvailable).toBe(true);
    expect(decodePracticeJob(value).availableDifficulties).toEqual([5]);
    expect(() => decodePracticeJob({ ...value, targetDifficulty: 2 })).toThrow();
    expect(() => decodePracticeJob({ ...value, availableDifficulties: [3, 3, 5] })).toThrow();
    expect(() => decodePracticeJob({ ...value, availableDifficulties: [] })).toThrow();
  });
  it("rejects feedback leaked before submission", () => {
    expect(() => decodePracticeQuestion({ ...unanswered, correctAnswer: 2 })).toThrow();
  });

  it("requires complete feedback after submission", () => {
    expect(() => decodePracticeQuestion({ ...unanswered, submittedAnswer: 1, correct: null })).toThrow();
  });

  it("accepts saved sets up to twenty questions and rejects invalid sizes", () => {
    expect(decodePracticeSet({ jobId: "1", contentId: "2", sessionId: "3",
      questions: Array.from({ length: 5 }, (_, index) => ({ ...unanswered, number: index + 1 })) }).questions).toHaveLength(5);
    expect(decodePracticeSet({ jobId: "1", contentId: "2", sessionId: "3",
      questions: Array.from({ length: 20 }, (_, index) => ({ ...unanswered, number: index + 1 })) }).questions).toHaveLength(20);
    expect(() => decodePracticeSet({ jobId: "1", contentId: "2", sessionId: "3", questions: [unanswered] })).toThrow();
    expect(() => decodePracticeSet({ jobId: "1", contentId: "2", sessionId: "3",
      questions: Array.from({ length: 21 }, (_, index) => ({ ...unanswered, number: index + 1 })) })).toThrow();
  });

  it("accepts a submitted exam answer only while feedback stays hidden", () => {
    const run = { runId: "4", jobId: "1", contentId: "2", sessionId: "3", mode: "EXAM",
      status: "IN_PROGRESS", answeredCount: 1, totalCount: 2, correctCount: null,
      questions: [{ ...unanswered, submittedAnswer: 1 }, { ...unanswered, number: 2 }] };
    expect(decodeAiRun(run).questions[0].correctAnswer).toBeNull();
    expect(() => decodeAiRun({ ...run, questions: [{ ...run.questions[0], correctAnswer: 2 }, run.questions[1]] })).toThrow();
    expect(() => decodeAiRun({ ...run, mode: "STUDY" })).toThrow();
  });

  it("rejects a score exposed in unfinished exam history", () => {
    const history = { runId: "4", jobId: "1", title: "개인 시험", status: "IN_PROGRESS",
      totalCount: 10, correctCount: null };
    expect(decodeExamHistory(history).correctCount).toBeNull();
    expect(() => decodeExamHistory({ ...history, correctCount: 1 })).toThrow();
  });
});
