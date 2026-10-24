import { ExamAttemptScreen } from "@/features/exams/exam-attempt-screen";
export default async function ExamAttemptPage({params}:{params:Promise<{attemptId:string}>}){const {attemptId}=await params;return <ExamAttemptScreen attemptId={attemptId}/>}
