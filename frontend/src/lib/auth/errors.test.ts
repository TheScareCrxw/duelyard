import { describe, expect, it } from "vitest";
import { CognitoError, describeCognitoError } from "./errors";

describe("describeCognitoError", () => {
  it("never reveals whether an account exists", () => {
    const wrongPassword = describeCognitoError(new CognitoError("NotAuthorizedException", "", 400));
    const noSuchUser = describeCognitoError(new CognitoError("UserNotFoundException", "", 400));
    expect(wrongPassword).toBe(noSuchUser);
  });

  it("falls back to a generic message", () => {
    expect(describeCognitoError(new Error("boom"))).toMatch(/Something went wrong/);
    expect(describeCognitoError(new CognitoError("Weird", "", 500))).toMatch(/Something went wrong/);
  });
});
