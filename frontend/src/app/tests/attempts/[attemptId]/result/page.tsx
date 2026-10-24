import { ExamResultScreen } from "@/features/exams/exam-result-screen";
export default async function ExamResultPage({params}:{params:Promise<{attemptId:string}>}){const {attemptId}=await params;return <ExamResultScreen attemptId={attemptId}/>}
