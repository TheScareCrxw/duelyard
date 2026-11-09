import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { ApiError } from "@/services/apiClient";
import { getCurrentUser } from "@/services/userService";
import type { CurrentUser } from "@/types/user";
import { ACCESS_COOKIE } from "./session";
import { isTokenExpired } from "./tokens";

/**
 * Data access layer for auth. Pages and server actions call these instead of relying on the
 * proxy alone. The proxy only refreshes tokens and does optimistic redirects. The backend
 * verifies the token on every API call.
 */

/** The access token from the session cookie, if present and not expired. */
export const getAccessToken = cache(async (): Promise<string | null> => {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  return token && !isTokenExpired(token) ? token : null;
});

export type SessionUser =
  | { status: "signed-out" }
  | { status: "signed-in"; user: CurrentUser }
  /** Has a session, but the API couldn't be reached to load the profile. */
  | { status: "unavailable" };

/** Loads the signed-in user once per request. */
export const getSessionUser = cache(async (): Promise<SessionUser> => {
  const accessToken = await getAccessToken();
  if (!accessToken) return { status: "signed-out" };
  try {
    return { status: "signed-in", user: await getCurrentUser(accessToken) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "signed-out" };
    console.error("Failed to load current user", error);
    return { status: "unavailable" };
  }
});

/** For protected pages: the current user, or a redirect to sign-in. */
export async function requireUser(returnTo: string): Promise<CurrentUser> {
  const session = await getSessionUser();
  if (session.status === "signed-in") return session.user;
  if (session.status === "unavailable") {
    throw new Error("Your account couldn't be loaded. Please try again shortly.");
  }
  redirect(`/login?next=${encodeURIComponent(returnTo)}`);
}
