export const DEFAULT_AFTER_LOGIN = "/dashboard";

/**
 * Only allows same-origin relative paths as post-login destinations, preventing open redirects
 * such as `?next=https://evil.example` or `?next=//evil.example`.
 */
export function safeNextPath(next: unknown, fallback = DEFAULT_AFTER_LOGIN): string {
  if (typeof next !== "string" || next.length === 0 || next.length > 512) return fallback;
  if (!next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  // Reject control characters and backslashes, which some browsers normalize to "/".
  if (/[\u0000-\u001f\\]/.test(next)) return fallback;
  // Never bounce back into the auth flow itself.
  if (/^\/(login|register|auth\/)/.test(next)) return fallback;
  return next;
}
