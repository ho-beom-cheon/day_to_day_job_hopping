import type { CurrentUserDto } from "@/api/contract";

export type SessionViewModel = {
  displayName: string;
  email: string;
  profileImageUrl: string | null;
  initials: string;
};

export function toSessionViewModel(user: CurrentUserDto): SessionViewModel {
  const displayName = user.nickname.trim() || user.email.split("@")[0];
  return { displayName, email: user.email, profileImageUrl: user.profileImageUrl, initials: displayName.slice(0, 1).toUpperCase() };
}
