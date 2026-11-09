import type { Metadata } from "next";
import Link from "next/link";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { ProfileStats } from "@/components/player/ProfileStats";
import { LinkButton } from "@/components/ui/Button";
import { getAccessToken, requireUser } from "@/lib/auth/dal";
import { getPlayerProfile } from "@/services/playerService";

export const metadata: Metadata = { title: "Dashboard" };

export default async function DashboardPage() {
  const user = await requireUser("/dashboard");
  const profile = await getPlayerProfile(user.username, await getAccessToken());

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-12">
      <div className="flex flex-col gap-5 rounded-2xl border border-line bg-surface p-6 sm:flex-row sm:items-center sm:p-8">
        <PlayerAvatar username={user.username} avatarUrl={user.avatarUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="text-sm text-muted">Welcome back,</p>
          <h1 className="truncate font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">{user.username}</h1>
        </div>
        <div className="flex gap-2">
          <LinkButton href={`/profile/${encodeURIComponent(user.username)}`} variant="secondary" size="sm">
            View profile
          </LinkButton>
          <LinkButton href="/settings" variant="ghost" size="sm">
            Settings
          </LinkButton>
        </div>
      </div>

      <div className="mt-10">
        {profile ? (
          <ProfileStats profile={profile} emptyMatches="No matches yet. Ranked play opens soon." />
        ) : (
          <p className="text-sm text-muted">
            Your stats are unavailable right now. <Link href="/dashboard" className="text-volt">Retry</Link>
          </p>
        )}
      </div>
    </div>
  );
}
