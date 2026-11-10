import type { Metadata } from "next";
import Link from "next/link";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { ProfileStats } from "@/components/player/ProfileStats";
import { LinkButton } from "@/components/ui/Button";
import { GAME_PRESENTATIONS } from "@/games/registry";
import { getAccessToken, requireUser } from "@/lib/auth/dal";
import { getPlayerProfile } from "@/services/playerService";
import { loadRatingHistories } from "@/services/ratingHistories";

export const metadata: Metadata = { title: "Dashboard" };

export default async function DashboardPage() {
  const user = await requireUser("/dashboard");
  const accessToken = await getAccessToken();
  const profile = await getPlayerProfile(user.username, accessToken);
  const ratingHistories = profile ? await loadRatingHistories(profile, accessToken) : undefined;

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

      <section aria-labelledby="quick-play" className="mt-8">
        <h2 id="quick-play" className="text-xs font-semibold tracking-[0.18em] text-muted uppercase">
          Quick play · ranked
        </h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {GAME_PRESENTATIONS.map(({ slug, displayName, Icon, accent }) => (
            <Link
              key={slug}
              href={`/play/${slug}?mode=RANKED`}
              className="flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3 text-sm font-semibold transition-colors hover:border-faint"
            >
              <Icon className="h-5 w-5 shrink-0" style={{ color: accent }} />
              <span className="truncate">{displayName}</span>
            </Link>
          ))}
        </div>
      </section>

      <div className="mt-10">
        {profile ? (
          <ProfileStats
            profile={profile}
            ratingHistories={ratingHistories}
            emptyMatches="No matches yet. Pick a game above to play your first."
          />
        ) : (
          <p className="text-sm text-muted">
            Your stats are unavailable right now. <Link href="/dashboard" className="text-volt">Retry</Link>
          </p>
        )}
      </div>
    </div>
  );
}
