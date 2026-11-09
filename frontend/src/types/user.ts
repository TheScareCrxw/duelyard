export type ProfileVisibility = "PUBLIC" | "PRIVATE";

/** Response of GET /api/users/me: the signed-in user's own account. */
export interface CurrentUser {
  id: number;
  username: string;
  email: string;
  avatarUrl: string | null;
  profileVisibility: ProfileVisibility;
  createdAt: string;
}

/** Response of GET /api/users/username-availability. */
export interface UsernameAvailability {
  username: string;
  available: boolean;
  reason: "INVALID_FORMAT" | "TAKEN" | null;
}
