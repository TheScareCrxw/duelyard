import { describe, expect, it } from "vitest";
import { DEFAULT_AFTER_LOGIN, safeNextPath } from "./redirect";

describe("safeNextPath", () => {
  it("allows same-origin paths", () => {
    expect(safeNextPath("/profile/ace")).toBe("/profile/ace");
    expect(safeNextPath("/play?game=darts")).toBe("/play?game=darts");
  });

  it.each([
    "https://evil.example",
    "//evil.example",
    String.raw`/\evil.example`,
    String.raw`/foo\bar`,
    "javascript:alert(1)",
    "relative/path",
    "/ok\nSet-Cookie: x",
    "/login",
    "/auth/callback",
    "",
    null,
    undefined,
  ])("rejects %j", (value) => {
    expect(safeNextPath(value)).toBe(DEFAULT_AFTER_LOGIN);
  });
});
