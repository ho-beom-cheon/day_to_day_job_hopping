import { EvaluationScreen } from "@/features/study-evaluations/evaluation-screen";
export default async function StudyEvaluationPage({params}:{params:Promise<{id:string}>}){const {id}=await params;return <EvaluationScreen id={id}/>;}
