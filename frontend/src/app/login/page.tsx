import { redirect } from "next/navigation";
import { LoginFailure } from "@/features/session/login-failure";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ error?: string | string[] }> }) {
  const { error } = await searchParams;
  const errorCode = (Array.isArray(error) ? error.find(value => value.trim()) : error)?.trim();
  if (!errorCode) redirect("/workspace");
  return <LoginFailure errorCode={errorCode} />;
}
