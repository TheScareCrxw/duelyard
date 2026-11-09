import type { CurrentUser, UsernameAvailability } from "@/types/user";
import { apiGet } from "./apiClient";

/** The caller's account. The backend creates it on the first call after sign-in. */
export function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return apiGet<CurrentUser>("/api/users/me", { accessToken });
}

export function checkUsernameAvailability(username: string): Promise<UsernameAvailability> {
  return apiGet<UsernameAvailability>(`/api/users/username-availability?username=${encodeURIComponent(username)}`);
}
