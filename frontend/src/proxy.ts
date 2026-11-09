import { NextResponse, type NextRequest } from "next/server";
import { isInvalidGrant, refreshSession, type TokenSet } from "@/lib/auth/cognito";
import { isAuthConfigured } from "@/lib/auth/config";
import { ACCESS_COOKIE, REFRESH_COOKIE, clearSession, writeSession } from "@/lib/auth/session";
import { isTokenExpired } from "@/lib/auth/tokens";

/**
 * Runs before every page request:
 *  1. Keeps the session alive: if the access token is missing or about to expire and a refresh
 *     token exists, gets new tokens from Cognito. They're set on the response for the browser and
 *     forwarded on the request so this same render already sees them.
 *  2. Optimistic redirects: signed-out users away from protected pages, signed-in users away
 *     from /login and /register.
 *
 * This is not the security boundary. Pages re-check via the DAL and the API verifies every token.
 */

const PROTECTED_PREFIXES = ["/dashboard", "/profile", "/play", "/settings"];
const GUEST_ONLY = ["/login", "/register"];
/** Refresh a little early so a token never expires mid-request. */
const REFRESH_SKEW_SECONDS = 60;

const matches = (pathname: string, prefixes: string[]) =>
  prefixes.some((p) => pathname === p || pathname.startsWith(`${p}/`));

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  let authenticated = !!accessToken && !isTokenExpired(accessToken, REFRESH_SKEW_SECONDS);
  let refreshed: TokenSet | null = null;
  let sessionInvalid = false;

  if (!authenticated && refreshToken && isAuthConfigured()) {
    try {
      refreshed = await refreshSession(refreshToken);
      authenticated = true;
    } catch (error) {
      // Revoked/expired refresh token: drop the session. Transient errors: keep it for next time.
      sessionInvalid = isInvalidGrant(error);
      if (!sessionInvalid) console.error("Session refresh failed", error);
    }
  }

  let response: NextResponse;
  if (!authenticated && matches(pathname, PROTECTED_PREFIXES)) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    response = NextResponse.redirect(login);
  } else if (authenticated && matches(pathname, GUEST_ONLY)) {
    response = NextResponse.redirect(new URL("/dashboard", request.url));
  } else {
    if (refreshed) {
      request.cookies.set(ACCESS_COOKIE, refreshed.accessToken);
    } else if (sessionInvalid) {
      request.cookies.delete(ACCESS_COOKIE);
      request.cookies.delete(REFRESH_COOKIE);
    }
    response = NextResponse.next({ request: { headers: new Headers(request.headers) } });
  }

  if (refreshed) writeSession(response.cookies, refreshed);
  else if (sessionInvalid) clearSession(response.cookies);

  return response;
}

export const config = {
  // Pages only: skip static assets, image optimization, metadata files and the OAuth routes.
  matcher: ["/((?!_next/static|_next/image|auth/|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)"],
};
