import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { VerifyForm } from "@/components/auth/VerifyForm";
import { readPendingSignup } from "@/lib/auth/pendingSignup";

export const metadata: Metadata = { title: "Verify email" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function VerifyPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const sent = typeof params.sent === "string" ? params.sent : undefined;
  const pending = await readPendingSignup();

  return (
    <AuthShell
      title="Check your email"
      subtitle={
        sent ? (
          <>
            We sent a 6-digit code to <span className="font-medium text-fg">{sent}</span>.
          </>
        ) : (
          "Enter the 6-digit code we emailed you to activate your account."
        )
      }
      footer={
        <Link href="/login" className="font-semibold text-volt hover:text-volt-dim">
          Back to sign in
        </Link>
      }
    >
      {pending ? (
        <>
          <p className="mb-5 text-sm text-muted">
            Activating <span className="font-semibold text-fg">{pending.username}</span>
          </p>
          <VerifyForm />
        </>
      ) : (
        <div className="space-y-4">
          <FormAlert tone="info">
            We couldn&apos;t find a sign-up in progress on this device. If you already verified, sign in; otherwise
            register again.
          </FormAlert>
          <Link href="/register" className="block text-center text-sm font-semibold text-volt hover:text-volt-dim">
            Register
          </Link>
        </div>
      )}
    </AuthShell>
  );
}
