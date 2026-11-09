import "server-only";
import { cookies } from "next/headers";
import { cookieOptions, SIGNUP_COOKIE } from "./session";

/**
 * Remembers an in-progress sign-up on this device (HttpOnly, 1 day) so that:
 *  - the verify page knows which Cognito account to confirm, and
 *  - the first sign-in works with the chosen username, before the backend has claimed it as a
 *    Cognito alias (until then Cognito only recognises the email).
 */
export interface PendingSignup {
  /** Opaque Cognito username. */
  id: string;
  username: string;
  email: string;
}

const MAX_AGE = 60 * 60 * 24;

export async function savePendingSignup(signup: PendingSignup): Promise<void> {
  (await cookies()).set(SIGNUP_COOKIE, JSON.stringify(signup), cookieOptions(MAX_AGE));
}

export async function readPendingSignup(): Promise<PendingSignup | null> {
  const raw = (await cookies()).get(SIGNUP_COOKIE)?.value;
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<PendingSignup>;
    return typeof value.id === "string" && typeof value.username === "string" && typeof value.email === "string"
      ? { id: value.id, username: value.username, email: value.email }
      : null;
  } catch {
    return null;
  }
}

/** Only callable from server actions / route handlers. */
export async function clearPendingSignup(): Promise<void> {
  (await cookies()).set(SIGNUP_COOKIE, "", cookieOptions(0));
}
