import { NextResponse, type NextRequest } from "next/server";
import { exchangeAuthorizationCode } from "@/lib/auth/cognito";
import { safeEqual } from "@/lib/auth/crypto";
import { safeNextPath } from "@/lib/auth/redirect";
import { OAUTH_COOKIE, cookieOptions, writeSession } from "@/lib/auth/session";
import { getCurrentUser } from "@/services/userService";

interface OAuthState {
  state: string;
  verifier: string;
  next: string;
}

function parseState(raw: string | undefined): OAuthState | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<OAuthState>;
    return typeof value.state === "string" && typeof value.verifier === "string"
      ? { state: value.state, verifier: value.verifier, next: safeNextPath(value.next) }
      : null;
  } catch {
    return null;
  }
}

/** Completes the Google sign-in started by /auth/google. */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const saved = parseState(request.cookies.get(OAUTH_COOKIE)?.value);

  const fail = (reason: string) => {
    const response = NextResponse.redirect(new URL(`/login?error=${reason}`, request.url));
    response.cookies.set(OAUTH_COOKIE, "", cookieOptions(0));
    return response;
  };

  if (params.get("error")) return fail("oauth_denied");
  const code = params.get("code");
  const state = params.get("state");
  if (!saved || !code || !state || !safeEqual(state, saved.state)) return fail("oauth_state");

  let tokens;
  try {
    tokens = await exchangeAuthorizationCode(code, saved.verifier);
  } catch (error) {
    console.error("Authorization code exchange failed", error);
    return fail("oauth_failed");
  }

  // Create the application account now so the first page load is fast. Failure here is not
  // fatal: the dashboard provisions on demand.
  try {
    await getCurrentUser(tokens.accessToken);
  } catch (error) {
    console.error("Account provisioning after Google sign-in failed", error);
  }

  const response = NextResponse.redirect(new URL(saved.next, request.url));
  writeSession(response.cookies, tokens);
  response.cookies.set(OAUTH_COOKIE, "", cookieOptions(0));
  return response;
}
