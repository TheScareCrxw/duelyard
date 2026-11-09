"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { parsePresetAvatar } from "@/lib/avatars";
import { getAccessToken } from "@/lib/auth/dal";
import { formString, validateUsername } from "@/lib/auth/validation";
import { ApiError } from "@/services/apiClient";
import { updateCurrentUser } from "@/services/userService";
import type { AuthFormState } from "@/types/auth";
import type { UpdateProfileRequest } from "@/types/user";

/*
 * Profile settings. Every change goes through PATCH /api/users/me, which only ever touches the
 * signed-in user (identified by their access token); the backend enforces all rules again.
 */

function describeApiError(error: unknown): string {
  if (!(error instanceof ApiError)) return "Something went wrong. Please try again.";
  switch (error.code) {
    case "USERNAME_TAKEN":
      return "That username is already taken.";
    case "USERNAME_RESERVED":
      return "That username is reserved.";
    case "USERNAME_INVALID":
      return "3–20 characters: letters, numbers and underscores only.";
    case "USERNAME_CHANGE_TOO_SOON": {
      const at = typeof error.details.availableAt === "string" ? new Date(error.details.availableAt) : null;
      return at
        ? `You can change your username again on ${at.toLocaleDateString("en-US", { month: "long", day: "numeric" })}.`
        : "You can change your username once every 30 days.";
    }
    case "AVATAR_INVALID":
      return "That avatar isn't available.";
    default:
      return error.status === 503 ? "The game server is unreachable. Please try again." : "Something went wrong. Please try again.";
  }
}

async function update(changes: UpdateProfileRequest, success: string, values?: Record<string, string>): Promise<AuthFormState> {
  const token = await getAccessToken();
  if (!token) redirect("/login?next=/settings");
  try {
    await updateCurrentUser(token, changes);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect("/login?next=/settings");
    return { error: describeApiError(error), values };
  }
  // Username and avatar appear in the header on every page.
  revalidatePath("/", "layout");
  return { message: success, values };
}

export async function updateUsernameAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  const username = formString(formData, "username");
  const invalid = validateUsername(username);
  if (invalid) return { fieldErrors: { username: invalid }, values: { username } };
  return update({ username }, "Username updated. Use it to sign in from now on.", { username });
}

export async function updateAvatarAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  const avatar = formString(formData, "avatar");
  if (avatar !== "" && !parsePresetAvatar(avatar)) return { error: "Pick one of the avatars shown." };
  return update({ avatarUrl: avatar }, "Avatar saved.");
}

export async function updateVisibilityAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  const visibility = formString(formData, "visibility");
  if (visibility !== "PUBLIC" && visibility !== "PRIVATE") return { error: "Choose public or private." };
  return update(
    { profileVisibility: visibility },
    visibility === "PRIVATE" ? "Your stats are now private." : "Your profile is now public.",
  );
}
