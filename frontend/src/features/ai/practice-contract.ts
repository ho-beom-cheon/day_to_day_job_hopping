import { readId, type Id } from "@/types/id";

type RecordValue = Record<string, unknown>;
const object = (value: unknown, label: string): RecordValue => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
};
const text = (value: unknown, label: string): string => {
  if (typeof value !== "string") throw new TypeError(`${label} must be text`);
  return value;
};
const integer = (value: unknown, label: string): number => {
  if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`);
  return value as number;
};

export type PracticeJobStatus = "NONE" | "READY" | "PROCESSING" | "SUCCESS" | "FAILED" | "UNKNOWN";
export type PracticeJob = { jobId: Id | null; status: PracticeJobStatus; questionCount: number;
  latestQuestionCount: number | null; estimatedMinutes: number; targetDifficulty: number; difficultyLevels: number[];
  qualityRejected: boolean; generationAvailable: boolean; availableDifficulties: number[]; rejectedQuestionCount: number };
export type AiPracticeQuestion = {
  number: number;
  difficulty?: number;
  prompt: string;
  choices: string[];
  submittedAnswer: number | null;
  correct: boolean | null;
  correctAnswer: number | null;
  explanation: string | null;
  sourceExcerpt?: string | null;
  reported?: boolean;
};
export type AiPracticeSet = { jobId: Id; contentId: Id; sessionId: Id; questions: AiPracticeQuestion[] };
export type SavedAiSet = { jobId: Id; contentId: Id; title: string; questionCount: number; generationSequence: number };
export type AiRunMode = "STUDY" | "EXAM" | "REVIEW";
export type AiRunQuestion = AiPracticeQuestion;
export type AiPracticeRun = {
  runId: Id; jobId: Id; contentId: Id; sessionId: Id; mode: AiRunMode;
  status: "IN_PROGRESS" | "COMPLETED"; answeredCount: number; totalCount: number;
  correctCount: number | null; questions: AiRunQuestion[];
  scoredCount: number;
};
export type AiWrongSummary = { jobId: Id; questionNo: number; prompt: string; status: "OPEN" | "RESOLVED";
  wrongCount: number; lastWrongAt: string };
export type AiWrongDetail = AiWrongSummary & { choices: string[]; correctAnswer: number; explanation: string; note: string };
export type AiExamHistory = { runId: Id; jobId: Id; title: string; status: "IN_PROGRESS" | "COMPLETED";
  totalCount: number; correctCount: number | null };

export function decodePracticeJob(value: unknown): PracticeJob {
  const data = object(value, "practice job");
  const status = text(data.status, "status");
  if (!["NONE", "READY", "PROCESSING", "SUCCESS", "FAILED", "UNKNOWN"].includes(status))
    throw new TypeError("Unsupported practice job status");
  if (!Array.isArray(data.difficultyLevels) || data.difficultyLevels.length !== data.questionCount)
    throw new TypeError("Invalid planned difficulty levels");
  if (![3, 4, 5].includes(integer(data.targetDifficulty, "targetDifficulty")))
    throw new TypeError("Invalid target difficulty");
  const targetDifficulty = data.targetDifficulty as number;
  const availableDifficulties = data.availableDifficulties === undefined
    ? (data.generationAvailable === true ? [targetDifficulty] : [])
    : data.availableDifficulties;
  if (!Array.isArray(availableDifficulties) || availableDifficulties.some((level, index) =>
    ![3, 4, 5].includes(level) || (index > 0 && level <= availableDifficulties[index - 1])) ||
    availableDifficulties.includes(targetDifficulty) !== (data.generationAvailable === true))
    throw new TypeError("Invalid available difficulty levels");
  return { jobId: data.jobId === undefined || data.jobId === null ? null : readId(data.jobId),
    status: status as PracticeJobStatus, questionCount: integer(data.questionCount, "questionCount"),
    latestQuestionCount: data.latestQuestionCount === null ? null : integer(data.latestQuestionCount, "latestQuestionCount"),
    estimatedMinutes: integer(data.estimatedMinutes, "estimatedMinutes"),
    targetDifficulty,
    difficultyLevels: data.difficultyLevels.map((level, index) => integer(level, `difficulty ${index}`)),
    qualityRejected: data.qualityRejected === true,
    generationAvailable: data.generationAvailable === true,
    availableDifficulties,
    rejectedQuestionCount: integer(data.rejectedQuestionCount ?? 0, "rejectedQuestionCount") };
}

export function decodePracticeQuestion(value: unknown): AiPracticeQuestion {
  const data = object(value, "practice question");
  if (!Array.isArray(data.choices) || data.choices.length !== 4) throw new TypeError("choices must have four items");
  const submittedAnswer = data.submittedAnswer === null ? null : integer(data.submittedAnswer, "submittedAnswer");
  if (submittedAnswer !== null && (submittedAnswer < 1 || submittedAnswer > 4 || typeof data.correct !== "boolean" ||
    !Number.isInteger(data.correctAnswer) || (data.correctAnswer as number) < 1 || (data.correctAnswer as number) > 4 ||
    typeof data.explanation !== "string")) throw new TypeError("Submitted question feedback is invalid");
  if (submittedAnswer === null && (data.correct !== null || data.correctAnswer !== null || data.explanation !== null))
    throw new TypeError("Unanswered question exposed feedback");
  return {
    number: integer(data.number, "number"), prompt: text(data.prompt, "prompt"),
    ...(data.difficulty === undefined ? {} : { difficulty: integer(data.difficulty, "difficulty") }),
    choices: data.choices.map((choice, index) => text(choice, `choice ${index + 1}`)), submittedAnswer,
    correct: data.correct === null ? null : data.correct as boolean,
    correctAnswer: data.correctAnswer === null ? null : integer(data.correctAnswer, "correctAnswer"),
    explanation: data.explanation === null ? null : text(data.explanation, "explanation"),
    sourceExcerpt: data.sourceExcerpt == null ? null : text(data.sourceExcerpt, "sourceExcerpt"),
    reported: data.reported === true,
  };
}

export function decodePracticeSet(value: unknown): AiPracticeSet {
  const data = object(value, "practice set");
  if (!Array.isArray(data.questions) || data.questions.length < 2 || data.questions.length > 20)
    throw new TypeError("practice set must have two to twenty questions");
  return {
    jobId: readId(data.jobId), contentId: readId(data.contentId), sessionId: readId(data.sessionId),
    questions: data.questions.map(decodePracticeQuestion),
  };
}

export function decodeSavedSet(value: unknown): SavedAiSet {
  const data = object(value, "saved AI set");
  return { jobId: readId(data.jobId), contentId: readId(data.contentId), title: text(data.title, "title"),
    questionCount: integer(data.questionCount, "questionCount"),
    generationSequence: integer(data.generationSequence, "generationSequence") };
}

export function decodeAiRun(value: unknown): AiPracticeRun {
  const data = object(value, "AI run");
  const mode = text(data.mode, "mode");
  const status = text(data.status, "status");
  if (!["STUDY", "EXAM", "REVIEW"].includes(mode) || !["IN_PROGRESS", "COMPLETED"].includes(status))
    throw new TypeError("Invalid AI run mode or status");
  if (!Array.isArray(data.questions) || data.questions.length < 1 || data.questions.length > 20)
    throw new TypeError("Invalid AI run questions");
  const questions = data.questions.map(item => {
    const question = object(item, "AI run question");
    const submittedAnswer = question.submittedAnswer === null ? null : integer(question.submittedAnswer, "submittedAnswer");
    const hidden = question.correct === null && question.correctAnswer === null && question.explanation === null;
    if (submittedAnswer === null && !hidden) throw new TypeError("Unanswered AI run exposed feedback");
    if (submittedAnswer !== null && !hidden) return decodePracticeQuestion(item);
    if (submittedAnswer !== null && (mode !== "EXAM" || status !== "IN_PROGRESS"))
      throw new TypeError("Submitted AI run omitted feedback");
    if (!Array.isArray(question.choices) || question.choices.length !== 4) throw new TypeError("Invalid choices");
    return { number: integer(question.number, "number"), prompt: text(question.prompt, "prompt"),
      ...(question.difficulty === undefined ? {} : { difficulty: integer(question.difficulty, "difficulty") }),
      choices: question.choices.map((choice, index) => text(choice, `choice ${index + 1}`)),
      submittedAnswer, correct: null, correctAnswer: null, explanation: null,
      sourceExcerpt: null, reported: question.reported === true };
  });
  return { runId: readId(data.runId), jobId: readId(data.jobId), contentId: readId(data.contentId),
    sessionId: readId(data.sessionId), mode: mode as AiRunMode,
    status: status as AiPracticeRun["status"], answeredCount: integer(data.answeredCount, "answeredCount"),
    totalCount: integer(data.totalCount, "totalCount"),
    correctCount: data.correctCount === null ? null : integer(data.correctCount, "correctCount"),
    scoredCount: integer(data.scoredCount ?? data.totalCount, "scoredCount"), questions };
}

export function decodeWrongSummary(value: unknown): AiWrongSummary {
  const data = object(value, "AI wrong note");
  const status = text(data.status, "status");
  if (status !== "OPEN" && status !== "RESOLVED") throw new TypeError("Invalid AI wrong status");
  return { jobId: readId(data.jobId), questionNo: integer(data.questionNo, "questionNo"),
    prompt: text(data.prompt, "prompt"), status, wrongCount: integer(data.wrongCount, "wrongCount"),
    lastWrongAt: text(data.lastWrongAt, "lastWrongAt") };
}

export function decodeWrongDetail(value: unknown): AiWrongDetail {
  const data = object(value, "AI wrong detail");
  if (!Array.isArray(data.choices) || data.choices.length !== 4) throw new TypeError("Invalid AI wrong choices");
  return { ...decodeWrongSummary(value), choices: data.choices.map((choice, index) => text(choice, `choice ${index + 1}`)),
    correctAnswer: integer(data.correctAnswer, "correctAnswer"), explanation: text(data.explanation, "explanation"),
    note: text(data.note, "note") };
}

export function decodeExamHistory(value: unknown): AiExamHistory {
  const data = object(value, "AI exam history");
  const status = text(data.status, "status");
  if (status !== "IN_PROGRESS" && status !== "COMPLETED") throw new TypeError("Invalid AI exam status");
  if (status === "IN_PROGRESS" && data.correctCount !== null) throw new TypeError("Incomplete exam exposed score");
  if (status === "COMPLETED" && !Number.isInteger(data.correctCount)) throw new TypeError("Completed exam omitted score");
  return { runId: readId(data.runId), jobId: readId(data.jobId), title: text(data.title, "title"),
    status, totalCount: integer(data.totalCount, "totalCount"),
    correctCount: data.correctCount === null ? null : integer(data.correctCount, "correctCount") };
}
