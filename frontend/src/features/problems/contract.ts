import { readId, type Id } from "@/types/id";

type Value = Record<string, unknown>;
const object = (value: unknown, label: string): Value => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as Value;
};
const text = (value: unknown, label: string): string => {
  if (typeof value !== "string") throw new TypeError(`${label} must be a string`);
  return value;
};
const integer = (value: unknown, label: string): number => {
  if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`);
  return value as number;
};
const boolean = (value: unknown, label: string): boolean => {
  if (typeof value !== "boolean") throw new TypeError(`${label} must be a boolean`);
  return value;
};

export type ProblemDto = {
  problemId: Id; contentId: Id | null; category: string; type: "MULTIPLE_CHOICE" | "TRUE_FALSE";
  difficulty: string; question: string; options: { optionNo: number; text: string }[];
  source: "CURATED" | "AI_GENERATED"; reviewStatus: "HUMAN_REVIEWED" | "UNREVIEWED";
  previousAttemptCount: number; answered: boolean;
};
export type AttemptDto = {
  attemptId: Id; problemId: Id; contentId: Id | null; answer: string; correct: boolean;
  correctAnswer: string; explanation: string; attemptNo: number; wrongAnswerId: Id | null;
  wrongAnswerCreated: boolean; submittedAt: string;
};
export type WrongSummaryDto = {
  wrongAnswerId: Id; problemId: Id; category: string; question: string; wrongCount: number;
  status: "UNRESOLVED" | "RESOLVED"; lastWrongAt: string; reviewCount: number;
};
export type WrongAnswerDto = WrongSummaryDto & {
  difficulty: string; myLastAnswer: string | null; correctAnswer: string; explanation: string;
  firstWrongAt: string; resolvedAt: string | null; resolutionType: "MANUAL" | "CORRECT_RETRY" | null;
  note: string; revision: number;
};
export type WrongPageDto = { content: WrongSummaryDto[]; page: number; size: number; totalElements: number; totalPages: number };

export function decodeProblem(value: unknown): ProblemDto {
  const v = object(value, "problem");
  const type = text(v.type, "type");
  if (type !== "MULTIPLE_CHOICE" && type !== "TRUE_FALSE") throw new TypeError("Unsupported problem type");
  const source = text(v.source, "source");
  if (source !== "CURATED" && source !== "AI_GENERATED") throw new TypeError("Unsupported source");
  const reviewStatus = text(v.reviewStatus, "reviewStatus");
  if (reviewStatus !== "HUMAN_REVIEWED" && reviewStatus !== "UNREVIEWED") throw new TypeError("Unsupported review status");
  if (!Array.isArray(v.options)) throw new TypeError("options must be an array");
  return { problemId: readId(v.problemId), contentId: v.contentId === null ? null : readId(v.contentId), category: text(v.category,"category"), type,
    difficulty: text(v.difficulty,"difficulty"), question: text(v.question,"question"), options: v.options.map((raw) => { const o=object(raw,"option"); return {optionNo:integer(o.optionNo,"optionNo"),text:text(o.text,"option text")}; }),
    source, reviewStatus, previousAttemptCount: integer(v.previousAttemptCount,"previousAttemptCount"), answered: boolean(v.answered,"answered") };
}

export function decodeAttempt(value: unknown): AttemptDto {
  const v=object(value,"attempt");
  return { attemptId:readId(v.attemptId), problemId:readId(v.problemId), contentId:v.contentId===null?null:readId(v.contentId), answer:text(v.answer,"answer"),
    correct:boolean(v.correct,"correct"), correctAnswer:text(v.correctAnswer,"correctAnswer"), explanation:text(v.explanation,"explanation"), attemptNo:integer(v.attemptNo,"attemptNo"),
    wrongAnswerId:v.wrongAnswerId===null?null:readId(v.wrongAnswerId), wrongAnswerCreated:boolean(v.wrongAnswerCreated,"wrongAnswerCreated"), submittedAt:text(v.submittedAt,"submittedAt") };
}

function decodeWrongSummary(value: unknown): WrongSummaryDto {
  const v=object(value,"wrong answer summary"); const status=text(v.status,"status");
  if(status!=="UNRESOLVED"&&status!=="RESOLVED") throw new TypeError("Unsupported wrong answer status");
  return {wrongAnswerId:readId(v.wrongAnswerId),problemId:readId(v.problemId),category:text(v.category,"category"),question:text(v.question,"question"),wrongCount:integer(v.wrongCount,"wrongCount"),status,lastWrongAt:text(v.lastWrongAt,"lastWrongAt"),reviewCount:integer(v.reviewCount,"reviewCount")};
}
export function decodeWrongPage(value: unknown): WrongPageDto {
  const v=object(value,"wrong answer page"); if(!Array.isArray(v.content)) throw new TypeError("content must be an array");
  return {content:v.content.map(decodeWrongSummary),page:integer(v.page,"page"),size:integer(v.size,"size"),totalElements:integer(v.totalElements,"totalElements"),totalPages:integer(v.totalPages,"totalPages")};
}
export function decodeWrongAnswer(value: unknown): WrongAnswerDto {
  const v=object(value,"wrong answer"), summary=decodeWrongSummary(v); const resolution=v.resolutionType===null?null:text(v.resolutionType,"resolutionType");
  if(resolution!==null&&resolution!=="MANUAL"&&resolution!=="CORRECT_RETRY") throw new TypeError("Unsupported resolution type");
  return {...summary,difficulty:text(v.difficulty,"difficulty"),myLastAnswer:v.myLastAnswer===null?null:text(v.myLastAnswer,"myLastAnswer"),correctAnswer:text(v.correctAnswer,"correctAnswer"),explanation:text(v.explanation,"explanation"),firstWrongAt:text(v.firstWrongAt,"firstWrongAt"),resolvedAt:v.resolvedAt===null?null:text(v.resolvedAt,"resolvedAt"),resolutionType:resolution,note:text(v.note,"note"),revision:integer(v.revision,"revision")};
}
