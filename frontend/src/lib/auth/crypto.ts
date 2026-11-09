import { createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";

/**
 * Cognito SECRET_HASH for app clients with a client secret:
 * Base64(HMAC_SHA256(key = clientSecret, message = username + clientId)).
 */
export function secretHash(username: string, clientId: string, clientSecret: string): string {
  return createHmac("sha256", clientSecret).update(username + clientId).digest("base64");
}

/** URL-safe random token (used for OAuth state and PKCE verifiers). */
export function randomToken(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

/** PKCE S256 challenge for a verifier. */
export function pkceChallenge(verifier: string): string {
  return createHash("sha256").update(verifier).digest("base64url");
}

export function safeEqual(a: string, b: string): boolean {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}
