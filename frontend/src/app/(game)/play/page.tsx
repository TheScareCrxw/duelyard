import type { Metadata } from "next";
import type { CSSProperties } from "react";
import { ActiveMatchBanner, ConnectionNotice } from "@/components/match/ActiveMatchBanner";
import { RankBadge } from "@/components/rank/RankBadge";
import { LinkButton } from "@/components/ui/Button";
import { presentationFor } from "@/games/registry";
import { getAccessToken, requireUser } from "@/lib/auth/dal";
import { listGames } from "@/services/gameService";
import { getPlayerProfile } from "@/services/playerService";

export const metadata: Metadata = { title: "Play" };

/** Game selection: each game with your rating, its modes and play buttons. */
export default async function PlayPage() {
  const user = await requireUser("/play");
  const [games, profile] = await Promise.all([listGames(), getPlayerProfile(user.username, await getAccessToken())]);
  const ratingOf = (slug: string) => profile?.ratings?.find((r) => r.gameSlug === slug);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-12">
      <ConnectionNotice />
      <ActiveMatchBanner />
      <h1 className="font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">Play</h1>
      <p className="mt-2 text-sm text-muted">Ranked matches move your rating. Casual matches never do.</p>

      <div className="mt-8 grid gap-5 sm:grid-cols-2">
        {games.map((g) => {
          const p = presentationFor(g.slug, g.displayName);
          const rating = ratingOf(g.slug);
          const live = g.availability === "AVAILABLE";
          return (
            <article
              key={g.slug}
              style={{ "--accent": p.accent } as CSSProperties}
              className="flex flex-col rounded-2xl border border-line bg-surface p-6"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-3">
                  <span
                    className="flex h-12 w-12 items-center justify-center rounded-xl border"
                    style={{ color: p.accent, borderColor: `color-mix(in oklab, ${p.accent} 35%, transparent)` }}
                  >
                    <p.Icon className="h-7 w-7" />
                  </span>
                  <h2 className="font-display text-2xl font-bold tracking-wide uppercase">{g.displayName}</h2>
                </div>
                {rating && <RankBadge rating={rating.rating} />}
              </div>
              <p className="mt-3 text-sm text-muted">{p.blurb || g.tagline}</p>
              {rating && rating.rankedGamesPlayed > 0 && (
                <p className="mt-2 text-xs text-faint">
                  Ranked {rating.rankedWins}–{rating.rankedLosses}
                  {rating.rankedDraws ? `–${rating.rankedDraws}` : ""} · peak {rating.peakRating}
                </p>
              )}
              <div className="mt-auto flex gap-2 pt-6">
                {live ? (
                  <>
                    {g.modes.includes("RANKED") && (
                      <LinkButton href={`/play/${g.slug}?mode=RANKED`} size="sm" className="flex-1">
                        Ranked
                      </LinkButton>
                    )}
                    {g.modes.includes("CASUAL") && (
                      <LinkButton href={`/play/${g.slug}?mode=CASUAL`} variant="secondary" size="sm" className="flex-1">
                        Casual
                      </LinkButton>
                    )}
                  </>
                ) : (
                  <span className="text-sm text-faint">Coming soon</span>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
