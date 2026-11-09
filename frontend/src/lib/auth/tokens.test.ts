import { describe, expect, it } from "vitest";
import { decodeJwtPayload, isTokenExpired, tokenExpiry } from "./tokens";

const jwt = (payload: object) =>
  ["eyJhbGciOiJSUzI1NiJ9", Buffer.from(JSON.stringify(payload)).toString("base64url"), "sig"].join(".");

describe("tokens", () => {
  const now = 1_800_000_000_000;

  it("reads the expiry claim", () => {
    expect(tokenExpiry(jwt({ exp: 1_800_000_100 }))).toBe(1_800_000_100);
    expect(decodeJwtPayload(jwt({ sub: "abc" }))).toEqual({ sub: "abc" });
  });

  it("treats tokens within the skew window as expired", () => {
    const token = jwt({ exp: now / 1000 + 30 });
    expect(isTokenExpired(token, 0, now)).toBe(false);
    expect(isTokenExpired(token, 60, now)).toBe(true);
  });

  it("treats malformed tokens as expired", () => {
    expect(isTokenExpired("not-a-jwt", 0, now)).toBe(true);
    expect(isTokenExpired("a.%%%.c", 0, now)).toBe(true);
    expect(isTokenExpired(jwt({ sub: "no-exp" }), 0, now)).toBe(true);
  });
});
