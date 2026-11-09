import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { GoogleButton, OrDivider } from "@/components/auth/GoogleButton";
import { LoginForm } from "@/components/auth/LoginForm";
import { isAuthConfigured } from "@/lib/auth/config";
import { readPendingSignup } from "@/lib/auth/pendingSignup";
import { safeNextPath } from "@/lib/auth/redirect";

export const metadata: Metadata = { title: "Sign in" };

const ERRORS: Record<string, string> = {
  not_configured: "Sign-in isn't configured on this server yet.",
  oauth_denied: "Google sign-in was cancelled.",
  oauth_state: "Your sign-in link expired. Please try again.",
  oauth_failed: "Google sign-in failed. Please try again.",
};

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function LoginPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const one = (key: string) => (typeof params[key] === "string" ? (params[key] as string) : undefined);
  const next = safeNextPath(one("next"));
  const error = ERRORS[one("error") ?? ""];
  const identifier = one("identifier") ?? (await readPendingSignup())?.username;

  return (
    <AuthShell
      title="Sign in"
      subtitle="Welcome back. Your rivals have been practicing."
      footer={
        <>
          New here?{" "}
          <Link href="/register" className="font-semibold text-volt hover:text-volt-dim">
            Create an account
          </Link>
        </>
      }
    >
      <div className="space-y-4">
        {!isAuthConfigured() && (
          <FormAlert tone="info">
            Sign-in isn&apos;t configured yet. Add the Cognito settings to <code>.env.local</code> (see the README).
          </FormAlert>
        )}
        {error && <FormAlert tone="error">{error}</FormAlert>}
        {one("verified") && <FormAlert tone="success">Email verified. Sign in to start playing.</FormAlert>}
        {one("reset") && <FormAlert tone="success">Password updated. Sign in with your new password.</FormAlert>}
      </div>
      <div className="mt-4">
        <GoogleButton next={next} />
        <OrDivider />
        <LoginForm next={next} identifier={identifier} />
      </div>
    </AuthShell>
  );
}
