import { NextResponse, type NextRequest } from "next/server";
import { callbackUrl, getAuthConfig } from "@/lib/auth/config";
import { pkceChallenge, randomToken } from "@/lib/auth/crypto";
import { safeNextPath } from "@/lib/auth/redirect";
import { OAUTH_COOKIE, cookieOptions } from "@/lib/auth/session";

/**
 * Starts "Continue with Google": redirects to Cognito's authorize endpoint with Google as the
 * identity provider, using the authorization-code flow with PKCE. The state and code verifier are
 * kept in a short-lived HttpOnly cookie and checked in /auth/callback.
 */
export async function GET(request: NextRequest) {
  const config = getAuthConfig();
  if (!config) {
    return NextResponse.redirect(new URL("/login?error=not_configured", request.url));
  }

  const state = randomToken();
  const verifier = randomToken(48);
  const next = safeNextPath(request.nextUrl.searchParams.get("next"));

  const authorize = new URL(`${config.domain}/oauth2/authorize`);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: config.clientId,
    redirect_uri: callbackUrl(config),
    identity_provider: "Google",
    // aws.cognito.signin.user.admin lets the backend read the user's email via GetUser.
    scope: "openid email profile aws.cognito.signin.user.admin",
    state,
    code_challenge: pkceChallenge(verifier),
    code_challenge_method: "S256",
  }).toString();

  const response = NextResponse.redirect(authorize);
  response.cookies.set(OAUTH_COOKIE, JSON.stringify({ state, verifier, next }), cookieOptions(600));
  return response;
}
