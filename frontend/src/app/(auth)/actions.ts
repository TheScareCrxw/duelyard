"use server";

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
} from "@/lib/auth/cognito";
import { getAuthConfig, isAuthConfigured } from "@/lib/auth/config";
import { CognitoError, describeCognitoError } from "@/lib/auth/errors";
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

function verifyUrl(username: string, destination?: string): string {
  const params = new URLSearchParams({ username });
  if (destination) params.set("sent", destination);
  return `/verify?${params}`;
}

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

  let tokens;
  try {
    tokens = await signInWithPassword(identifier, password);
  } catch (error) {
    if (error instanceof CognitoError && error.code === "UserNotConfirmedException") {
      redirect(verifyUrl(identifier));
    }
    if (!(error instanceof CognitoError)) console.error("Sign-in failed", error);
    return { error: describeCognitoError(error), values };
  }

  writeSession(await cookies(), tokens);
  await provisionQuietly(tokens.accessToken);
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

  // Usernames must also be free in the app (Google accounts get app usernames without a matching
  // Cognito username). If the API is down, Cognito's own uniqueness check still applies.
  try {
    const availability = await checkUsernameAvailability(username);
    if (!availability.available) {
      return { fieldErrors: { username: "That username is already taken." }, values };
    }
  } catch (error) {
    console.warn("Username availability check skipped", error);
  }

  let destination: string | undefined;
  try {
    ({ destination } = await signUp(username, email, password));
  } catch (error) {
    if (error instanceof CognitoError && error.code === "UsernameExistsException") {
      return { fieldErrors: { username: "That username is already taken." }, values };
    }
    if (error instanceof CognitoError && error.code === "InvalidPasswordException") {
      return { fieldErrors: { password: describeCognitoError(error) }, values };
    }
    if (!(error instanceof CognitoError)) console.error("Sign-up failed", error);
    return { error: describeCognitoError(error), values };
  }

  redirect(verifyUrl(username, destination));
}

// --- Email verification ------------------------------------------------------------------------

export async function verifyAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const username = formString(formData, "username");
  const code = formString(formData, "code");
  const values = { username };

  const fieldErrors: FieldErrors = {
    username: username ? undefined : "Enter your username.",
    code: validateCode(code),
  };
  if (hasErrors(fieldErrors)) return { fieldErrors, values };

  try {
    await confirmSignUp(username, code);
  } catch (error) {
    if (error instanceof CognitoError && error.code === "NotAuthorizedException") {
      // Already confirmed.
      redirect(`/login?verified=1&identifier=${encodeURIComponent(username)}`);
    }
    return { error: describeCognitoError(error), values };
  }

  redirect(`/login?verified=1&identifier=${encodeURIComponent(username)}`);
}

export async function resendCodeAction(_prev: AuthFormState, formData: FormData): Promise<AuthFormState> {
  if (!isAuthConfigured()) return NOT_CONFIGURED;
  const username = formString(formData, "username");
  if (!username) return { fieldErrors: { username: "Enter your username." } };
  try {
    const { destination } = await resendConfirmationCode(username);
    return { message: destination ? `New code sent to ${destination}.` : "New code sent.", values: { username } };
  } catch (error) {
    return { error: describeCognitoError(error), values: { username } };
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
