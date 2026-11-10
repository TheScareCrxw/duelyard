import type { CurrentUser, UpdateProfileRequest, UsernameAvailability } from "@/types/user";
import { apiGet, apiPatch } from "./apiClient";

/** The caller's account. The backend creates it on the first call after sign-in. */
export function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return apiGet<CurrentUser>("/api/users/me", { accessToken });
}

export function updateCurrentUser(accessToken: string, changes: UpdateProfileRequest): Promise<CurrentUser> {
  return apiPatch<CurrentUser>("/api/users/me", changes, { accessToken });
}

export function checkUsernameAvailability(username: string): Promise<UsernameAvailability> {
  return apiGet<UsernameAvailability>(`/api/users/username-availability?username=${encodeURIComponent(username)}`);
}
