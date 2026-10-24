export type LoginFailureCopy = { title: string; message: string };

export function loginFailureCopy(code?: string): LoginFailureCopy {
  if (code === "ACCOUNT_NOT_ALLOWED") return {
    title: "사용할 수 없는 계정이에요",
    message: "허용된 Google 계정인지 확인한 뒤 다시 시도해 주세요.",
  };
  return {
    title: "로그인을 완료하지 못했어요",
    message: "Google 로그인 창을 닫았거나 인증 정보가 만료되었을 수 있어요. 다시 시도해 주세요.",
  };
}
