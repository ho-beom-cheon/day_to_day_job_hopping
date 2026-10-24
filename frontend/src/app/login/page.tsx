import { LoginFailure } from "@/features/session/login-failure";
import { LoginLanding } from "@/features/session/login-landing";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ error?: string | string[] }> }) {
  const { error } = await searchParams;
  const errorCode = (Array.isArray(error) ? error.find(value => value.trim()) : error)?.trim();
  return errorCode ? <LoginFailure errorCode={errorCode} /> : <LoginLanding />;
}
