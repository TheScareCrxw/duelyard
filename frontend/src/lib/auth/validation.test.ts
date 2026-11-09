import { describe, expect, it } from "vitest";
import { validateCode, validateEmail, validatePassword, validateUsername } from "./validation";

describe("validation", () => {
  it("validates usernames like the backend", () => {
    expect(validateUsername("Ace_99")).toBeUndefined();
    expect(validateUsername("ab")).toBeDefined();
    expect(validateUsername("a".repeat(21))).toBeDefined();
    expect(validateUsername("bad name")).toBeDefined();
  });

  it("validates emails", () => {
    expect(validateEmail("a@b.co")).toBeUndefined();
    expect(validateEmail("nope")).toBeDefined();
  });

  it("enforces the Cognito password policy", () => {
    expect(validatePassword("Str0ng!pass")).toBeUndefined();
    expect(validatePassword("Sh0rt!")).toMatch(/8 characters/);
    expect(validatePassword("alllower1!")).toMatch(/uppercase/);
    expect(validatePassword("NoNumbers!")).toMatch(/number/);
    expect(validatePassword("NoSymbol123")).toMatch(/symbol/);
  });

  it("requires 6-digit codes", () => {
    expect(validateCode("123456")).toBeUndefined();
    expect(validateCode("12345")).toBeDefined();
    expect(validateCode("abcdef")).toBeDefined();
  });
});
