import { ReviewDetailScreen } from "@/features/problems/review-detail-screen";
export default async function ReviewDetailPage({params}:{params:Promise<{wrongAnswerId:string}>}){const {wrongAnswerId}=await params;return <ReviewDetailScreen wrongAnswerId={wrongAnswerId}/>}
