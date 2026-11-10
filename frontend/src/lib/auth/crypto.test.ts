import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import { pkceChallenge, randomToken, safeEqual, secretHash } from "./crypto";

describe("crypto", () => {
  it("computes Cognito SECRET_HASH as HMAC-SHA256(secret, username + clientId)", () => {
    const expected = createHmac("sha256", "secret").update("aliceclient123").digest("base64");
    expect(secretHash("alice", "client123", "secret")).toBe(expected);
  });

  it("computes the RFC 7636 S256 challenge", () => {
    // Test vector from RFC 7636, Appendix B.
    expect(pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    );
  });

  it("generates url-safe random tokens", () => {
    const token = randomToken();
    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(randomToken()).not.toBe(token);
  });

  it("compares strings safely", () => {
    expect(safeEqual("abc", "abc")).toBe(true);
    expect(safeEqual("abc", "abd")).toBe(false);
    expect(safeEqual("abc", "abcd")).toBe(false);
  });
});
