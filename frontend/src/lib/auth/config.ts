import "server-only";

/**
 * Cognito configuration, read from server-only environment variables. Nothing here is exposed
 * to the browser (no NEXT_PUBLIC_ prefix).
 */
export interface AuthConfig {
  appUrl: string;
  region: string;
  clientId: string;
  clientSecret: string;
  /** Cognito domain (managed login / OAuth endpoints), e.g. https://duelyard.auth.us-east-1.amazoncognito.com */
  domain: string;
}

let cached: AuthConfig | null | undefined;

export function getAuthConfig(): AuthConfig | null {
  if (cached !== undefined) return cached;
  const appUrl = process.env.APP_URL;
  const region = process.env.COGNITO_REGION;
  const clientId = process.env.COGNITO_CLIENT_ID;
  const clientSecret = process.env.COGNITO_CLIENT_SECRET;
  const domain = process.env.COGNITO_DOMAIN?.replace(/\/+$/, "");
  cached = appUrl && region && clientId && clientSecret && domain ? { appUrl, region, clientId, clientSecret, domain } : null;
  return cached;
}

export function requireAuthConfig(): AuthConfig {
  const config = getAuthConfig();
  if (!config) {
    throw new Error(
      "Authentication is not configured. Set APP_URL, COGNITO_REGION, COGNITO_CLIENT_ID, COGNITO_CLIENT_SECRET and COGNITO_DOMAIN.",
    );
  }
  return config;
}

export function isAuthConfigured(): boolean {
  return getAuthConfig() !== null;
}

/** Redirect URI registered on the Cognito app client for the OAuth (Google) flow. */
export function callbackUrl(config: AuthConfig): string {
  return `${config.appUrl}/auth/callback`;
}
