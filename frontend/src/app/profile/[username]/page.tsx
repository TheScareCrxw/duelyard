import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { ProfileStats } from "@/components/player/ProfileStats";
import { LinkButton } from "@/components/ui/Button";
import { getAccessToken } from "@/lib/auth/dal";
import { getPlayerProfile } from "@/services/playerService";
import { loadRatingHistories } from "@/services/ratingHistories";

type Params = Promise<{ username: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { username } = await params;
  return { title: decodeURIComponent(username) };
}

/** Public profile. Signed-in owners see their own profile even when it's private. */
export default async function ProfilePage({ params }: { params: Params }) {
  const { username } = await params;
  const accessToken = await getAccessToken();
  const profile = await getPlayerProfile(decodeURIComponent(username), accessToken);
  if (!profile) notFound();
  const ratingHistories = await loadRatingHistories(profile, accessToken);

  const joined = new Date(profile.memberSince).toLocaleDateString("en-US", { month: "long", year: "numeric" });

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-12">
      <header className="relative overflow-hidden rounded-2xl border border-line bg-surface p-6 sm:p-8">
        <div aria-hidden className="absolute -top-24 -right-16 h-56 w-56 rounded-full bg-volt/10 blur-3xl" />
        <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center">
          <PlayerAvatar username={profile.username} avatarUrl={profile.avatarUrl} size="xl" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">
                {profile.username}
              </h1>
              {profile.self && profile.profileVisibility === "PRIVATE" && (
                <span className="rounded-full bg-raised px-2.5 py-1 text-xs font-semibold text-muted">Private</span>
              )}
            </div>
            <p className="mt-1 text-sm text-muted">Member since {joined}</p>
          </div>
          {profile.self && (
            <LinkButton href="/settings" variant="secondary" size="sm">
              Edit profile
            </LinkButton>
          )}
        </div>
      </header>

      <div className="mt-10">
        {profile.restricted ? (
          <div className="rounded-xl border border-dashed border-line px-5 py-12 text-center">
            <p className="font-semibold">This profile is private</p>
            <p className="mt-1 text-sm text-muted">{profile.username} has chosen to keep their stats to themselves.</p>
          </div>
        ) : (
          <ProfileStats
            profile={profile}
            ratingHistories={ratingHistories}
            emptyMatches={profile.self ? "You haven't played any matches yet." : `${profile.username} hasn't played any matches yet.`}
          />
        )}
      </div>
    </div>
  );
}
