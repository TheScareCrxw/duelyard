import "server-only";
import { callbackUrl, requireAuthConfig, type AuthConfig } from "./config";
import { secretHash } from "./crypto";
import { CognitoError } from "./errors";

/**
 * Minimal server-side Cognito client. User-pool operations used here (SignUp, InitiateAuth, ...)
 * are public APIs authorized by the app client id + SECRET_HASH, so no AWS IAM credentials are
 * needed. OAuth flows (Google, refresh, revoke) use the Cognito domain's token endpoints with the
 * client secret.
 */

const TIMEOUT_MS = 8000;

export interface TokenSet {
  accessToken: string;
  /** Absent on refresh (Cognito keeps the existing refresh token valid). */
  refreshToken?: string;
  expiresIn: number;
}

export interface CodeDelivery {
  /** Masked destination, e.g. "a***@e***". */
  destination?: string;
}

// ---------------------------------------------------------------------------------------------
// User-pool API (JSON over HTTPS)
// ---------------------------------------------------------------------------------------------

async function callCognito<T>(config: AuthConfig, operation: string, body: Record<string, unknown>): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`https://cognito-idp.${config.region}.amazonaws.com/`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-amz-json-1.1",
        "X-Amz-Target": `AWSCognitoIdentityProviderService.${operation}`,
      },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (cause) {
    throw new CognitoError("NetworkError", `Cognito unreachable: ${(cause as Error).message}`, 503);
  }

  let data: Record<string, unknown> = {};
  try {
    const text = await response.text();
    if (text) data = JSON.parse(text) as Record<string, unknown>;
  } catch {
    // Non-JSON body (e.g. a proxy error page); handled by the status check below.
  }
  if (!response.ok) {
    const type = String(data.__type ?? "UnknownError");
    // __type can be "com.amazonaws...#NotAuthorizedException"; keep only the name.
    throw new CognitoError(type.split("#").pop()!, String(data.message ?? "Cognito request failed"), response.status);
  }
  return data as T;
}

function hashFor(config: AuthConfig, username: string): string {
  return secretHash(username, config.clientId, config.clientSecret);
}

/**
 * Creates an unconfirmed account. The Cognito username is an opaque id the player never sees;
 * they sign in with their app username (the preferred_username alias, which the backend claims on
 * first sign-in) or their email. Cognito doesn't allow setting an alias before confirmation, so
 * the chosen username travels in custom:desired_username.
 */
export async function signUp(
  cognitoUsername: string,
  desiredUsername: string,
  email: string,
  password: string,
): Promise<CodeDelivery> {
  const config = requireAuthConfig();
  const result = await callCognito<{ CodeDeliveryDetails?: { Destination?: string } }>(config, "SignUp", {
    ClientId: config.clientId,
    SecretHash: hashFor(config, cognitoUsername),
    Username: cognitoUsername,
    Password: password,
    UserAttributes: [
      { Name: "email", Value: email },
      { Name: "custom:desired_username", Value: desiredUsername },
    ],
  });
  return { destination: result.CodeDeliveryDetails?.Destination };
}

export async function confirmSignUp(username: string, code: string): Promise<void> {
  const config = requireAuthConfig();
  await callCognito(config, "ConfirmSignUp", {
    ClientId: config.clientId,
    SecretHash: hashFor(config, username),
    Username: username,
    ConfirmationCode: code,
  });
}

export async function resendConfirmationCode(username: string): Promise<CodeDelivery> {
  const config = requireAuthConfig();
  const result = await callCognito<{ CodeDeliveryDetails?: { Destination?: string } }>(
    config,
    "ResendConfirmationCode",
    { ClientId: config.clientId, SecretHash: hashFor(config, username), Username: username },
  );
  return { destination: result.CodeDeliveryDetails?.Destination };
}

/** Username/email + password sign-in. Requires ALLOW_USER_PASSWORD_AUTH on the app client. */
export async function signInWithPassword(identifier: string, password: string): Promise<TokenSet> {
  const config = requireAuthConfig();
  const result = await callCognito<{
    ChallengeName?: string;
    AuthenticationResult?: { AccessToken: string; RefreshToken?: string; ExpiresIn: number };
  }>(config, "InitiateAuth", {
    AuthFlow: "USER_PASSWORD_AUTH",
    ClientId: config.clientId,
    AuthParameters: { USERNAME: identifier, PASSWORD: password, SECRET_HASH: hashFor(config, identifier) },
  });

  if (result.ChallengeName || !result.AuthenticationResult) {
    // MFA / forced password change are not enabled for this pool; treat as unsupported.
    throw new CognitoError(result.ChallengeName ?? "UnsupportedChallenge", "Additional sign-in step required", 400);
  }
  const auth = result.AuthenticationResult;
  return { accessToken: auth.AccessToken, refreshToken: auth.RefreshToken, expiresIn: auth.ExpiresIn };
}

export async function forgotPassword(identifier: string): Promise<CodeDelivery> {
  const config = requireAuthConfig();
  const result = await callCognito<{ CodeDeliveryDetails?: { Destination?: string } }>(config, "ForgotPassword", {
    ClientId: config.clientId,
    SecretHash: hashFor(config, identifier),
    Username: identifier,
  });
  return { destination: result.CodeDeliveryDetails?.Destination };
}

export async function confirmForgotPassword(identifier: string, code: string, newPassword: string): Promise<void> {
  const config = requireAuthConfig();
  await callCognito(config, "ConfirmForgotPassword", {
    ClientId: config.clientId,
    SecretHash: hashFor(config, identifier),
    Username: identifier,
    ConfirmationCode: code,
    Password: newPassword,
  });
}

// ---------------------------------------------------------------------------------------------
// OAuth endpoints on the Cognito domain
// ---------------------------------------------------------------------------------------------

function basicAuth(config: AuthConfig): string {
  return "Basic " + Buffer.from(`${config.clientId}:${config.clientSecret}`).toString("base64");
}

async function postForm(config: AuthConfig, path: string, form: Record<string, string>): Promise<Response> {
  try {
    return await fetch(`${config.domain}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Authorization: basicAuth(config) },
      body: new URLSearchParams(form),
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (cause) {
    throw new CognitoError("NetworkError", `Cognito unreachable: ${(cause as Error).message}`, 503);
  }
}

async function tokenRequest(config: AuthConfig, form: Record<string, string>): Promise<TokenSet> {
  const response = await postForm(config, "/oauth2/token", form);
  const data = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new CognitoError(String(data.error ?? "token_error"), "Token request failed", response.status);
  }
  return {
    accessToken: String(data.access_token),
    refreshToken: typeof data.refresh_token === "string" ? data.refresh_token : undefined,
    expiresIn: Number(data.expires_in),
  };
}

export async function exchangeAuthorizationCode(code: string, codeVerifier: string): Promise<TokenSet> {
  const config = requireAuthConfig();
  return tokenRequest(config, {
    grant_type: "authorization_code",
    client_id: config.clientId,
    code,
    redirect_uri: callbackUrl(config),
    code_verifier: codeVerifier,
  });
}

export async function refreshSession(refreshToken: string): Promise<TokenSet> {
  const config = requireAuthConfig();
  return tokenRequest(config, { grant_type: "refresh_token", client_id: config.clientId, refresh_token: refreshToken });
}

/** Revokes a refresh token and every access token issued from it. Best effort. */
export async function revokeRefreshToken(refreshToken: string): Promise<void> {
  const config = requireAuthConfig();
  const response = await postForm(config, "/oauth2/revoke", { token: refreshToken, client_id: config.clientId });
  if (!response.ok) {
    throw new CognitoError("revoke_failed", "Token revocation failed", response.status);
  }
}

/** True when a refresh failed because the refresh token itself is no longer valid. */
export function isInvalidGrant(error: unknown): boolean {
  return error instanceof CognitoError && (error.code === "invalid_grant" || error.status === 400);
}
