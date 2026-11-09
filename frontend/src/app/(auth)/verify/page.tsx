import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/components/auth/AuthShell";
import { VerifyForm } from "@/components/auth/VerifyForm";

export const metadata: Metadata = { title: "Verify email" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function VerifyPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const username = typeof params.username === "string" ? params.username : undefined;
  const sent = typeof params.sent === "string" ? params.sent : undefined;

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
      <VerifyForm username={username} />
    </AuthShell>
  );
}
