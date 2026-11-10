import type { TokenSet } from "./cognito";

/**
 * Session cookies. Tokens live only in HttpOnly cookies set by the Next.js server; browser
 * JavaScript can never read them.
 *
 * In production cookie names use the `__Host-` prefix, which browsers only accept with Secure,
 * Path=/ and no Domain: the cookie can't be set by a subdomain or sent over plain HTTP.
 */
const isProduction = process.env.NODE_ENV === "production";
const prefix = isProduction ? "__Host-" : "";

export const ACCESS_COOKIE = `${prefix}dy_at`;
export const REFRESH_COOKIE = `${prefix}dy_rt`;
export const OAUTH_COOKIE = `${prefix}dy_oauth`;
export const SIGNUP_COOKIE = `${prefix}dy_signup`;

/** Matches the refresh token validity configured on the Cognito app client. */
export const REFRESH_TOKEN_MAX_AGE = 60 * 60 * 24 * 30;

export interface CookieOptions {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "lax" | "strict";
  path: string;
  maxAge: number;
}

/** Common surface of `cookies()` (server functions) and `NextResponse.cookies` (proxy, route handlers). */
export interface CookieWriter {
  set(name: string, value: string, options: CookieOptions): unknown;
}

export function cookieOptions(maxAge: number): CookieOptions {
  // Lax (not Strict) so the session survives the top-level redirect back from Google sign-in.
  return { httpOnly: true, secure: isProduction, sameSite: "lax", path: "/", maxAge };
}

export function writeSession(cookies: CookieWriter, tokens: TokenSet): void {
  cookies.set(ACCESS_COOKIE, tokens.accessToken, cookieOptions(Math.max(tokens.expiresIn, 60)));
  if (tokens.refreshToken) {
    cookies.set(REFRESH_COOKIE, tokens.refreshToken, cookieOptions(REFRESH_TOKEN_MAX_AGE));
  }
}

/** Expires the cookies with identical attributes (required for `__Host-` cookies to be cleared). */
export function clearSession(cookies: CookieWriter): void {
  cookies.set(ACCESS_COOKIE, "", cookieOptions(0));
  cookies.set(REFRESH_COOKIE, "", cookieOptions(0));
}
