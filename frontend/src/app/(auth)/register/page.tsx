import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { GoogleButton, OrDivider } from "@/components/auth/GoogleButton";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { isAuthConfigured } from "@/lib/auth/config";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <AuthShell
      title="Create account"
      subtitle="Every player starts at 1000 in every game. Where you go from there is up to you."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="font-semibold text-volt hover:text-volt-dim">
            Sign in
          </Link>
        </>
      }
    >
      {!isAuthConfigured() && (
        <div className="mb-4">
          <FormAlert tone="info">
            Registration isn&apos;t configured yet. Add the Cognito settings to <code>.env.local</code> (see the README).
          </FormAlert>
        </div>
      )}
      <GoogleButton label="Sign up with Google" />
      <OrDivider />
      <RegisterForm />
    </AuthShell>
  );
}
