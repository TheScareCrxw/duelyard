import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AvatarPicker } from "@/components/settings/AvatarPicker";
import { UsernameSettingsForm } from "@/components/settings/UsernameSettingsForm";
import { VisibilityForm } from "@/components/settings/VisibilityForm";
import { requireUser } from "@/lib/auth/dal";

export const metadata: Metadata = { title: "Settings" };

export default async function SettingsPage() {
  const user = await requireUser("/settings");

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 sm:py-12">
      <h1 className="font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">Settings</h1>
      <p className="mt-2 text-sm text-muted">Signed in as {user.email}</p>

      <div className="mt-8 space-y-6">
        <Section title="Username">
          <UsernameSettingsForm current={user.username} availableAt={user.usernameChangeAvailableAt} />
        </Section>
        <Section title="Avatar">
          <AvatarPicker username={user.username} current={user.avatarUrl} />
        </Section>
        <Section title="Profile visibility">
          <VisibilityForm current={user.profileVisibility} />
        </Section>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line bg-surface p-5 sm:p-6">
      <h2 className="mb-5 text-xs font-semibold tracking-[0.18em] text-muted uppercase">{title}</h2>
      {children}
    </section>
  );
}
