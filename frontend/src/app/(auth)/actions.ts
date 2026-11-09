"use server";

import { randomUUID } from "node:crypto";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  confirmForgotPassword,
  confirmSignUp,
  forgotPassword,
  resendConfirmationCode,
  revokeRefreshToken,
  signInWithPassword,
  signUp,
  type TokenSet,
} from "@/lib/auth/cognito";
import { getAuthConfig, isAuthConfigured } from "@/lib/auth/config";
import { CognitoError, describeCognitoError } from "@/lib/auth/errors";
import { clearPendingSignup, readPendingSignup, savePendingSignup } from "@/lib/auth/pendingSignup";
import { safeNextPath } from "@/lib/auth/redirect";
import { REFRESH_COOKIE, clearSession, writeSession } from "@/lib/auth/session";
import {
  formPassword,
  formString,
  validateCode,
  validateEmail,
  validatePassword,
  validateUsername,
  type FieldErrors,
} from "@/lib/auth/validation";
import { checkUsernameAvailability, getCurrentUser } from "@/services/userService";
import type { AuthFormState } from "@/types/auth";

/*
 * Server actions for the auth pages. Next.js rejects cross-origin server action calls, which
 * protects these from CSRF. Passwords are never echoed back to the client.
 */

const NOT_CONFIGURED: AuthFormState = {
  error: "Sign-in isn't configured on this server yet. See the README for the Cognito setup.",
};

const hasErrors = (errors: FieldErrors) => Object.values(errors).some(Boolean);

const isCredentialError = (error: unknown) =>
  error instanceof CognitoError && (error.code === "NotAuthorizedException" || error.code === "UserNotFoundException");

// --- Sign in -----------------------------------------------------------------------------------

export async function loginAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const identifier = formString(formData, "identifier");
  const password = formPassword(formData, "password");
  const next = safeNextPath(formString(formData, "next"));
  const values = { identifier };

  const fieldErrors: FieldErrors = {
    identifier: identifier ? undefined : "Enter your username or email.",
    password: password ? undefined : "Enter your password.",
  };
  if (hasErrors(fieldErrors)) return { fieldErrors, values };

  let tokens: TokenSet;
  try {
    tokens = await signInWithPassword(identifier, password);
  } catch (error) {
    // Until the first sign-in completes, Cognito only knows the new account by email. If this
    // device just signed up with that username, retry with the email.
    const pending = await readPendingSignup();
    if (isCredentialError(error) && pending && pending.username.toLowerCase() === identifier.toLowerCase()) {
      try {
        tokens = await signInWithPassword(pending.email, password);
      } catch (retryError) {
        return { error: describeCognitoError(retryError), values };
      }
    } else {
      if (error instanceof CognitoError && error.code === "UserNotConfirmedException") redirect("/verify");
      if (!(error instanceof CognitoError)) console.error("Sign-in failed", error);
      return { error: describeCognitoError(error), values };
    }
  }

  writeSession(await cookies(), tokens);
  // Creates the account and claims the username for sign-in on first login.
  await provisionQuietly(tokens.accessToken);
  await clearPendingSignup();
  redirect(next);
}

// --- Register ----------------------------------------------------------------------------------

export async function registerAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const username = formString(formData, "username");
  const email = formString(formData, "email").toLowerCase();
  const password = formPassword(formData, "password");
  const confirm = formPassword(formData, "confirmPassword");
  const values = { username, email };

  const fieldErrors: FieldErrors = {
    username: validateUsername(username),
    email: validateEmail(email),
    password: validatePassword(password),
    confirmPassword: password === confirm ? undefined : "Passwords don't match.",
  };
  if (hasErrors(fieldErrors)) return { fieldErrors, values };

  try {
    const availability = await checkUsernameAvailability(username);
    if (!availability.available) {
      const message =
        availability.reason === "RESERVED" ? "That username is reserved." : "That username is already taken.";
      return { fieldErrors: { username: message }, values };
    }
  } catch (error) {
    // The backend re-checks on first sign-in and picks an alternative if needed.
    console.warn("Username availability check skipped", error);
  }

  const id = randomUUID();
  let destination: string | undefined;
  try {
    ({ destination } = await signUp(id, username, email, password));
  } catch (error) {
    if (error instanceof CognitoError && error.code === "InvalidPasswordException") {
      return { fieldErrors: { password: describeCognitoError(error) }, values };
    }
    if (!(error instanceof CognitoError)) console.error("Sign-up failed", error);
    return { error: describeCognitoError(error), values };
  }

  await savePendingSignup({ id, username, email });
  redirect(destination ? `/verify?sent=${encodeURIComponent(destination)}` : "/verify");
}

// --- Email verification ------------------------------------------------------------------------

export async function verifyAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const pending = await readPendingSignup();
  if (!pending) return { error: "Your sign-up session expired. Please register again." };

  const code = formString(formData, "code");
  const codeError = validateCode(code);
  if (codeError) return { fieldErrors: { code: codeError } };

  try {
    await confirmSignUp(pending.id, code);
  } catch (error) {
    const alreadyConfirmed = error instanceof CognitoError && error.code === "NotAuthorizedException";
    if (!alreadyConfirmed) return { error: describeCognitoError(error) };
  }
  // Keep the pending sign-up: the first sign-in uses it (see loginAction).
  redirect("/login?verified=1");
}

export async function resendCodeAction(): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const pending = await readPendingSignup();
  if (!pending) return { error: "Your sign-up session expired. Please register again." };
  try {
    const { destination } = await resendConfirmationCode(pending.id);
    return { message: destination ? `New code sent to ${destination}.` : "New code sent." };
  } catch (error) {
    return { error: describeCognitoError(error) };
  }
}

// --- Forgot password ---------------------------------------------------------------------------

export async function forgotPasswordAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const identifier = formString(formData, "identifier");
  if (!identifier) return { fieldErrors: { identifier: "Enter your username or email." }, step: "request" };

  try {
    await forgotPassword(identifier);
  } catch (error) {
    const code = error instanceof CognitoError ? error.code : "";
    // Don't reveal whether an account exists.
    if (code !== "UserNotFoundException" && code !== "InvalidParameterException") {
      return { error: describeCognitoError(error), values: { identifier }, step: "request" };
    }
  }
  return {
    step: "reset",
    values: { identifier },
    message: "If an account matches, we've emailed it a reset code.",
  };
}

export async function resetPasswordAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const identifier = formString(formData, "identifier");
  const code = formString(formData, "code");
  const password = formPassword(formData, "password");
  const values = { identifier };

  const fieldErrors: FieldErrors = { code: validateCode(code), password: validatePassword(password) };
  if (hasErrors(fieldErrors)) return { fieldErrors, values, step: "reset" };

  try {
    await confirmForgotPassword(identifier, code, password);
  } catch (error) {
    return { error: describeCognitoError(error), values, step: "reset" };
  }
  redirect(`/login?reset=1&identifier=${encodeURIComponent(identifier)}`);
}

// --- Sign out ----------------------------------------------------------------------------------

export async function logoutAction(): Promise<void> {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_COOKIE)?.value;
  clearSession(cookieStore);

  const config = getAuthConfig();
  if (refreshToken && config) {
    try {
      await revokeRefreshToken(refreshToken);
    } catch (error) {
      console.warn("Refresh token revocation failed", error);
    }
  }

  // Also end the Cognito managed-login session, so "Continue with Google" asks again.
  if (config) {
    const logout = new URL(`${config.domain}/logout`);
    logout.search = new URLSearchParams({ client_id: config.clientId, logout_uri: `${config.appUrl}/` }).toString();
    redirect(logout.toString());
  }
  redirect("/");
}

// -----------------------------------------------------------------------------------------------

/** Creates the application account right after sign-in. Non-fatal: pages provision on demand. */
async function provisionQuietly(accessToken: string): Promise<void> {
  try {
    await getCurrentUser(accessToken);
  } catch (error) {
    console.error("Account provisioning after sign-in failed", error);
  }
}
