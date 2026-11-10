export type ProfileVisibility = "PUBLIC" | "PRIVATE";

/** Response of GET/PATCH /api/users/me: the signed-in user's own account. */
export interface CurrentUser {
  id: number;
  username: string;
  email: string;
  avatarUrl: string | null;
  profileVisibility: ProfileVisibility;
  createdAt: string;
  /** When the next rename is allowed; null if allowed now. */
  usernameChangeAvailableAt: string | null;
}

/** Body of PATCH /api/users/me. Omitted fields are unchanged; avatarUrl "" removes the avatar. */
export interface UpdateProfileRequest {
  username?: string;
  avatarUrl?: string;
  profileVisibility?: ProfileVisibility;
}

/** Response of GET /api/users/username-availability. */
export interface UsernameAvailability {
  username: string;
  available: boolean;
  reason: "INVALID_FORMAT" | "RESERVED" | "TAKEN" | null;
}
