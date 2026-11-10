/**
 * Reads claims from a JWT WITHOUT verifying its signature. Only for scheduling decisions on the
 * Next.js server (e.g. "is this token about to expire, so refresh it?"). The Spring Boot API
 * verifies every token it receives; never use these claims for authorization.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const json = Buffer.from(parts[1], "base64url").toString("utf8");
    const payload: unknown = JSON.parse(json);
    return payload && typeof payload === "object" ? (payload as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Seconds since epoch when the token expires, or null if unreadable. */
export function tokenExpiry(token: string): number | null {
  const exp = decodeJwtPayload(token)?.exp;
  return typeof exp === "number" ? exp : null;
}

/** True if the token is unreadable or expires within `skewSeconds`. */
export function isTokenExpired(token: string, skewSeconds = 0, nowMs = Date.now()): boolean {
  const exp = tokenExpiry(token);
  return exp === null || exp * 1000 <= nowMs + skewSeconds * 1000;
}
