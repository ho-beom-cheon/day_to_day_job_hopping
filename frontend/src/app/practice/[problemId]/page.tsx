import { PracticeScreen } from "@/features/problems/practice-screen";
export default async function PracticePage({params,searchParams}:{params:Promise<{problemId:string}>;searchParams:Promise<{contentId?:string}>}) {
  const {problemId}=await params; const {contentId}=await searchParams;
  return <PracticeScreen problemId={problemId} contentId={contentId}/>;
}
